package com.pocketmind.assistant.domain.finance

import com.pocketmind.assistant.auth.AuthenticatedUser
import com.pocketmind.assistant.auth.SupabaseAccessToken
import com.pocketmind.assistant.domain.memory.AssistantProductAlias
import com.pocketmind.assistant.testing.ReadOnlyMemoryRepository
import com.pocketmind.shared.domain.model.CreditCardProfile
import com.pocketmind.shared.domain.model.CurrencyCode
import com.pocketmind.shared.domain.model.FinancialAccount
import com.pocketmind.shared.domain.model.FinancialAccountType
import com.pocketmind.shared.domain.model.FinancialTransaction
import com.pocketmind.shared.domain.model.LoanProfile
import com.pocketmind.shared.domain.model.Money
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FinancialReadServiceTest {
    @Test
    fun `overview keeps currencies separate and reuses deterministic balances`() =
        runTest {
            val service = readService(snapshot = baseSnapshot())

            val result = service.getOverview()

            val cop = result.currencies.single { it.currency == "COP" }
            assertEquals(400_000L, cop.liquidBalanceMinorUnits)
            assertEquals(300_000L, cop.monthlyIncomeMinorUnits)
            assertEquals(50_000L, cop.monthlyExpenseMinorUnits)
            assertEquals(80_000L, cop.productSavingsBalanceMinorUnits)
            assertEquals(200_000L, cop.creditCardDebtMinorUnits)
            assertEquals(500_000L, cop.loanDebtMinorUnits)
            val usd = result.currencies.single { it.currency == "USD" }
            assertEquals(100L, usd.liquidBalanceMinorUnits)
            assertEquals(STATE_VERSION, result.metadata.stateVersion)
            assertTrue(result.metadata.mustRevalidateBeforeWrite)
            assertTrue(result.metadata.mayLagUnsyncedDeviceChanges)
        }

    @Test
    fun `confirmed alias resolves one real product without fuzzy matching`() =
        runTest {
            val alias = productAlias(
                productId = BANK.id,
                value = "mi banco",
            )
            val service = readService(
                snapshot = baseSnapshot(),
                aliases = listOf(alias),
            )

            val resolved = service.getProduct("mi banco")
            val missing = service.getProduct("bancolombia parecida")

            assertEquals("resolved", resolved.status)
            assertEquals(BANK.id, resolved.product?.id)
            assertTrue(resolved.product?.aliases.orEmpty().contains("mi banco"))
            assertEquals("not_found", missing.status)
            assertNull(missing.product)
        }

    @Test
    fun `ambiguous alias returns only user candidates and never chooses`() =
        runTest {
            val service = readService(
                snapshot = baseSnapshot().copy(
                    accounts = baseSnapshot().accounts.map { account ->
                        if (account.id == BANK.id || account.id == CASH.id) {
                            account.copy(aliases = listOf("principal"))
                        } else {
                            account
                        }
                    },
                ),
            )

            val result = service.getProduct("principal")

            assertEquals("ambiguous", result.status)
            assertNull(result.product)
            assertEquals(setOf(BANK.id, CASH.id), result.candidates.map { it.id }.toSet())
        }

    @Test
    fun `transaction query resolves product filters and reports truncation`() =
        runTest {
            val service = readService(snapshot = baseSnapshot())

            val result = service.listTransactions(
                TransactionQuery(
                    productReference = BANK.name,
                    status = "POSTED",
                    limit = 2,
                ),
            )

            assertEquals("ok", result.status)
            assertEquals(BANK.id, result.resolvedProduct?.id)
            assertEquals(3, result.totalMatches)
            assertEquals(2, result.returnedCount)
            assertTrue(result.truncated)
            assertTrue(result.transactions.all {
                it.productId == BANK.id || it.relatedProductId == BANK.id
            })
        }

    @Test
    fun `archived products stay hidden unless explicitly requested`() = runTest {
        val service = readService(snapshot = baseSnapshot())

        val default = service.listProducts(includeArchived = false)
        val complete = service.listProducts(includeArchived = true)

        assertFalse(default.products.any { it.id == ARCHIVED.id })
        assertTrue(complete.products.any { it.id == ARCHIVED.id })
    }
}

private fun readService(
    snapshot: FinancialContextSnapshot,
    aliases: List<AssistantProductAlias> = emptyList(),
): FinancialReadService = FinancialReadService(
    contextRepository = FinancialContextRepository { snapshot },
    memoryRepository = ReadOnlyMemoryRepository(aliases),
    session = TEST_SESSION,
    clock = Clock.fixed(
        Instant.ofEpochMilli(NOW),
        ZoneOffset.UTC,
    ),
)

