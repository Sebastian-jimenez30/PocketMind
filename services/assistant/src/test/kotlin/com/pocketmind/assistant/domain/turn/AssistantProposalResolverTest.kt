package com.pocketmind.assistant.domain.turn

import com.pocketmind.assistant.agent.chat.AssistantDecisionAction
import com.pocketmind.assistant.agent.chat.AssistantFinancialIntent
import com.pocketmind.assistant.agent.chat.AssistantModelDecision
import com.pocketmind.assistant.agent.chat.AssistantPromotionalRatePeriod
import com.pocketmind.assistant.auth.AuthenticatedUser
import com.pocketmind.assistant.auth.SupabaseAccessToken
import com.pocketmind.assistant.domain.finance.FinancialContextRepository
import com.pocketmind.assistant.domain.finance.FinancialContextSnapshot
import com.pocketmind.assistant.domain.finance.FinancialReadService
import com.pocketmind.assistant.testing.ReadOnlyMemoryRepository
import com.pocketmind.shared.domain.command.FinancialCommand
import com.pocketmind.shared.domain.model.CreditCardProfile
import com.pocketmind.shared.domain.model.CustomCategory
import com.pocketmind.shared.domain.model.CurrencyCode
import com.pocketmind.shared.domain.model.FinancialAccount
import com.pocketmind.shared.domain.model.FinancialAccountType
import com.pocketmind.shared.domain.model.FinancialProductConfiguration
import com.pocketmind.shared.domain.model.FinancialTransaction
import com.pocketmind.shared.domain.model.LoanProfile
import com.pocketmind.shared.domain.model.Money
import com.pocketmind.shared.domain.model.SavingsMovementType
import com.pocketmind.shared.domain.model.SavingsProductType
import com.pocketmind.shared.domain.model.SavingsProfile
import com.pocketmind.shared.domain.model.TransactionSource
import com.pocketmind.shared.domain.model.TransactionStatus
import com.pocketmind.shared.domain.model.TransactionType
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class AssistantProposalResolverTest {
    private val resolver = AssistantProposalResolver(TEST_CLOCK)

    @Test
    fun `card purchase keeps promotional installments in deterministic command`() = runTest {
        val result = resolver.resolve(
            decision = proposal(
                intent = AssistantFinancialIntent.RECORD_CARD_PURCHASE,
                primaryProductReference = CARD.id,
                amountMinorUnits = 1_200_000,
                merchant = "Celular",
                installmentCount = 6,
                promotionalRatePeriods = listOf(
                    AssistantPromotionalRatePeriod(
                        firstInstallment = 1,
                        lastInstallment = 3,
                        annualInterestBasisPoints = 0,
                    ),
                ),
            ),
            readService = readService(),
            commandId = "card-purchase",
        )

        val command = assertIs<FinancialCommand.RecordCardPurchase>(
            assertIs<AssistantProposalResolution.Proposal>(result).command,
        )
        assertEquals(CARD.id, command.cardId)
        assertEquals(6, command.installmentCount)
        assertEquals(1..3, command.promotionalRatePeriods.single().run {
            firstInstallment..lastInstallment
        })
    }

    @Test
    fun `scheduled card payment derives amount from current card state`() = runTest {
        val result = resolver.resolve(
            decision = proposal(
                intent = AssistantFinancialIntent.RECORD_CARD_PAYMENT,
                primaryProductReference = CARD.id,
                sourceProductReference = BANK.id,
                paymentType = "SCHEDULED_INSTALLMENT",
            ),
            readService = readService(),
            commandId = "card-payment",
        )

        val proposal = assertIs<AssistantProposalResolution.Proposal>(result)
        val command = assertIs<FinancialCommand.RecordCardPayment>(proposal.command)
        assertEquals(null, command.amount)
        assertEquals(BANK.id, command.sourceProductId)
        assertTrue(requireNotNull(proposal.amount).minorUnits > 0)
    }

    @Test
    fun `unknown internal product id is never exposed in clarification`() = runTest {
        val internalId = "a3924ade-4ed5-4530-bf68-bff93a152354"
        val result = resolver.resolve(
            decision = proposal(
                intent = AssistantFinancialIntent.RECORD_CARD_PAYMENT,
                primaryProductReference = internalId,
                paymentType = "FULL_BALANCE",
            ),
            readService = readService(),
            commandId = "safe-clarification",
        )

        val clarification = assertIs<AssistantProposalResolution.Clarification>(result)
        assertEquals("¿Cuál tarjeta quieres pagar?", clarification.message)
        assertFalse(clarification.message.contains(internalId))
    }

    @Test
    fun `savings rate change does not invent a money movement`() = runTest {
        val result = resolver.resolve(
            decision = proposal(
                intent = AssistantFinancialIntent.RECORD_SAVINGS_MOVEMENT,
                primaryProductReference = SAVINGS.id,
                savingsMovementType = "RATE_CHANGE",
                annualRateBasisPoints = 1_100,
            ),
            readService = readService(),
            commandId = "rate-change",
        )

        val proposal = assertIs<AssistantProposalResolution.Proposal>(result)
        val command = assertIs<FinancialCommand.RecordSavingsMovement>(proposal.command)
        assertEquals(SavingsMovementType.RATE_CHANGE, command.movementType)
        assertEquals(0L, command.amount.minorUnits)
        assertEquals(1_100, command.annualYieldBasisPoints)
        assertEquals(null, proposal.amount)
    }

    @Test
    fun `creating a CDT produces a complete term deposit configuration`() = runTest {
        val result = resolver.resolve(
            decision = proposal(
                intent = AssistantFinancialIntent.CREATE_PRODUCT,
                productName = "CDT Bancolombia",
                productType = "CDT",
                savingsProductType = "TERM_DEPOSIT",
                amountMinorUnits = 2_000_000,
                annualRateBasisPoints = 1_100,
                openedAtEpochMillis = NOW,
                maturityAtEpochMillis = NOW + 180L * DAY_MILLIS,
            ),
            readService = readService(),
            commandId = "create-cdt",
        )

        val command = assertIs<FinancialCommand.CreateProduct>(
            assertIs<AssistantProposalResolution.Proposal>(result).command,
        )
        val configuration = assertIs<FinancialProductConfiguration.Savings>(
            command.configuration,
        )
        assertEquals(FinancialAccountType.SAVINGS, command.account.type)
        assertEquals(SavingsProductType.TERM_DEPOSIT, configuration.profile.type)
        assertEquals(1_100, configuration.profile.annualYieldBasisPoints)
    }

    @Test
    fun `transaction edit preserves omitted fields and rejects unknown types`() = runTest {
        val invalid = resolver.resolve(
            decision = proposal(
                intent = AssistantFinancialIntent.UPDATE_TRANSACTION,
                transactionId = TRANSACTION.id,
                transactionType = "UNKNOWN",
            ),
            readService = readService(),
            commandId = "invalid-edit",
        )
        assertIs<AssistantProposalResolution.Clarification>(invalid)

        val result = resolver.resolve(
            decision = proposal(
                intent = AssistantFinancialIntent.UPDATE_TRANSACTION,
                transactionId = TRANSACTION.id,
                amountMinorUnits = 25_000,
            ),
            readService = readService(),
            commandId = "valid-edit",
        )

        val command = assertIs<FinancialCommand.UpdateTransaction>(
            assertIs<AssistantProposalResolution.Proposal>(result).command,
        )
        assertEquals(25_000L, command.amount.minorUnits)
        assertEquals(TRANSACTION.note, command.note)
        assertEquals(TRANSACTION.merchant, command.merchant)
    }

    @Test
    fun `natural bank reference resolves an external transfer as expense`() = runTest {
        val result = resolver.resolve(
            decision = proposal(
                intent = AssistantFinancialIntent.RECORD_EXPENSE,
                primaryProductReference = "desde mi cuenta de Bancolombia",
                amountMinorUnits = 20_000,
                merchant = "Mi novia",
            ),
            readService = readService(),
            commandId = "external-transfer",
        )

        val command = assertIs<FinancialCommand.RecordExpense>(
            assertIs<AssistantProposalResolution.Proposal>(result).command,
        )
        assertEquals(BANK.id, command.productId)
        assertEquals(20_000L, command.amount.minorUnits)
    }

    @Test
    fun `custom category selected by the model is preserved in the command`() = runTest {
        val result = resolver.resolve(
            decision = proposal(
                intent = AssistantFinancialIntent.RECORD_EXPENSE,
                primaryProductReference = BANK.id,
                amountMinorUnits = 35_000,
                merchant = "Veterinaria",
                categoryId = CUSTOM_CATEGORY.name,
            ),
            readService = readService(),
            commandId = "custom-category-expense",
        )

        val command = assertIs<FinancialCommand.RecordExpense>(
            assertIs<AssistantProposalResolution.Proposal>(result).command,
        )
        assertEquals(CUSTOM_CATEGORY.id, command.categoryId)
    }

    @Test
    fun `omitted product resolves automatically when only one is compatible`() = runTest {
        val result = resolver.resolve(
            decision = proposal(
                intent = AssistantFinancialIntent.RECORD_EXPENSE,
                amountMinorUnits = 5_000,
                merchant = "Tienda",
            ),
            readService = FinancialReadService(
                contextRepository = FinancialContextRepository {
                    snapshot().copy(
                        accounts = listOf(BANK),
                        creditCardProfiles = emptyList(),
                        savingsProfiles = emptyList(),
                        loanProfiles = emptyList(),
                    )
                },
                memoryRepository = ReadOnlyMemoryRepository(),
                session = TEST_SESSION,
                clock = TEST_CLOCK,
            ),
            commandId = "single-compatible-product",
        )

        val command = assertIs<FinancialCommand.RecordExpense>(
            assertIs<AssistantProposalResolution.Proposal>(result).command,
        )
        assertEquals(BANK.id, command.productId)
        assertEquals(5_000L, command.amount.minorUnits)
    }

    @Test
    fun `savings deposit preserves its funding product`() = runTest {
        val result = resolver.resolve(
            decision = proposal(
                intent = AssistantFinancialIntent.RECORD_SAVINGS_MOVEMENT,
                primaryProductReference = SAVINGS.id,
                sourceProductReference = BANK.id,
                amountMinorUnits = 80_000,
                savingsMovementType = "DEPOSIT",
            ),
            readService = readService(),
            commandId = "savings-deposit",
        )

        val command = assertIs<FinancialCommand.RecordSavingsMovement>(
            assertIs<AssistantProposalResolution.Proposal>(result).command,
        )
        assertEquals(SavingsMovementType.DEPOSIT, command.movementType)
        assertEquals(BANK.id, command.sourceProductId)
        assertEquals(null, command.destinationProductId)
    }

    @Test
    fun `scheduled loan payment derives amount and preserves funding product`() = runTest {
        val result = resolver.resolve(
            decision = proposal(
                intent = AssistantFinancialIntent.RECORD_LOAN_PAYMENT,
                primaryProductReference = LOAN.id,
                sourceProductReference = BANK.id,
                paymentType = "SCHEDULED_INSTALLMENT",
            ),
            readService = readService(),
            commandId = "loan-payment",
        )

        val proposal = assertIs<AssistantProposalResolution.Proposal>(result)
        val command = assertIs<FinancialCommand.RecordLoanPayment>(proposal.command)
        assertEquals(null, command.amount)
        assertEquals(BANK.id, command.sourceProductId)
        assertEquals(100_000L, requireNotNull(proposal.amount).minorUnits)
    }

    @Test
    fun `product edit merges only requested credit card configuration`() = runTest {
        val result = resolver.resolve(
            decision = proposal(
                intent = AssistantFinancialIntent.UPDATE_PRODUCT,
                primaryProductReference = CARD.id,
                annualRateBasisPoints = 2_900,
            ),
            readService = readService(),
            commandId = "update-card",
        )

        val command = assertIs<FinancialCommand.UpdateProduct>(
            assertIs<AssistantProposalResolution.Proposal>(result).command,
        )
        val configuration = assertIs<FinancialProductConfiguration.CreditCard>(
            command.configuration,
        )
        assertEquals(2_900, configuration.profile.annualInterestBasisPoints)
        assertEquals(10, configuration.profile.statementClosingDay)
        assertEquals(25, configuration.profile.paymentDueDay)
    }

    @Test
    fun `manual transaction can be deleted through a supervised proposal`() = runTest {
        val result = resolver.resolve(
            decision = proposal(
                intent = AssistantFinancialIntent.DELETE_TRANSACTION,
                transactionId = TRANSACTION.id,
            ),
            readService = readService(),
            commandId = "delete-transaction",
        )

        val command = assertIs<FinancialCommand.DeleteTransaction>(
            assertIs<AssistantProposalResolution.Proposal>(result).command,
        )
        assertEquals(TRANSACTION.id, command.transactionId)
    }

    private fun readService(): FinancialReadService = FinancialReadService(
        contextRepository = FinancialContextRepository { snapshot() },
        memoryRepository = ReadOnlyMemoryRepository(),
        session = TEST_SESSION,
        clock = TEST_CLOCK,
    )
}

