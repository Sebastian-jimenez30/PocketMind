package com.pocketmind.assistant.domain.finance

import com.pocketmind.assistant.auth.AuthenticatedUser
import com.pocketmind.assistant.domain.memory.AssistantMemoryRepository
import com.pocketmind.shared.domain.model.CurrencyCode
import com.pocketmind.shared.domain.model.FinancialAccount
import com.pocketmind.shared.domain.model.FinancialAccountType
import com.pocketmind.shared.domain.model.ProductReferenceResolution
import com.pocketmind.shared.domain.model.TransactionStatus
import com.pocketmind.shared.domain.model.TransactionType
import com.pocketmind.shared.domain.model.calculateCreditCardOverview
import com.pocketmind.shared.domain.model.calculateLoanOverview
import com.pocketmind.shared.domain.model.calculateSavingsProjection
import com.pocketmind.shared.domain.model.resolveProductReference
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class FinancialReadService(
    private val contextRepository: FinancialContextRepository,
    private val memoryRepository: AssistantMemoryRepository,
    private val session: AuthenticatedUser,
    private val clock: Clock = Clock.systemUTC(),
) {
    private var cachedContext: ResolvedContext? = null

    suspend fun listProducts(includeArchived: Boolean): ProductListResult {
        val context = loadContext()
        return ProductListResult(
            metadata = context.metadata,
            products = context.snapshot.accounts
                .asSequence()
                .filter { includeArchived || !it.isArchived }
                .map { context.summarize(it) }
                .sortedWith(compareBy(ProductSummary::isArchived, ProductSummary::name))
                .toList(),
        )
    }

    suspend fun getProduct(
        reference: String,
        allowedTypes: Set<FinancialAccountType> = emptySet(),
    ): ProductLookupResult {
        val context = loadContext()
        return when (val resolution = context.resolve(reference, allowedTypes)) {
            is ProductReferenceResolution.Resolved -> ProductLookupResult(
                status = STATUS_RESOLVED,
                metadata = context.metadata,
                product = context.summarize(resolution.product),
            )

            is ProductReferenceResolution.Ambiguous -> ProductLookupResult(
                status = STATUS_AMBIGUOUS,
                metadata = context.metadata,
                candidates = resolution.candidates.map(context::summarize),
            )

            ProductReferenceResolution.NotFound -> ProductLookupResult(
                status = STATUS_NOT_FOUND,
                metadata = context.metadata,
            )
        }
    }

    suspend fun getOverview(): FinancialOverviewResult {
        val context = loadContext()
        val snapshot = context.snapshot
        val now = context.metadata.observedAtEpochMillis
        val monthStart = Instant.ofEpochMilli(now)
            .atZone(ZoneOffset.UTC)
            .withDayOfMonth(1)
            .toLocalDate()
            .atStartOfDay(ZoneOffset.UTC)
            .toInstant()
            .toEpochMilli()
        val currencies = buildSet {
            snapshot.accounts.forEach { add(it.currency) }
            snapshot.transactions.forEach { add(it.amount.currency) }
            snapshot.incomeSources.forEach { add(it.expectedAmount.currency) }
            snapshot.savingsPlans.forEach { add(it.currentAmount.currency) }
            snapshot.debts.forEach { add(it.outstandingBalance.currency) }
            snapshot.recurringObligations.forEach { add(it.amount.currency) }
        }.sortedBy(CurrencyCode::name)

        return FinancialOverviewResult(
            metadata = context.metadata,
            monthStartEpochMillis = monthStart,
            currencies = currencies.map { currency ->
                val posted = snapshot.transactions.filter {
                    it.status == TransactionStatus.POSTED &&
                        it.amount.currency == currency
                }
                val activeAccounts = snapshot.accounts.filter {
                    !it.isArchived && it.currency == currency
                }
                CurrencyOverview(
                    currency = currency.name,
                    liquidBalanceMinorUnits = activeAccounts
                        .filter { it.type.isLiquid }
                        .sumOf { context.liquidBalance(it) },
                    monthlyIncomeMinorUnits = posted
                        .filter {
                            it.type == TransactionType.INCOME &&
                                it.occurredAtEpochMillis in monthStart..now
                        }
                        .sumOf { it.amount.minorUnits },
                    monthlyExpenseMinorUnits = posted
                        .filter {
                            it.type == TransactionType.EXPENSE &&
                                it.occurredAtEpochMillis in monthStart..now
                        }
                        .sumOf { it.amount.minorUnits },
                    productSavingsBalanceMinorUnits = activeAccounts
                        .filter { it.type == FinancialAccountType.SAVINGS }
                        .sumOf { context.savingsBalance(it) },
                    legacySavingsPlanBalanceMinorUnits = snapshot.savingsPlans
                        .filter { it.isActive && it.currentAmount.currency == currency }
                        .sumOf { it.currentAmount.minorUnits },
                    creditCardDebtMinorUnits = activeAccounts
                        .filter { it.type == FinancialAccountType.CREDIT_CARD }
                        .sumOf { context.cardDebt(it) },
                    loanDebtMinorUnits = activeAccounts
                        .filter { it.type == FinancialAccountType.LOAN }
                        .sumOf { context.loanDebt(it) },
                    legacyDebtMinorUnits = snapshot.debts
                        .filter {
                            it.isActive && it.outstandingBalance.currency == currency
                        }
                        .sumOf { it.outstandingBalance.minorUnits },
                )
            },
            activeProductCount = snapshot.accounts.count { !it.isArchived },
            postedTransactionCount = snapshot.transactions.count {
                it.status == TransactionStatus.POSTED
            },
            activeIncomeSourceCount = snapshot.incomeSources.count { it.isActive },
            activeRecurringObligationCount =
                snapshot.recurringObligations.count { it.isActive },
        )
    }

    suspend fun listTransactions(query: TransactionQuery): TransactionListResult {
        require(query.limit in 1..MAX_TRANSACTION_LIMIT)
        require(
            query.fromEpochMillis == null ||
                query.toEpochMillis == null ||
                query.fromEpochMillis <= query.toEpochMillis,
        )
        val type = query.type?.let { value ->
            TransactionType.entries.firstOrNull { it.name == value }
                ?: throw IllegalArgumentException("Unsupported transaction type.")
        }
        val status = query.status?.let { value ->
            TransactionStatus.entries.firstOrNull { it.name == value }
                ?: throw IllegalArgumentException("Unsupported transaction status.")
        }
        val context = loadContext()
        val resolvedProduct = query.productReference?.let { reference ->
            when (val resolution = context.resolve(reference)) {
                is ProductReferenceResolution.Resolved -> resolution.product
                is ProductReferenceResolution.Ambiguous -> {
                    return TransactionListResult(
                        status = STATUS_AMBIGUOUS,
                        metadata = context.metadata,
                        candidates = resolution.candidates.map(context::summarize),
                    )
                }

                ProductReferenceResolution.NotFound -> {
                    return TransactionListResult(
                        status = STATUS_NOT_FOUND,
                        metadata = context.metadata,
                    )
                }
            }
        }

        val matches = context.snapshot.transactions
            .asSequence()
            .filter { transaction ->
                resolvedProduct == null ||
                    transaction.accountId == resolvedProduct.id ||
                    transaction.relatedAccountId == resolvedProduct.id
            }
            .filter {
                query.fromEpochMillis == null ||
                    it.occurredAtEpochMillis >= query.fromEpochMillis
            }
            .filter {
                query.toEpochMillis == null ||
                    it.occurredAtEpochMillis <= query.toEpochMillis
            }
            .filter { type == null || it.type == type }
            .filter { status == null || it.status == status }
            .sortedWith(
                compareByDescending<com.pocketmind.shared.domain.model.FinancialTransaction> {
                    it.occurredAtEpochMillis
                }.thenByDescending { it.id },
            )
            .toList()
        val returned = matches.take(query.limit)

        return TransactionListResult(
            status = STATUS_OK,
            metadata = context.metadata,
            resolvedProduct = resolvedProduct?.let(context::summarize),
            transactions = returned.map { transaction ->
                TransactionSummary(
                    id = transaction.id,
                    productId = transaction.accountId,
                    relatedProductId = transaction.relatedAccountId,
                    type = transaction.type.name,
                    amount = MoneyValue(
                        minorUnits = transaction.amount.minorUnits,
                        currency = transaction.amount.currency.name,
                    ),
                    occurredAtEpochMillis = transaction.occurredAtEpochMillis,
                    categoryId = transaction.categoryId,
                    merchant = transaction.merchant,
                    note = transaction.note,
                    source = transaction.source.name,
                    status = transaction.status.name,
                    manualRevision = transaction.manualRevision,
                )
            },
            totalMatches = matches.size,
            returnedCount = returned.size,
            truncated = matches.size > returned.size,
        )
    }

    private suspend fun loadContext(): ResolvedContext {
        cachedContext?.let { return it }
        val snapshot = contextRepository.fetchSnapshot(session)
        val confirmedAliases = memoryRepository.listProductAliases(session)
            .groupBy { it.productId }
        val accounts = snapshot.accounts.map { account ->
            val aliases = (account.aliases + confirmedAliases[account.id].orEmpty()
                .map { it.alias })
                .map(String::trim)
                .filter(String::isNotEmpty)
                .distinctBy { it.normalizedReference() }
            account.copy(aliases = aliases)
        }
        val resolvedSnapshot = snapshot.copy(accounts = accounts)
        return ResolvedContext(
            snapshot = resolvedSnapshot,
            metadata = FinancialReadMetadata(
                stateVersion = snapshot.stateVersion,
                latestRemoteUpdateEpochMillis =
                    snapshot.latestRemoteUpdateEpochMillis,
                observedAtEpochMillis = clock.millis(),
                remoteRecordCount = snapshot.remoteRecordCount,
                unknownEntityTypes = snapshot.unknownEntityTypes.toList(),
            ),
        ).also { cachedContext = it }
    }

    private data class ResolvedContext(
        val snapshot: FinancialContextSnapshot,
        val metadata: FinancialReadMetadata,
    ) {
        fun resolve(
            reference: String,
            allowedTypes: Set<FinancialAccountType> = emptySet(),
        ): ProductReferenceResolution {
            val normalized = reference.trim()
            val candidates = snapshot.accounts.filter {
                allowedTypes.isEmpty() || it.type in allowedTypes
            }
            candidates.singleOrNull { it.id == normalized }?.let {
                return ProductReferenceResolution.Resolved(
                    product = it,
                    matchedByAlias = false,
                )
            }
            return resolveProductReference(reference, candidates)
        }

        fun summarize(account: FinancialAccount): ProductSummary {
            val base = ProductSummary(
                id = account.id,
                name = account.name,
                type = account.type.name,
                currency = account.currency.name,
                aliases = account.aliases,
                isArchived = account.isArchived,
                dataStatus = DATA_COMPLETE,
            )
            return when (account.type) {
                FinancialAccountType.CASH,
                FinancialAccountType.BANK_ACCOUNT,
                -> base.copy(
                    currentBalance = account.moneyValue(liquidBalance(account)),
                )

                FinancialAccountType.SAVINGS -> {
                    val profile = snapshot.savingsProfiles
                        .singleOrNull { it.accountId == account.id }
                        ?: return base.copy(
                            dataStatus = DATA_MISSING_PROFILE,
                            currentBalance = account.openingBalance.toValue(),
                        )
                    val projection = calculateSavingsProjection(
                        profile = profile,
                        openingBalance = account.openingBalance,
                        movements = snapshot.savingsMovements,
                        atEpochMillis = metadata.observedAtEpochMillis,
                    )
                    base.copy(
                        currentBalance = projection.currentBalance.toValue(),
                        annualRateBasisPoints =
                            projection.annualYieldBasisPoints,
                        maturityAtEpochMillis = profile.maturityAtEpochMillis,
                    )
                }

                FinancialAccountType.CREDIT_CARD -> {
                    val profile = snapshot.creditCardProfiles
                        .singleOrNull { it.accountId == account.id }
                        ?: return base.copy(
                            dataStatus = DATA_MISSING_PROFILE,
                            currentDebt = account.openingBalance.toValue(),
                        )
                    val overview = calculateCreditCardOverview(
                        profile = profile,
                        openingDebt = account.openingBalance,
                        purchases = snapshot.installmentPurchases,
                        payments = snapshot.creditCardPayments,
                    )
                    base.copy(
                        currentDebt = overview.currentDebt.toValue(),
                        availableCredit = overview.availableCredit.toValue(),
                        nextPayment = overview.nextPayment.toValue(),
                        annualRateBasisPoints =
                            profile.annualInterestBasisPoints,
                        statementClosingDay = profile.statementClosingDay,
                        paymentDueDay = profile.paymentDueDay,
                    )
                }

                FinancialAccountType.LOAN -> {
                    val profile = snapshot.loanProfiles
                        .singleOrNull { it.accountId == account.id }
                        ?: return base.copy(
                            dataStatus = DATA_MISSING_PROFILE,
                            currentDebt = account.openingBalance.toValue(),
                        )
                    val overview = calculateLoanOverview(
                        profile = profile,
                        openingDebt = account.openingBalance,
                        payments = snapshot.loanPayments,
                        atEpochMillis = metadata.observedAtEpochMillis,
                    )
                    base.copy(
                        currentDebt = overview.currentDebt.toValue(),
                        nextPayment = overview.nextPayment.toValue(),
                        annualRateBasisPoints =
                            profile.annualInterestBasisPoints,
                        paymentDueDay = profile.paymentDueDay,
                    )
                }
            }
        }

        fun liquidBalance(account: FinancialAccount): Long {
            var balance = account.openingBalance.minorUnits
            snapshot.transactions
                .asSequence()
                .filter {
                    it.status == TransactionStatus.POSTED &&
                        it.amount.currency == account.currency
                }
                .forEach { transaction ->
                    if (transaction.accountId == account.id) {
                        balance += when (transaction.type) {
                            TransactionType.INCOME -> transaction.amount.minorUnits
                            TransactionType.EXPENSE,
                            TransactionType.TRANSFER,
                            -> -transaction.amount.minorUnits
                        }
                    }
                    if (
                        transaction.type == TransactionType.TRANSFER &&
                        transaction.relatedAccountId == account.id
                    ) {
                        balance += transaction.amount.minorUnits
                    }
                }
            return balance
        }

        fun savingsBalance(account: FinancialAccount): Long =
            summarize(account).currentBalance?.minorUnits
                ?: account.openingBalance.minorUnits

        fun cardDebt(account: FinancialAccount): Long =
            summarize(account).currentDebt?.minorUnits
                ?: account.openingBalance.minorUnits

        fun loanDebt(account: FinancialAccount): Long =
            summarize(account).currentDebt?.minorUnits
                ?: account.openingBalance.minorUnits

        private fun FinancialAccount.moneyValue(minorUnits: Long): MoneyValue =
            MoneyValue(minorUnits, currency.name)
    }

    companion object {
        const val MAX_TRANSACTION_LIMIT = 200
        private const val STATUS_OK = "ok"
        private const val STATUS_RESOLVED = "resolved"
        private const val STATUS_AMBIGUOUS = "ambiguous"
        private const val STATUS_NOT_FOUND = "not_found"
        private const val DATA_COMPLETE = "complete"
        private const val DATA_MISSING_PROFILE = "missing_profile"
    }
}

class FinancialReadServiceFactory(
    private val contextRepository: FinancialContextRepository,
    private val memoryRepository: AssistantMemoryRepository,
    private val clock: Clock = Clock.systemUTC(),
) {
    fun create(session: AuthenticatedUser): FinancialReadService =
        FinancialReadService(
            contextRepository = contextRepository,
            memoryRepository = memoryRepository,
            session = session,
            clock = clock,
        )
}

data class TransactionQuery(
    val productReference: String? = null,
    val fromEpochMillis: Long? = null,
    val toEpochMillis: Long? = null,
    val type: String? = null,
    val status: String? = TransactionStatus.POSTED.name,
    val limit: Int = 50,
)

private val FinancialAccountType.isLiquid: Boolean
    get() = this == FinancialAccountType.CASH ||
        this == FinancialAccountType.BANK_ACCOUNT

private fun com.pocketmind.shared.domain.model.Money.toValue(): MoneyValue =
    MoneyValue(minorUnits, currency.name)

private fun String.normalizedReference(): String =
    trim().lowercase().replace(Regex("\\s+"), " ")
