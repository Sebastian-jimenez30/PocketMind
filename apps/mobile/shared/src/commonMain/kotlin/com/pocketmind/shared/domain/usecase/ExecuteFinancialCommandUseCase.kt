package com.pocketmind.shared.domain.usecase

import com.pocketmind.shared.domain.command.FinancialCommand
import com.pocketmind.shared.domain.command.FinancialCommandError
import com.pocketmind.shared.domain.command.FinancialCommandResult
import com.pocketmind.shared.domain.model.CreditCardPayment
import com.pocketmind.shared.domain.model.FinancialAccount
import com.pocketmind.shared.domain.model.FinancialAccountType
import com.pocketmind.shared.domain.model.FinancialProductConfiguration
import com.pocketmind.shared.domain.model.FinancialTransaction
import com.pocketmind.shared.domain.model.InstallmentPurchase
import com.pocketmind.shared.domain.model.LoanPayment
import com.pocketmind.shared.domain.model.Money
import com.pocketmind.shared.domain.model.SavingsMovement
import com.pocketmind.shared.domain.model.SavingsMovementType
import com.pocketmind.shared.domain.model.TransactionCategoryId
import com.pocketmind.shared.domain.model.TransactionSource
import com.pocketmind.shared.domain.model.TransactionStatus
import com.pocketmind.shared.domain.model.TransactionType
import com.pocketmind.shared.domain.model.calculateCreditCardOverview
import com.pocketmind.shared.domain.model.calculateLoanOverview
import com.pocketmind.shared.domain.model.calculateSavingsProjection
import com.pocketmind.shared.domain.repository.FinancialAccountRepository
import com.pocketmind.shared.domain.repository.ManualFinanceRepository
import com.pocketmind.shared.domain.repository.TransactionRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Platform calendar policy used to derive a card purchase's first due date. */
fun interface CreditCardPaymentDateCalculator {
    fun firstPaymentAt(
        purchasedAtEpochMillis: Long,
        statementClosingDay: Int,
        paymentDueDay: Int,
    ): Long
}

/**
 * Single write boundary for financial operations, regardless of input channel.
 *
 * Expected business failures are returned as [FinancialCommandResult.Rejected].
 * Infrastructure failures are allowed to propagate so presentation and workers
 * can apply their retry and observability policies.
 */