private fun proposal(
    intent: AssistantFinancialIntent,
    primaryProductReference: String? = null,
    sourceProductReference: String? = null,
    amountMinorUnits: Long? = null,
    merchant: String? = null,
    productName: String? = null,
    productType: String? = null,
    annualRateBasisPoints: Int? = null,
    savingsProductType: String? = null,
    openedAtEpochMillis: Long? = null,
    maturityAtEpochMillis: Long? = null,
    installmentCount: Int? = null,
    promotionalRatePeriods: List<AssistantPromotionalRatePeriod> = emptyList(),
    paymentType: String? = null,
    savingsMovementType: String? = null,
    transactionId: String? = null,
    transactionType: String? = null,
    categoryId: String? = null,
): AssistantModelDecision = AssistantModelDecision(
    action = AssistantDecisionAction.PROPOSE,
    intent = intent,
    primaryProductReference = primaryProductReference,
    sourceProductReference = sourceProductReference,
    amountMinorUnits = amountMinorUnits,
    merchant = merchant,
    productName = productName,
    productType = productType,
    annualRateBasisPoints = annualRateBasisPoints,
    savingsProductType = savingsProductType,
    openedAtEpochMillis = openedAtEpochMillis,
    maturityAtEpochMillis = maturityAtEpochMillis,
    installmentCount = installmentCount,
    promotionalRatePeriods = promotionalRatePeriods,
    paymentType = paymentType,
    savingsMovementType = savingsMovementType,
    transactionId = transactionId,
    transactionType = transactionType,
    categoryId = categoryId,
)

