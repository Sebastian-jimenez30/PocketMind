package com.pocketmind.shared.domain.usecase

import com.pocketmind.shared.domain.command.FinancialCommand
import com.pocketmind.shared.domain.command.FinancialCommandError
import com.pocketmind.shared.domain.command.FinancialCommandResult
import com.pocketmind.shared.domain.model.CreditCardPayment
import com.pocketmind.shared.domain.model.CreditCardProfile
import com.pocketmind.shared.domain.model.CurrencyCode
import com.pocketmind.shared.domain.model.DebtPaymentType
import com.pocketmind.shared.domain.model.FinancialAccount
import com.pocketmind.shared.domain.model.FinancialAccountType
import com.pocketmind.shared.domain.model.FinancialProductConfiguration
import com.pocketmind.shared.domain.model.FinancialTransaction
import com.pocketmind.shared.domain.model.InstallmentPurchase
import com.pocketmind.shared.domain.model.InstallmentRatePeriod
import com.pocketmind.shared.domain.model.LoanPayment
import com.pocketmind.shared.domain.model.LoanProfile
import com.pocketmind.shared.domain.model.Money
import com.pocketmind.shared.domain.model.SavingsMovement
import com.pocketmind.shared.domain.model.SavingsMovementType
import com.pocketmind.shared.domain.model.SavingsProductType
import com.pocketmind.shared.domain.model.SavingsProfile
import com.pocketmind.shared.domain.model.TransactionSource
import com.pocketmind.shared.domain.model.TransactionType
import com.pocketmind.shared.domain.repository.FinancialAccountRepository
import com.pocketmind.shared.domain.repository.ManualFinanceRepository
import com.pocketmind.shared.domain.repository.TransactionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ExecuteFinancialCommandUseCaseTest {
    private val accountRepository = FakeAccountRepository()
    private val transactionRepository = FakeTransactionRepository()
    private val manualFinanceRepository = FakeManualFinanceRepository()
    private val useCase = ExecuteFinancialCommandUseCase(
        accountRepository = accountRepository,
        transactionRepository = transactionRepository,
        manualFinanceRepository = manualFinanceRepository,
        cardPaymentDateCalculator = CreditCardPaymentDateCalculator { _, _, _ -> FIRST_PAYMENT_AT },
    )

    @Test
    fun `record income validates and persists a normalized transaction`() = runTest {
        accountRepository.accounts[BANK.id] = BANK

        val result = useCase(
            FinancialCommand.RecordIncome(
                commandId = "income-1",
                productId = BANK.id,
                amount = money(125_000),
                occurredAtEpochMillis = NOW,
                source = TransactionSource.MANUAL,
                categoryId = " SALARY ",
                merchant = " Employer ",
            ),
        )

        assertIs<FinancialCommandResult.Success>(result)
        assertEquals(
            FinancialTransaction(
                id = "income-1",
                accountId = BANK.id,
                type = TransactionType.INCOME,
                amount = money(125_000),
                occurredAtEpochMillis = NOW,
                categoryId = "SALARY",
                merchant = "Employer",
                source = TransactionSource.MANUAL,
            ),
            transactionRepository.transactions["income-1"],
        )
    }

    @Test
    fun `create product persists account and matching configuration together`() = runTest {
        val savings = FinancialAccount(
            id = "savings-new",
            name = "Cajita",
            type = FinancialAccountType.SAVINGS,
            currency = CurrencyCode.COP,
            openingBalance = money(80_000),
        )
        val configuration = FinancialProductConfiguration.Savings(
            SavingsProfile(
                accountId = savings.id,
                type = SavingsProductType.POCKET,
                annualYieldBasisPoints = 1_100,
                openedAtEpochMillis = NOW,
                maturityAtEpochMillis = null,
            ),
        )

        val result = useCase(
            FinancialCommand.CreateProduct(
                commandId = "create-product-1",
                account = savings,
                configuration = configuration,
            ),
        )

        assertIs<FinancialCommandResult.Success>(result)
        assertEquals(savings to configuration, manualFinanceRepository.savedProduct)
    }

    @Test
    fun `card purchase uses configured rate and shared due date policy`() = runTest {
        accountRepository.accounts[CARD.id] = CARD
        manualFinanceRepository.cardProfiles[CARD.id] = CARD_PROFILE

        val result = useCase(
            FinancialCommand.RecordCardPurchase(
                commandId = "purchase-1",
                cardId = CARD.id,
                merchant = "Tienda",
                principal = money(100_000),
                installmentCount = 2,
                purchasedAtEpochMillis = NOW,
                source = TransactionSource.MANUAL,
                categoryId = "SHOPPING",
            ),
        )

        assertIs<FinancialCommandResult.Success>(result)
        val purchase = manualFinanceRepository.savedPurchase?.first
        assertEquals(CARD_PROFILE.annualInterestBasisPoints, purchase?.annualInterestBasisPoints)
        assertEquals(FIRST_PAYMENT_AT, purchase?.firstPaymentAtEpochMillis)
        assertEquals("purchase-purchase-1", manualFinanceRepository.savedPurchase?.second?.id)
    }

    @Test
    fun `card purchase persists promotional rate periods for deterministic calculation`() = runTest {
        accountRepository.accounts[CARD.id] = CARD
        manualFinanceRepository.cardProfiles[CARD.id] = CARD_PROFILE
        val promotion = InstallmentRatePeriod(
            firstInstallment = 1,
            lastInstallment = 3,
            annualInterestBasisPoints = 0,
        )

        val result = useCase(
            FinancialCommand.RecordCardPurchase(
                commandId = "purchase-promotion",
                cardId = CARD.id,
                merchant = "Computador",
                principal = money(600_000),
                installmentCount = 6,
                purchasedAtEpochMillis = NOW,
                source = TransactionSource.VOICE,
                promotionalRatePeriods = listOf(promotion),
            ),
        )

        assertIs<FinancialCommandResult.Success>(result)
        assertEquals(listOf(promotion), manualFinanceRepository.savedPurchase?.first?.promotionalRatePeriods)
        assertEquals(0, manualFinanceRepository.savedPurchase?.first?.installmentSchedule?.first()?.interest?.minorUnits)
    }

    @Test
    fun `card purchase exceeding available credit is rejected without writes`() = runTest {
        accountRepository.accounts[CARD.id] = CARD
        manualFinanceRepository.cardProfiles[CARD.id] = CARD_PROFILE

        val result = useCase(
            FinancialCommand.RecordCardPurchase(
                commandId = "purchase-too-large",
                cardId = CARD.id,
                merchant = "Tienda",
                principal = money(2_000_000),
                installmentCount = 1,
                purchasedAtEpochMillis = NOW,
                source = TransactionSource.MANUAL,
            ),
        )

        val rejected = assertIs<FinancialCommandResult.Rejected>(result)
        assertTrue(FinancialCommandError.PURCHASE_EXCEEDS_AVAILABLE_CREDIT in rejected.errors)
        assertEquals(null, manualFinanceRepository.savedPurchase)
    }

    @Test
    fun `card payment records debt payment and liquid source ledger atomically`() = runTest {
        val cardWithDebt = CARD.copy(openingBalance = money(200_000))
        accountRepository.accounts[cardWithDebt.id] = cardWithDebt
        accountRepository.accounts[BANK.id] = BANK
        manualFinanceRepository.cardProfiles[cardWithDebt.id] = CARD_PROFILE

        val result = useCase(
            FinancialCommand.RecordCardPayment(
                commandId = "card-payment-1",
                cardId = cardWithDebt.id,
                amount = money(100_000),
                paidAtEpochMillis = NOW,
                source = TransactionSource.MANUAL,
                sourceProductId = BANK.id,
            ),
        )

        assertIs<FinancialCommandResult.Success>(result)
        val saved = manualFinanceRepository.savedCardPayment
        assertEquals(BANK.id, saved?.first?.sourceAccountId)
        assertEquals(TransactionType.TRANSFER, saved?.second?.type)
        assertEquals(cardWithDebt.id, saved?.second?.relatedAccountId)
    }

    @Test
    fun `scheduled card payment derives the current installment without model calculation`() = runTest {
        val cardWithDebt = CARD.copy(openingBalance = money(200_000))
        accountRepository.accounts[cardWithDebt.id] = cardWithDebt
        accountRepository.accounts[BANK.id] = BANK
        manualFinanceRepository.cardProfiles[cardWithDebt.id] = CARD_PROFILE.copy(
            openingDebtInstallmentCount = 2,
        )

        val result = useCase(
            FinancialCommand.RecordCardPayment(
                commandId = "scheduled-card-payment",
                cardId = cardWithDebt.id,
                paymentType = DebtPaymentType.SCHEDULED_INSTALLMENT,
                paidAtEpochMillis = NOW,
                source = TransactionSource.VOICE,
                sourceProductId = BANK.id,
            ),
        )

        assertIs<FinancialCommandResult.Success>(result)
        assertEquals(money(100_000), manualFinanceRepository.savedCardPayment?.first?.amount)
        assertEquals(
            DebtPaymentType.SCHEDULED_INSTALLMENT,
            manualFinanceRepository.savedCardPayment?.first?.type,
        )
    }

    @Test
    fun `scheduled card payment rejects a stale amount instead of executing it`() = runTest {
        val cardWithDebt = CARD.copy(openingBalance = money(200_000))
        accountRepository.accounts[cardWithDebt.id] = cardWithDebt
        manualFinanceRepository.cardProfiles[cardWithDebt.id] = CARD_PROFILE.copy(
            openingDebtInstallmentCount = 2,
        )

        val result = useCase(
            FinancialCommand.RecordCardPayment(
                commandId = "stale-card-payment",
                cardId = cardWithDebt.id,
                amount = money(90_000),
                paymentType = DebtPaymentType.SCHEDULED_INSTALLMENT,
                paidAtEpochMillis = NOW,
                source = TransactionSource.VOICE,
            ),
        )

        val rejected = assertIs<FinancialCommandResult.Rejected>(result)
        assertTrue(FinancialCommandError.PAYMENT_AMOUNT_MISMATCH in rejected.errors)
        assertEquals(null, manualFinanceRepository.savedCardPayment)
    }

    @Test
    fun `card payment from savings records the matching savings withdrawal`() = runTest {
        val cardWithDebt = CARD.copy(openingBalance = money(200_000))
        accountRepository.accounts[cardWithDebt.id] = cardWithDebt
        accountRepository.accounts[SAVINGS.id] = SAVINGS
        manualFinanceRepository.cardProfiles[cardWithDebt.id] = CARD_PROFILE
        manualFinanceRepository.savingsProfiles[SAVINGS.id] = SAVINGS_PROFILE

        val result = useCase(
            FinancialCommand.RecordCardPayment(
                commandId = "card-payment-from-savings",
                cardId = cardWithDebt.id,
                amount = money(50_000),
                paidAtEpochMillis = NOW,
                source = TransactionSource.MANUAL,
                sourceProductId = SAVINGS.id,
            ),
        )

        assertIs<FinancialCommandResult.Success>(result)
        assertEquals(
            SavingsMovementType.WITHDRAWAL,
            manualFinanceRepository.savedDebtSourceSavingsMovement?.type,
        )
        assertEquals(SAVINGS.id, manualFinanceRepository.savedDebtSourceSavingsMovement?.accountId)
    }

    @Test
    fun `savings deposit creates a transfer from its liquid source`() = runTest {
        accountRepository.accounts[SAVINGS.id] = SAVINGS
        accountRepository.accounts[BANK.id] = BANK
        manualFinanceRepository.savingsProfiles[SAVINGS.id] = SAVINGS_PROFILE

        val result = useCase(
            FinancialCommand.RecordSavingsMovement(
                commandId = "deposit-1",
                savingsId = SAVINGS.id,
                movementType = SavingsMovementType.DEPOSIT,
                amount = money(50_000),
                occurredAtEpochMillis = NOW,
                source = TransactionSource.MANUAL,
                sourceProductId = BANK.id,
            ),
        )

        assertIs<FinancialCommandResult.Success>(result)
        val saved = manualFinanceRepository.savedSavingsMovement
        assertEquals(TransactionType.TRANSFER, saved?.second?.type)
        assertEquals(BANK.id, saved?.second?.accountId)
        assertEquals(SAVINGS.id, saved?.second?.relatedAccountId)
    }

    @Test
    fun `savings withdrawal cannot exceed projected balance`() = runTest {
        accountRepository.accounts[SAVINGS.id] = SAVINGS
        manualFinanceRepository.savingsProfiles[SAVINGS.id] = SAVINGS_PROFILE

        val result = useCase(
            FinancialCommand.RecordSavingsMovement(
                commandId = "withdrawal-1",
                savingsId = SAVINGS.id,
                movementType = SavingsMovementType.WITHDRAWAL,
                amount = money(150_000),
                occurredAtEpochMillis = NOW,
                source = TransactionSource.MANUAL,
            ),
        )

        val rejected = assertIs<FinancialCommandResult.Rejected>(result)
        assertTrue(FinancialCommandError.WITHDRAWAL_EXCEEDS_SAVINGS in rejected.errors)
        assertEquals(null, manualFinanceRepository.savedSavingsMovement)
    }

    @Test
    fun `loan payment cannot exceed calculated debt`() = runTest {
        accountRepository.accounts[LOAN.id] = LOAN
        manualFinanceRepository.loanProfiles[LOAN.id] = LOAN_PROFILE

        val result = useCase(
            FinancialCommand.RecordLoanPayment(
                commandId = "loan-payment-1",
                loanId = LOAN.id,
                amount = money(1_500_000),
                paidAtEpochMillis = NOW,
                source = TransactionSource.MANUAL,
            ),
        )

        val rejected = assertIs<FinancialCommandResult.Rejected>(result)
        assertTrue(FinancialCommandError.PAYMENT_EXCEEDS_LOAN_DEBT in rejected.errors)
        assertEquals(null, manualFinanceRepository.savedLoanPayment)
    }

    @Test
    fun `loan payment records the loan event and source transfer`() = runTest {
        accountRepository.accounts[LOAN.id] = LOAN
        accountRepository.accounts[BANK.id] = BANK
        manualFinanceRepository.loanProfiles[LOAN.id] = LOAN_PROFILE

        val result = useCase(
            FinancialCommand.RecordLoanPayment(
                commandId = "loan-payment-success",
                loanId = LOAN.id,
                amount = money(100_000),
                paidAtEpochMillis = NOW,
                source = TransactionSource.MANUAL,
                sourceProductId = BANK.id,
            ),
        )

        assertIs<FinancialCommandResult.Success>(result)
        val saved = manualFinanceRepository.savedLoanPayment
        assertEquals(BANK.id, saved?.first?.sourceAccountId)
        assertEquals(TransactionType.TRANSFER, saved?.second?.type)
        assertEquals(LOAN.id, saved?.second?.relatedAccountId)
    }

    @Test
    fun `full loan payment derives the current balance`() = runTest {
        accountRepository.accounts[LOAN.id] = LOAN
        manualFinanceRepository.loanProfiles[LOAN.id] = LOAN_PROFILE

        val result = useCase(
            FinancialCommand.RecordLoanPayment(
                commandId = "full-loan-payment",
                loanId = LOAN.id,
                paymentType = DebtPaymentType.FULL_BALANCE,
                paidAtEpochMillis = NOW,
                source = TransactionSource.VOICE,
            ),
        )

        assertIs<FinancialCommandResult.Success>(result)
        assertEquals(LOAN.openingBalance, manualFinanceRepository.savedLoanPayment?.first?.amount)
        assertEquals(DebtPaymentType.FULL_BALANCE, manualFinanceRepository.savedLoanPayment?.first?.type)
    }

    @Test
    fun `command with an unknown calculation rule is rejected before persistence`() = runTest {
        accountRepository.accounts[CARD.id] = CARD
        manualFinanceRepository.cardProfiles[CARD.id] = CARD_PROFILE

        val result = useCase(
            FinancialCommand.RecordCardPurchase(
                commandId = "future-rule-purchase",
                cardId = CARD.id,
                merchant = "Tienda",
                principal = money(100_000),
                installmentCount = 2,
                purchasedAtEpochMillis = NOW,
                source = TransactionSource.VOICE,
                calculationRuleVersion = 99,
            ),
        )

        val rejected = assertIs<FinancialCommandResult.Rejected>(result)
        assertTrue(FinancialCommandError.UNSUPPORTED_RULE_VERSION in rejected.errors)
        assertEquals(null, manualFinanceRepository.savedPurchase)
    }

    @Test
    fun `linked ledger transaction cannot be deleted independently`() = runTest {
        val linked = FinancialTransaction(
            id = "purchase-linked",
            accountId = CARD.id,
            type = TransactionType.EXPENSE,
            amount = money(50_000),
            occurredAtEpochMillis = NOW,
            source = TransactionSource.MANUAL,
        )
        transactionRepository.transactions[linked.id] = linked

        val result = useCase(
            FinancialCommand.DeleteTransaction(
                commandId = "delete-1",
                transactionId = linked.id,
            ),
        )

        val rejected = assertIs<FinancialCommandResult.Rejected>(result)
        assertTrue(FinancialCommandError.LINKED_TRANSACTION_REQUIRES_PRODUCT_ACTION in rejected.errors)
        assertEquals(linked, transactionRepository.transactions[linked.id])
    }

    @Test
    fun `explicit transaction edit increments manual revision`() = runTest {
        accountRepository.accounts[BANK.id] = BANK
        val existing = FinancialTransaction(
            id = "transaction-editable",
            accountId = BANK.id,
            type = TransactionType.EXPENSE,
            amount = money(20_000),
            occurredAtEpochMillis = NOW,
            source = TransactionSource.BANK_NOTIFICATION,
            manualRevision = 1,
        )
        transactionRepository.transactions[existing.id] = existing

        val result = useCase(
            FinancialCommand.UpdateTransaction(
                commandId = "edit-1",
                transactionId = existing.id,
                productId = BANK.id,
                type = TransactionType.EXPENSE,
                amount = money(25_000),
                occurredAtEpochMillis = NOW,
                source = existing.source,
                merchant = "Comercio corregido",
            ),
        )

        assertIs<FinancialCommandResult.Success>(result)
        assertEquals(2, transactionRepository.transactions[existing.id]?.manualRevision)
        assertEquals("Comercio corregido", transactionRepository.transactions[existing.id]?.merchant)
    }

    private class FakeAccountRepository : FinancialAccountRepository {
        val accounts = mutableMapOf<String, FinancialAccount>()
        private val active = MutableStateFlow<List<FinancialAccount>>(emptyList())

        override fun observeActive(): Flow<List<FinancialAccount>> = active
        override suspend fun getById(id: String): FinancialAccount? = accounts[id]
        override suspend fun save(account: FinancialAccount) {
            accounts[account.id] = account
            active.value = accounts.values.filterNot { it.isArchived }
        }
    }

    private class FakeTransactionRepository : TransactionRepository {
        val transactions = mutableMapOf<String, FinancialTransaction>()
        private val observed = MutableStateFlow<List<FinancialTransaction>>(emptyList())

        override fun observeAll(): Flow<List<FinancialTransaction>> = observed
        override suspend fun getById(id: String): FinancialTransaction? = transactions[id]
        override suspend fun save(transaction: FinancialTransaction) {
            transactions[transaction.id] = transaction
            observed.value = transactions.values.toList()
        }
        override suspend fun delete(id: String) {
            transactions.remove(id)
            observed.value = transactions.values.toList()
        }
    }

    private class FakeManualFinanceRepository : ManualFinanceRepository {
        val cardProfiles = mutableMapOf<String, CreditCardProfile>()
        val savingsProfiles = mutableMapOf<String, SavingsProfile>()
        val loanProfiles = mutableMapOf<String, LoanProfile>()
        private val cardProfilesFlow = MutableStateFlow<List<CreditCardProfile>>(emptyList())
        private val purchases = MutableStateFlow<List<InstallmentPurchase>>(emptyList())
        private val cardPayments = MutableStateFlow<List<CreditCardPayment>>(emptyList())
        private val savingsProfilesFlow = MutableStateFlow<List<SavingsProfile>>(emptyList())
        private val savingsMovements = MutableStateFlow<List<SavingsMovement>>(emptyList())
        private val loanProfilesFlow = MutableStateFlow<List<LoanProfile>>(emptyList())
        private val loanPayments = MutableStateFlow<List<LoanPayment>>(emptyList())

        var savedProduct: Pair<FinancialAccount, FinancialProductConfiguration>? = null
        var savedPurchase: Pair<InstallmentPurchase, FinancialTransaction>? = null
        var savedCardPayment: Pair<CreditCardPayment, FinancialTransaction>? = null
        var savedSavingsMovement: Pair<SavingsMovement, FinancialTransaction?>? = null
        var savedLoanPayment: Pair<LoanPayment, FinancialTransaction>? = null
        var savedDebtSourceSavingsMovement: SavingsMovement? = null
        var savedRelatedSavingsMovement: SavingsMovement? = null

        override fun observeCreditCardProfiles(): Flow<List<CreditCardProfile>> = cardProfilesFlow
        override fun observeInstallmentPurchases(): Flow<List<InstallmentPurchase>> = purchases
        override fun observeCreditCardPayments(): Flow<List<CreditCardPayment>> = cardPayments
        override fun observeSavingsProfiles(): Flow<List<SavingsProfile>> = savingsProfilesFlow
        override fun observeSavingsMovements(): Flow<List<SavingsMovement>> = savingsMovements
        override fun observeLoanProfiles(): Flow<List<LoanProfile>> = loanProfilesFlow
        override fun observeLoanPayments(): Flow<List<LoanPayment>> = loanPayments
        override suspend fun getCreditCardProfile(accountId: String): CreditCardProfile? =
            cardProfiles[accountId]
        override suspend fun getSavingsProfile(accountId: String): SavingsProfile? =
            savingsProfiles[accountId]
        override suspend fun getLoanProfile(accountId: String): LoanProfile? =
            loanProfiles[accountId]

        override suspend fun saveProduct(
            account: FinancialAccount,
            configuration: FinancialProductConfiguration,
        ) {
            savedProduct = account to configuration
        }

        override suspend fun saveInstallmentPurchase(
            purchase: InstallmentPurchase,
            ledgerTransaction: FinancialTransaction,
        ) {
            savedPurchase = purchase to ledgerTransaction
            purchases.value = purchases.value + purchase
        }

        override suspend fun saveCreditCardPayment(
            payment: CreditCardPayment,
            ledgerTransaction: FinancialTransaction,
            sourceSavingsMovement: SavingsMovement?,
        ) {
            savedCardPayment = payment to ledgerTransaction
            savedDebtSourceSavingsMovement = sourceSavingsMovement
            cardPayments.value = cardPayments.value + payment
            sourceSavingsMovement?.let { savingsMovements.value = savingsMovements.value + it }
        }

        override suspend fun saveSavingsMovement(
            movement: SavingsMovement,
            ledgerTransaction: FinancialTransaction?,
            relatedSavingsMovement: SavingsMovement?,
        ) {
            savedSavingsMovement = movement to ledgerTransaction
            savedRelatedSavingsMovement = relatedSavingsMovement
            savingsMovements.value = savingsMovements.value +
                listOfNotNull(movement, relatedSavingsMovement)
        }

        override suspend fun saveLoanPayment(
            payment: LoanPayment,
            ledgerTransaction: FinancialTransaction,
            sourceSavingsMovement: SavingsMovement?,
        ) {
            savedLoanPayment = payment to ledgerTransaction
            savedDebtSourceSavingsMovement = sourceSavingsMovement
            loanPayments.value = loanPayments.value + payment
            sourceSavingsMovement?.let { savingsMovements.value = savingsMovements.value + it }
        }
    }

    private companion object {
        const val NOW = 1_800_000_000_000L
        const val FIRST_PAYMENT_AT = NOW + 2_592_000_000L

        fun money(value: Long) = Money(value, CurrencyCode.COP)

        val BANK = FinancialAccount(
            id = "bank",
            name = "Bancolombia",
            type = FinancialAccountType.BANK_ACCOUNT,
            currency = CurrencyCode.COP,
            openingBalance = money(500_000),
        )
        val CARD = FinancialAccount(
            id = "card",
            name = "Tarjeta",
            type = FinancialAccountType.CREDIT_CARD,
            currency = CurrencyCode.COP,
            openingBalance = money(0),
        )
        val CARD_PROFILE = CreditCardProfile(
            accountId = CARD.id,
            creditLimit = money(1_000_000),
            annualInterestBasisPoints = 2_400,
            statementClosingDay = 20,
            paymentDueDay = 8,
        )
        val SAVINGS = FinancialAccount(
            id = "savings",
            name = "Ahorro",
            type = FinancialAccountType.SAVINGS,
            currency = CurrencyCode.COP,
            openingBalance = money(100_000),
        )
        val SAVINGS_PROFILE = SavingsProfile(
            accountId = SAVINGS.id,
            type = SavingsProductType.SIMPLE,
            annualYieldBasisPoints = 0,
            openedAtEpochMillis = NOW - 86_400_000,
            maturityAtEpochMillis = null,
        )
        val LOAN = FinancialAccount(
            id = "loan",
            name = "Préstamo",
            type = FinancialAccountType.LOAN,
            currency = CurrencyCode.COP,
            openingBalance = money(1_000_000),
        )
        val LOAN_PROFILE = LoanProfile(
            accountId = LOAN.id,
            annualInterestBasisPoints = 0,
            monthlyPayment = money(100_000),
            paymentDueDay = 15,
            openedAtEpochMillis = NOW - 86_400_000,
        )
    }
}