private fun baseSnapshot(): FinancialContextSnapshot =
    FinancialContextSnapshot(
        stateVersion = STATE_VERSION,
        latestRemoteUpdateEpochMillis = NOW - 1_000,
        supportedSchemaVersion = 2,
        remoteRecordCount = 14,
        unknownEntityTypes = emptySet(),
        accounts = listOf(BANK, CASH, USD_CASH, CARD, SAVINGS, LOAN, ARCHIVED),
        transactions = listOf(
            transaction(
                id = "income",
                accountId = BANK.id,
                type = TransactionType.INCOME,
                amount = 300_000,
                at = NOW - 300_000,
            ),
            transaction(
                id = "expense",
                accountId = BANK.id,
                type = TransactionType.EXPENSE,
                amount = 50_000,
                at = NOW - 200_000,
            ),
            transaction(
                id = "transfer",
                accountId = BANK.id,
                type = TransactionType.TRANSFER,
                amount = 20_000,
                at = NOW - 100_000,
                relatedAccountId = CASH.id,
            ),
        ),
        incomeSources = emptyList(),
        debts = emptyList(),
        savingsPlans = emptyList(),
        recurringObligations = emptyList(),
        creditCardProfiles = listOf(
            CreditCardProfile(
                accountId = CARD.id,
                creditLimit = Money(1_000_000, CurrencyCode.COP),
                annualInterestBasisPoints = 2_500,
                statementClosingDay = 10,
                paymentDueDay = 25,
            ),
        ),
        installmentPurchases = emptyList(),
        creditCardPayments = emptyList(),
        savingsProfiles = listOf(
            SavingsProfile(
                accountId = SAVINGS.id,
                type = SavingsProductType.POCKET,
                annualYieldBasisPoints = 0,
                openedAtEpochMillis = NOW - 86_400_000,
                maturityAtEpochMillis = null,
            ),
        ),
        savingsMovements = emptyList(),
        loanProfiles = listOf(
            LoanProfile(
                accountId = LOAN.id,
                annualInterestBasisPoints = 0,
                monthlyPayment = Money(50_000, CurrencyCode.COP),
                paymentDueDay = 15,
                openedAtEpochMillis = NOW - 86_400_000,
            ),
        ),
        loanPayments = emptyList(),
    )

private fun transaction(
    id: String,
    accountId: String,
    type: TransactionType,
    amount: Long,
    at: Long,
    relatedAccountId: String? = null,
): FinancialTransaction = FinancialTransaction(
    id = id,
    accountId = accountId,
    type = type,
    amount = Money(amount, CurrencyCode.COP),
    occurredAtEpochMillis = at,
    categoryId = null,
    merchant = null,
    note = null,
    source = TransactionSource.MANUAL,
    status = TransactionStatus.POSTED,
    relatedAccountId = relatedAccountId,
)

private fun productAlias(
    productId: String,
    value: String,
): AssistantProductAlias = AssistantProductAlias(
    id = "alias-$productId",
    userId = USER_ID,
    productId = productId,
    productType = "BANK_ACCOUNT",
    alias = value,
    normalizedAlias = value,
    createdAt = Instant.ofEpochMilli(NOW),
    updatedAt = Instant.ofEpochMilli(NOW),
)

private fun account(
    id: String,
    name: String,
    type: FinancialAccountType,
    balance: Long,
    currency: CurrencyCode = CurrencyCode.COP,
    archived: Boolean = false,
): FinancialAccount = FinancialAccount(
    id = id,
    name = name,
    type = type,
    currency = currency,
    openingBalance = Money(balance, currency),
    isArchived = archived,
)

private val BANK = account("bank", "Cuenta Bancolombia", FinancialAccountType.BANK_ACCOUNT, 100_000)
private val CASH = account("cash", "Efectivo", FinancialAccountType.CASH, 50_000)
private val USD_CASH = account(
    "usd",
    "Dólares",
    FinancialAccountType.CASH,
    100,
    CurrencyCode.USD,
)
private val CARD = account("card", "Tarjeta", FinancialAccountType.CREDIT_CARD, 200_000)
private val SAVINGS = account("savings", "Cajita", FinancialAccountType.SAVINGS, 80_000)
private val LOAN = account("loan", "Préstamo", FinancialAccountType.LOAN, 500_000)
private val ARCHIVED = account(
    "archived",
    "Anterior",
    FinancialAccountType.BANK_ACCOUNT,
    0,
    archived = true,
)
private val TEST_SESSION = AuthenticatedUser(
    userId = USER_ID,
    role = "authenticated",
    accessToken = SupabaseAccessToken("access-token"),
)

private const val USER_ID = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa"
private const val STATE_VERSION = 42L
private const val NOW = 1_775_000_000_000L