class ExecuteFinancialCommandUseCase(
    private val accountRepository: FinancialAccountRepository,
    private val transactionRepository: TransactionRepository,
    private val manualFinanceRepository: ManualFinanceRepository,
    private val cardPaymentDateCalculator: CreditCardPaymentDateCalculator,
) {
    private val executionMutex = Mutex()

    suspend operator fun invoke(command: FinancialCommand): FinancialCommandResult =
        executionMutex.withLock {
            if (command.commandId.isBlank()) {
                return@withLock command.rejected(FinancialCommandError.MISSING_COMMAND_ID)
            }
            when (command) {
                is FinancialCommand.RecordIncome -> executeStandardTransaction(
                    command = command,
                    productId = command.productId,
                    type = TransactionType.INCOME,
                    amount = command.amount,
                    occurredAtEpochMillis = command.occurredAtEpochMillis,
                    categoryId = command.categoryId,
                    merchant = command.merchant,
                    note = command.note,
                    relatedProductId = null,
                    source = command.source,
                )

                is FinancialCommand.RecordExpense -> executeStandardTransaction(
                    command = command,
                    productId = command.productId,
                    type = TransactionType.EXPENSE,
                    amount = command.amount,
                    occurredAtEpochMillis = command.occurredAtEpochMillis,
                    categoryId = command.categoryId,
                    merchant = command.merchant,
                    note = command.note,
                    relatedProductId = null,
                    source = command.source,
                )

                is FinancialCommand.Transfer -> executeTransfer(command)
                is FinancialCommand.CreateProduct -> executeCreateProduct(command)
                is FinancialCommand.UpdateProduct -> executeUpdateProduct(command)
                is FinancialCommand.ArchiveProduct -> executeArchiveProduct(command)
                is FinancialCommand.RecordCardPurchase -> executeCardPurchase(command)
                is FinancialCommand.RecordCardPayment -> executeCardPayment(command)
                is FinancialCommand.RecordSavingsMovement -> executeSavingsMovement(command)
                is FinancialCommand.RecordLoanPayment -> executeLoanPayment(command)
                is FinancialCommand.UpdateTransaction -> executeUpdateTransaction(command)
                is FinancialCommand.DeleteTransaction -> executeDeleteTransaction(command)
            }
        }

    private suspend fun executeStandardTransaction(
        command: FinancialCommand,
        productId: String,
        type: TransactionType,
        amount: Money,
        occurredAtEpochMillis: Long,
        categoryId: String?,
        merchant: String?,
        note: String?,
        relatedProductId: String?,
        source: TransactionSource,
        transactionId: String = command.commandId,
    ): FinancialCommandResult {
        val product = accountRepository.getById(productId)
            ?: return command.rejected(FinancialCommandError.PRODUCT_NOT_FOUND)
        if (!product.type.isLiquidProduct()) {
            return command.rejected(FinancialCommandError.UNSUPPORTED_PRODUCT_OPERATION)
        }
        validateTransactionValues(command, amount, occurredAtEpochMillis)?.let { return it }
        if (amount.currency != product.currency) {
            return command.rejected(FinancialCommandError.CURRENCY_MISMATCH)
        }
        transactionRepository.save(
            FinancialTransaction(
                id = transactionId.trim(),
                accountId = product.id,
                type = type,
                amount = amount,
                occurredAtEpochMillis = occurredAtEpochMillis,
                categoryId = categoryId.normalized(),
                merchant = merchant.normalized(),
                note = note.normalized(),
                source = source,
                status = TransactionStatus.POSTED,
                relatedAccountId = relatedProductId.normalized(),
            ),
        )
        return command.succeeded(transactionId)
    }

    private suspend fun executeTransfer(command: FinancialCommand.Transfer): FinancialCommandResult {
        val source = accountRepository.getById(command.sourceProductId)
            ?: return command.rejected(FinancialCommandError.PRODUCT_NOT_FOUND)
        val destinationId = command.destinationProductId.trim()
        if (destinationId.isBlank()) {
            return command.rejected(FinancialCommandError.MISSING_TRANSFER_DESTINATION)
        }
        if (source.id == destinationId) {
            return command.rejected(FinancialCommandError.SAME_TRANSFER_PRODUCT)
        }
        val destination = accountRepository.getById(destinationId)
            ?: return command.rejected(FinancialCommandError.PRODUCT_NOT_FOUND)
        if (!source.type.isLiquidProduct() || !destination.type.isLiquidProduct()) {
            return command.rejected(FinancialCommandError.UNSUPPORTED_PRODUCT_OPERATION)
        }
        if (command.amount.currency != source.currency || command.amount.currency != destination.currency) {
            return command.rejected(FinancialCommandError.CURRENCY_MISMATCH)
        }
        return executeStandardTransaction(
            command = command,
            productId = source.id,
            type = TransactionType.TRANSFER,
            amount = command.amount,
            occurredAtEpochMillis = command.occurredAtEpochMillis,
            categoryId = command.categoryId,
            merchant = command.merchant,
            note = command.note,
            relatedProductId = destination.id,
            source = command.source,
        )
    }

    private suspend fun executeCreateProduct(
        command: FinancialCommand.CreateProduct,
    ): FinancialCommandResult {
        if (accountRepository.getById(command.account.id) != null) {
            return command.rejected(FinancialCommandError.PRODUCT_ALREADY_EXISTS)
        }
        return saveProduct(command, command.account, command.configuration)
    }

    private suspend fun executeUpdateProduct(
        command: FinancialCommand.UpdateProduct,
    ): FinancialCommandResult {
        if (accountRepository.getById(command.account.id) == null) {
            return command.rejected(FinancialCommandError.PRODUCT_NOT_FOUND)
        }
        return saveProduct(command, command.account, command.configuration)
    }

    private suspend fun saveProduct(
        command: FinancialCommand,
        account: FinancialAccount,
        configuration: FinancialProductConfiguration,
    ): FinancialCommandResult {
        if (!configuration.matches(account)) {
            return command.rejected(FinancialCommandError.INVALID_PRODUCT_CONFIGURATION)
        }
        manualFinanceRepository.saveProduct(account, configuration)
        return command.succeeded(account.id)
    }

    private suspend fun executeArchiveProduct(
        command: FinancialCommand.ArchiveProduct,
    ): FinancialCommandResult {
        val account = accountRepository.getById(command.productId)
            ?: return command.rejected(FinancialCommandError.PRODUCT_NOT_FOUND)
        accountRepository.save(account.copy(isArchived = true))
        return command.succeeded(account.id)
    }

    private suspend fun executeCardPurchase(
        command: FinancialCommand.RecordCardPurchase,
    ): FinancialCommandResult {
        val account = accountRepository.getById(command.cardId)
            ?.takeIf { it.type == FinancialAccountType.CREDIT_CARD }
            ?: return command.rejected(FinancialCommandError.PRODUCT_NOT_FOUND)
        val profile = manualFinanceRepository.getCreditCardProfile(account.id)
            ?: return command.rejected(FinancialCommandError.MISSING_CARD_PROFILE)
        validateTransactionValues(command, command.principal, command.purchasedAtEpochMillis)?.let { return it }
        if (command.principal.currency != account.currency) {
            return command.rejected(FinancialCommandError.CURRENCY_MISMATCH)
        }
        if (command.installmentCount !in 1..60) {
            return command.rejected(FinancialCommandError.INVALID_INSTALLMENTS)
        }
        val merchant = command.merchant.normalized()
            ?: return command.rejected(FinancialCommandError.INVALID_MERCHANT)
        val purchases = manualFinanceRepository.observeInstallmentPurchases().first()
        val payments = manualFinanceRepository.observeCreditCardPayments().first()
        val overview = calculateCreditCardOverview(profile, account.openingBalance, purchases, payments)
        val purchase = InstallmentPurchase(
            id = command.commandId,
            accountId = account.id,
            merchant = merchant,
            principal = command.principal,
            installmentCount = command.installmentCount,
            annualInterestBasisPoints = profile.annualInterestBasisPoints,
            purchasedAtEpochMillis = command.purchasedAtEpochMillis,
            firstPaymentAtEpochMillis = cardPaymentDateCalculator.firstPaymentAt(
                purchasedAtEpochMillis = command.purchasedAtEpochMillis,
                statementClosingDay = profile.statementClosingDay,
                paymentDueDay = profile.paymentDueDay,
            ),
            categoryId = command.categoryId.normalized(),
            note = command.note.normalized(),
        )
        if (purchase.financedTotal.minorUnits > overview.availableCredit.minorUnits) {
            return command.rejected(FinancialCommandError.PURCHASE_EXCEEDS_AVAILABLE_CREDIT)
        }
        val ledgerId = "purchase-${command.commandId}"
        manualFinanceRepository.saveInstallmentPurchase(
            purchase = purchase,
            ledgerTransaction = FinancialTransaction(
                id = ledgerId,
                accountId = account.id,
                type = TransactionType.EXPENSE,
                amount = command.principal,
                occurredAtEpochMillis = command.purchasedAtEpochMillis,
                categoryId = command.categoryId.normalized(),
                merchant = merchant,
                note = "${command.installmentCount} cuotas. ${command.note.orEmpty()}".trim(),
                source = command.source,
            ),
        )
        return command.succeeded(command.commandId, ledgerId)
    }

    private suspend fun executeCardPayment(
        command: FinancialCommand.RecordCardPayment,
    ): FinancialCommandResult {
        val account = accountRepository.getById(command.cardId)
            ?.takeIf { it.type == FinancialAccountType.CREDIT_CARD }
            ?: return command.rejected(FinancialCommandError.PRODUCT_NOT_FOUND)
        val profile = manualFinanceRepository.getCreditCardProfile(account.id)
            ?: return command.rejected(FinancialCommandError.MISSING_CARD_PROFILE)
        validateTransactionValues(command, command.amount, command.paidAtEpochMillis)?.let { return it }
        validateRelatedCurrency(command, command.sourceProductId, command.amount, account)?.let { return it }
        val overview = calculateCreditCardOverview(
            profile = profile,
            openingDebt = account.openingBalance,
            purchases = manualFinanceRepository.observeInstallmentPurchases().first(),
            payments = manualFinanceRepository.observeCreditCardPayments().first(),
        )
        if (command.amount.minorUnits > overview.currentDebt.minorUnits) {
            return command.rejected(FinancialCommandError.PAYMENT_EXCEEDS_CARD_DEBT)
        }
        val sourceSavings = when (
            val result = relatedSavingsMovement(
                command = command,
                relatedProductId = command.sourceProductId,
                movementType = SavingsMovementType.WITHDRAWAL,
                amount = command.amount,
                occurredAtEpochMillis = command.paidAtEpochMillis,
                movementId = "source-savings-${command.commandId}",
                note = "Pago de tarjeta. ${command.note.orEmpty()}",
            )
        ) {
            RelatedSavingsMovement.None -> null
            is RelatedSavingsMovement.Ready -> result.movement
            is RelatedSavingsMovement.Rejected -> return result.result
        }
        val payment = CreditCardPayment(
            id = command.commandId,
            accountId = account.id,
            amount = command.amount,
            paidAtEpochMillis = command.paidAtEpochMillis,
            sourceAccountId = command.sourceProductId.normalized(),
            note = command.note.normalized(),
        )
        val ledgerId = "card-payment-${command.commandId}"
        manualFinanceRepository.saveCreditCardPayment(
            payment = payment,
            ledgerTransaction = debtPaymentLedger(
                id = ledgerId,
                debtProduct = account,
                amount = command.amount,
                occurredAtEpochMillis = command.paidAtEpochMillis,
                sourceProductId = command.sourceProductId,
                merchant = "Pago de tarjeta",
                note = command.note,
                source = command.source,
            ),
            sourceSavingsMovement = sourceSavings,
        )
        return command.succeeded(
            *listOfNotNull(command.commandId, ledgerId, sourceSavings?.id).toTypedArray(),
        )
    }

    private suspend fun executeSavingsMovement(
        command: FinancialCommand.RecordSavingsMovement,
    ): FinancialCommandResult {
        val account = accountRepository.getById(command.savingsId)
            ?.takeIf { it.type == FinancialAccountType.SAVINGS }
            ?: return command.rejected(FinancialCommandError.PRODUCT_NOT_FOUND)
        val profile = manualFinanceRepository.getSavingsProfile(account.id)
            ?: return command.rejected(FinancialCommandError.MISSING_SAVINGS_PROFILE)
        if (command.occurredAtEpochMillis <= 0) {
            return command.rejected(FinancialCommandError.INVALID_DATE)
        }
        if (command.amount.currency != account.currency) {
            return command.rejected(FinancialCommandError.CURRENCY_MISMATCH)
        }
        validateRelatedCurrency(command, command.relatedProductId, command.amount, account)?.let { return it }
        when (command.movementType) {
            SavingsMovementType.DEPOSIT, SavingsMovementType.WITHDRAWAL -> {
                if (!command.amount.isPositive) {
                    return command.rejected(FinancialCommandError.INVALID_AMOUNT)
                }
            }
            SavingsMovementType.RATE_CHANGE -> {
                if (command.annualYieldBasisPoints == null || command.annualYieldBasisPoints < 0) {
                    return command.rejected(FinancialCommandError.INVALID_SAVINGS_RATE)
                }
            }
        }
        if (command.movementType == SavingsMovementType.WITHDRAWAL) {
            val projection = calculateSavingsProjection(
                profile = profile,
                openingBalance = account.openingBalance,
                movements = manualFinanceRepository.observeSavingsMovements().first(),
                atEpochMillis = command.occurredAtEpochMillis,
            )
            if (command.amount.minorUnits > projection.currentBalance.minorUnits) {
                return command.rejected(FinancialCommandError.WITHDRAWAL_EXCEEDS_SAVINGS)
            }
        }
        val movement = SavingsMovement(
            id = command.commandId,
            accountId = account.id,
            type = command.movementType,
            amount = command.amount,
            annualYieldBasisPoints = command.annualYieldBasisPoints,
            occurredAtEpochMillis = command.occurredAtEpochMillis,
            note = command.note.normalized(),
        )
        val relatedSavings = if (command.movementType == SavingsMovementType.RATE_CHANGE) {
            null
        } else {
            when (
                val result = relatedSavingsMovement(
                    command = command,
                    relatedProductId = command.relatedProductId,
                    movementType = if (command.movementType == SavingsMovementType.DEPOSIT) {
                        SavingsMovementType.WITHDRAWAL
                    } else {
                        SavingsMovementType.DEPOSIT
                    },
                    amount = command.amount,
                    occurredAtEpochMillis = command.occurredAtEpochMillis,
                    movementId = "related-savings-${command.commandId}",
                    note = command.note,
                )
            ) {
                RelatedSavingsMovement.None -> null
                is RelatedSavingsMovement.Ready -> result.movement
                is RelatedSavingsMovement.Rejected -> return result.result
            }
        }
        val ledger = savingsLedger(command, account)
        manualFinanceRepository.saveSavingsMovement(
            movement = movement,
            ledgerTransaction = ledger,
            relatedSavingsMovement = relatedSavings,
        )
        return command.succeeded(
            *listOfNotNull(command.commandId, ledger?.id, relatedSavings?.id).toTypedArray(),
        )
    }

    private suspend fun executeLoanPayment(
        command: FinancialCommand.RecordLoanPayment,
    ): FinancialCommandResult {
        val account = accountRepository.getById(command.loanId)
            ?.takeIf { it.type == FinancialAccountType.LOAN }
            ?: return command.rejected(FinancialCommandError.PRODUCT_NOT_FOUND)
        val profile = manualFinanceRepository.getLoanProfile(account.id)
            ?: return command.rejected(FinancialCommandError.MISSING_LOAN_PROFILE)
        validateTransactionValues(command, command.amount, command.paidAtEpochMillis)?.let { return it }
        validateRelatedCurrency(command, command.sourceProductId, command.amount, account)?.let { return it }
        val overview = calculateLoanOverview(
            profile = profile,
            openingDebt = account.openingBalance,
            payments = manualFinanceRepository.observeLoanPayments().first(),
            atEpochMillis = command.paidAtEpochMillis,
        )
        if (command.amount.minorUnits > overview.currentDebt.minorUnits) {
            return command.rejected(FinancialCommandError.PAYMENT_EXCEEDS_LOAN_DEBT)
        }
        val sourceSavings = when (
            val result = relatedSavingsMovement(
                command = command,
                relatedProductId = command.sourceProductId,
                movementType = SavingsMovementType.WITHDRAWAL,
                amount = command.amount,
                occurredAtEpochMillis = command.paidAtEpochMillis,
                movementId = "source-savings-${command.commandId}",
                note = "Pago de préstamo. ${command.note.orEmpty()}",
            )
        ) {
            RelatedSavingsMovement.None -> null
            is RelatedSavingsMovement.Ready -> result.movement
            is RelatedSavingsMovement.Rejected -> return result.result
        }
        val payment = LoanPayment(
            id = command.commandId,
            accountId = account.id,
            amount = command.amount,
            paidAtEpochMillis = command.paidAtEpochMillis,
            sourceAccountId = command.sourceProductId.normalized(),
            note = command.note.normalized(),
        )
        val ledgerId = "loan-payment-${command.commandId}"
        manualFinanceRepository.saveLoanPayment(
            payment = payment,
            ledgerTransaction = debtPaymentLedger(
                id = ledgerId,
                debtProduct = account,
                amount = command.amount,
                occurredAtEpochMillis = command.paidAtEpochMillis,
                sourceProductId = command.sourceProductId,
                merchant = "Pago de préstamo",
                note = command.note,
                source = command.source,
            ),
            sourceSavingsMovement = sourceSavings,
        )
        return command.succeeded(
            *listOfNotNull(command.commandId, ledgerId, sourceSavings?.id).toTypedArray(),
        )
    }

    private suspend fun executeUpdateTransaction(
        command: FinancialCommand.UpdateTransaction,
    ): FinancialCommandResult {
        val existing = transactionRepository.getById(command.transactionId)
            ?: return command.rejected(FinancialCommandError.TRANSACTION_NOT_FOUND)
        if (existing.id.isLinkedProductTransaction()) {
            return command.rejected(FinancialCommandError.LINKED_TRANSACTION_REQUIRES_PRODUCT_ACTION)
        }
        if (command.type == TransactionType.TRANSFER && command.relatedProductId.isNullOrBlank()) {
            return command.rejected(FinancialCommandError.MISSING_TRANSFER_DESTINATION)
        }
        if (command.type == TransactionType.TRANSFER) {
            val destination = command.relatedProductId?.let { accountRepository.getById(it) }
                ?: return command.rejected(FinancialCommandError.PRODUCT_NOT_FOUND)
            if (destination.id == command.productId) {
                return command.rejected(FinancialCommandError.SAME_TRANSFER_PRODUCT)
            }
            if (destination.currency != command.amount.currency) {
                return command.rejected(FinancialCommandError.CURRENCY_MISMATCH)
            }
        }
        return executeStandardTransaction(
            command = command,
            productId = command.productId,
            type = command.type,
            amount = command.amount,
            occurredAtEpochMillis = command.occurredAtEpochMillis,
            categoryId = command.categoryId,
            merchant = command.merchant,
            note = command.note,
            relatedProductId = command.relatedProductId,
            source = command.source,
            transactionId = existing.id,
        )
    }

    private suspend fun executeDeleteTransaction(
        command: FinancialCommand.DeleteTransaction,
    ): FinancialCommandResult {
        val transaction = transactionRepository.getById(command.transactionId)
            ?: return command.rejected(FinancialCommandError.TRANSACTION_NOT_FOUND)
        if (transaction.id.isLinkedProductTransaction()) {
            return command.rejected(FinancialCommandError.LINKED_TRANSACTION_REQUIRES_PRODUCT_ACTION)
        }
        transactionRepository.delete(transaction.id)
        return command.succeeded(transaction.id)
    }

    private suspend fun validateRelatedCurrency(
        command: FinancialCommand,
        relatedProductId: String?,
        amount: Money,
        primaryProduct: FinancialAccount,
    ): FinancialCommandResult.Rejected? {
        if (amount.currency != primaryProduct.currency) {
            return command.rejected(FinancialCommandError.CURRENCY_MISMATCH)
        }
        val relatedId = relatedProductId.normalized() ?: return null
        if (relatedId == primaryProduct.id) {
            return command.rejected(FinancialCommandError.SAME_TRANSFER_PRODUCT)
        }
        val related = accountRepository.getById(relatedId)
            ?: return command.rejected(FinancialCommandError.PRODUCT_NOT_FOUND)
        return if (!related.type.isFundingProduct()) {
            command.rejected(FinancialCommandError.UNSUPPORTED_PRODUCT_OPERATION)
        } else if (related.currency != amount.currency) {
            command.rejected(FinancialCommandError.CURRENCY_MISMATCH)
        } else {
            null
        }
    }

    private suspend fun relatedSavingsMovement(
        command: FinancialCommand,
        relatedProductId: String?,
        movementType: SavingsMovementType,
        amount: Money,
        occurredAtEpochMillis: Long,
        movementId: String,
        note: String?,
    ): RelatedSavingsMovement {
        val relatedId = relatedProductId.normalized() ?: return RelatedSavingsMovement.None
        val account = accountRepository.getById(relatedId)
            ?: return RelatedSavingsMovement.Rejected(
                command.rejected(FinancialCommandError.PRODUCT_NOT_FOUND),
            )
        if (account.type != FinancialAccountType.SAVINGS) return RelatedSavingsMovement.None
        val profile = manualFinanceRepository.getSavingsProfile(account.id)
            ?: return RelatedSavingsMovement.Rejected(
                command.rejected(FinancialCommandError.MISSING_SAVINGS_PROFILE),
            )
        if (movementType == SavingsMovementType.WITHDRAWAL) {
            val projection = calculateSavingsProjection(
                profile = profile,
                openingBalance = account.openingBalance,
                movements = manualFinanceRepository.observeSavingsMovements().first(),
                atEpochMillis = occurredAtEpochMillis,
            )
            if (amount.minorUnits > projection.currentBalance.minorUnits) {
                return RelatedSavingsMovement.Rejected(
                    command.rejected(FinancialCommandError.WITHDRAWAL_EXCEEDS_SAVINGS),
                )
            }
        }
        return RelatedSavingsMovement.Ready(
            SavingsMovement(
                id = movementId,
                accountId = account.id,
                type = movementType,
                amount = amount,
                annualYieldBasisPoints = null,
                occurredAtEpochMillis = occurredAtEpochMillis,
                note = note.normalized(),
            ),
        )
    }

    private fun savingsLedger(
        command: FinancialCommand.RecordSavingsMovement,
        savings: FinancialAccount,
    ): FinancialTransaction? {
        if (command.movementType == SavingsMovementType.RATE_CHANGE) return null
        val related = command.relatedProductId.normalized()
        val isDeposit = command.movementType == SavingsMovementType.DEPOSIT
        return FinancialTransaction(
            id = "savings-${command.commandId}",
            accountId = when {
                related == null -> savings.id
                isDeposit -> related
                else -> savings.id
            },
            type = if (related == null) {
                if (isDeposit) TransactionType.INCOME else TransactionType.EXPENSE
            } else {
                TransactionType.TRANSFER
            },
            amount = command.amount,
            occurredAtEpochMillis = command.occurredAtEpochMillis,
            categoryId = TransactionCategoryId.SAVINGS.name,
            merchant = if (isDeposit) "Aporte al ahorro" else "Retiro del ahorro",
            note = command.note.normalized(),
            source = command.source,
            relatedAccountId = when {
                related == null -> null
                isDeposit -> savings.id
                else -> related
            },
        )
    }

    private fun debtPaymentLedger(
        id: String,
        debtProduct: FinancialAccount,
        amount: Money,
        occurredAtEpochMillis: Long,
        sourceProductId: String?,
        merchant: String,
        note: String?,
        source: TransactionSource,
    ): FinancialTransaction {
        val sourceId = sourceProductId.normalized()
        return FinancialTransaction(
            id = id,
            accountId = sourceId ?: debtProduct.id,
            type = if (sourceId == null) TransactionType.INCOME else TransactionType.TRANSFER,
            amount = amount,
            occurredAtEpochMillis = occurredAtEpochMillis,
            categoryId = TransactionCategoryId.DEBT_PAYMENT.name,
            merchant = merchant,
            note = note.normalized(),
            source = source,
            relatedAccountId = sourceId?.let { debtProduct.id },
        )
    }

    private fun validateTransactionValues(
        command: FinancialCommand,
        amount: Money,
        occurredAtEpochMillis: Long,
    ): FinancialCommandResult.Rejected? = when {
        !amount.isPositive -> command.rejected(FinancialCommandError.INVALID_AMOUNT)
        occurredAtEpochMillis <= 0 -> command.rejected(FinancialCommandError.INVALID_DATE)
        else -> null
    }
}