private fun snapshot(): FinancialContextSnapshot = FinancialContextSnapshot(
    stateVersion = 17L,
    latestRemoteUpdateEpochMillis = NOW,
    supportedSchemaVersion = 2,
    remoteRecordCount = 8,
    unknownEntityTypes = emptySet(),
    accounts = listOf(BANK, CARD, SAVINGS, LOAN),
    transactions = listOf(TRANSACTION),
    incomeSources = emptyList(),
    debts = emptyList(),
    savingsPlans = emptyList(),
    recurringObligations = emptyList(),
    creditCardProfiles = listOf(
        CreditCardProfile(
            accountId = CARD.id,
            creditLimit = Money(2_000_000, CurrencyCode.COP),
            annualInterestBasisPoints = 2_500,
            statementClosingDay = 10,
            paymentDueDay = 25,
            openingDebtInstallmentCount = 4,
            openingDebtFirstPaymentAtEpochMillis = NOW + DAY_MILLIS,
        ),
    ),
    installmentPurchases = emptyList(),
    creditCardPayments = emptyList(),
    savingsProfiles = listOf(
        SavingsProfile(
            accountId = SAVINGS.id,
            type = SavingsProductType.POCKET,
            annualYieldBasisPoints = 900,
            openedAtEpochMillis = NOW - DAY_MILLIS,
            maturityAtEpochMillis = null,
        ),
    ),
    savingsMovements = emptyList(),
    loanProfiles = listOf(
        LoanProfile(
            accountId = LOAN.id,
            annualInterestBasisPoints = 1_800,
            monthlyPayment = Money(100_000, CurrencyCode.COP),
            paymentDueDay = 15,
            openedAtEpochMillis = NOW - DAY_MILLIS,
        ),
    ),
    loanPayments = emptyList(),
    customCategories = listOf(CUSTOM_CATEGORY),
)