private fun FinancialProductConfiguration.matches(account: FinancialAccount): Boolean = when (this) {
    FinancialProductConfiguration.Standard ->
        account.type == FinancialAccountType.CASH || account.type == FinancialAccountType.BANK_ACCOUNT
    is FinancialProductConfiguration.CreditCard ->
        account.type == FinancialAccountType.CREDIT_CARD && profile.accountId == account.id
    is FinancialProductConfiguration.Savings ->
        account.type == FinancialAccountType.SAVINGS && profile.accountId == account.id
    is FinancialProductConfiguration.Loan ->
        account.type == FinancialAccountType.LOAN && profile.accountId == account.id
}

private fun FinancialAccountType.isLiquidProduct(): Boolean =
    this == FinancialAccountType.CASH || this == FinancialAccountType.BANK_ACCOUNT

private fun FinancialAccountType.isFundingProduct(): Boolean =
    isLiquidProduct() || this == FinancialAccountType.SAVINGS

private fun String?.normalized(): String? = this?.trim()?.takeIf(String::isNotEmpty)

private fun String.isLinkedProductTransaction(): Boolean =
    startsWith("purchase-") || startsWith("card-payment-") ||
        startsWith("savings-") || startsWith("loan-payment-")

private fun FinancialCommand.rejected(
    vararg errors: FinancialCommandError,
): FinancialCommandResult.Rejected = FinancialCommandResult.Rejected(
    commandId = commandId,
    errors = errors.toSet(),
)

private fun FinancialCommand.succeeded(
    vararg entityIds: String,
): FinancialCommandResult.Success = FinancialCommandResult.Success(
    commandId = commandId,
    affectedEntityIds = entityIds.map { it.trim() }.filter { it.isNotEmpty() }.toSet(),
)

private sealed interface RelatedSavingsMovement {
    data object None : RelatedSavingsMovement
    data class Ready(val movement: SavingsMovement) : RelatedSavingsMovement
    data class Rejected(val result: FinancialCommandResult.Rejected) : RelatedSavingsMovement
}