private fun account(
    id: String,
    name: String,
    type: FinancialAccountType,
    openingBalance: Long,
): FinancialAccount = FinancialAccount(
    id = id,
    name = name,
    type = type,
    currency = CurrencyCode.COP,
    openingBalance = Money(openingBalance, CurrencyCode.COP),
)

private val BANK = account(
    id = "bank",
    name = "Ahorros Bancolombia",
    type = FinancialAccountType.BANK_ACCOUNT,
    openingBalance = 500_000,
)
private val CARD = account(
    id = "card",
    name = "Visa Bancolombia",
    type = FinancialAccountType.CREDIT_CARD,
    openingBalance = 400_000,
)
private val SAVINGS = account(
    id = "savings",
    name = "Cajita Nu",
    type = FinancialAccountType.SAVINGS,
    openingBalance = 80_000,
)
private val LOAN = account(
    id = "loan",
    name = "Préstamo",
    type = FinancialAccountType.LOAN,
    openingBalance = 1_000_000,
)
private val TRANSACTION = FinancialTransaction(
    id = "manual-expense",
    accountId = BANK.id,
    type = TransactionType.EXPENSE,
    amount = Money(20_000, CurrencyCode.COP),
    occurredAtEpochMillis = NOW - DAY_MILLIS,
    categoryId = "OTHER",
    merchant = "Tienda",
    note = "Compra semanal",
    source = TransactionSource.MANUAL,
    status = TransactionStatus.POSTED,
)
private val CUSTOM_CATEGORY = CustomCategory(
    id = "category-pets",
    name = "Mascotas",
    createdAtEpochMillis = NOW - DAY_MILLIS,
)
private val TEST_SESSION = AuthenticatedUser(
    userId = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa",
    role = "authenticated",
    accessToken = SupabaseAccessToken("test-token"),
)
private val TEST_CLOCK = Clock.fixed(Instant.ofEpochMilli(NOW), ZoneOffset.UTC)
private const val NOW = 1_775_000_000_000L
private const val DAY_MILLIS = 86_400_000L
