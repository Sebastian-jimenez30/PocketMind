package com.pocketmind.shared.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ManualFinanceProductsTest {
    @Test
    fun `zero interest purchase splits principal evenly`() {
        val purchase = purchase(principal = 1_200_000, installments = 12, rate = 0)

        assertEquals(100_000, purchase.installmentAmount.minorUnits)
        assertEquals(1_200_000, purchase.financedTotal.minorUnits)
    }

    @Test
    fun `promotional installments use zero rate before the configured card rate`() {
        val purchase = InstallmentPurchase(
            id = "promotion",
            accountId = "card",
            merchant = "Comercio",
            principal = money(1_200_000),
            installmentCount = 12,
            annualInterestBasisPoints = 2_400,
            purchasedAtEpochMillis = 1L,
            firstPaymentAtEpochMillis = 2L,
            categoryId = null,
            note = null,
            promotionalRatePeriods = listOf(
                InstallmentRatePeriod(
                    firstInstallment = 1,
                    lastInstallment = 3,
                    annualInterestBasisPoints = 0,
                ),
            ),
        )

        assertEquals(listOf(0, 0, 0), purchase.installmentSchedule.take(3).map { it.annualInterestBasisPoints })
        assertEquals(2_400, purchase.installmentSchedule[3].annualInterestBasisPoints)
        assertTrue(purchase.financedTotal.minorUnits > purchase.principal.minorUnits)
        assertEquals(CURRENT_FINANCIAL_RULE_VERSION, purchase.installmentSchedule.last().calculationRuleVersion)
    }

    @Test
    fun `card overview aggregates purchases with different installment plans`() {
        val profile = CreditCardProfile(
            accountId = "card",
            creditLimit = money(5_000_000),
            annualInterestBasisPoints = 0,
            statementClosingDay = 10,
            paymentDueDay = 25,
        )
        val first = purchase(id = "first", principal = 1_200_000, installments = 12, rate = 0)
        val second = purchase(id = "second", principal = 600_000, installments = 6, rate = 0)
        val overview = calculateCreditCardOverview(
            profile,
            openingDebt = money(0),
            purchases = listOf(first, second),
            payments = listOf(
                CreditCardPayment("payment", "card", money(200_000), 2L, null, null),
            ),
        )

        assertEquals(1_600_000, overview.currentDebt.minorUnits)
        assertEquals(3_400_000, overview.availableCredit.minorUnits)
        assertEquals(2, overview.paidInstallments["first"])
        assertEquals(200_000, overview.nextPayment.minorUnits)
    }

    @Test
    fun `savings projection compounds annual yield and preserves contributed capital`() {
        val profile = SavingsProfile(
            accountId = "savings",
            type = SavingsProductType.POCKET,
            annualYieldBasisPoints = 1_000,
            openedAtEpochMillis = 1_000L,
            maturityAtEpochMillis = null,
        )

        val projection = calculateSavingsProjection(
            profile = profile,
            openingBalance = money(1_000_000),
            movements = emptyList(),
            atEpochMillis = 1_000L + 365L * 86_400_000L,
        )

        assertEquals(1_000_000, projection.contributedBalance.minorUnits)
        assertTrue(projection.currentBalance.minorUnits in 1_099_999..1_100_001)
        assertTrue(projection.estimatedYield.minorUnits in 99_999..100_001)
    }

    @Test
    fun `daily savings progress exposes accumulated effective annual yield`() {
        val profile = SavingsProfile(
            accountId = "savings",
            type = SavingsProductType.POCKET,
            annualYieldBasisPoints = 1_000,
            openedAtEpochMillis = 1_000L,
            maturityAtEpochMillis = null,
        )

        val progress = calculateSavingsDailyProgress(
            profile,
            openingBalance = money(1_000_000),
            movements = emptyList(),
            fromEpochMillis = 1_000L,
            toEpochMillis = 1_000L + 2L * 86_400_000L,
        )

        assertEquals(3, progress.size)
        assertTrue(progress.last().balance.minorUnits > progress.first().balance.minorUnits)
        assertTrue(progress.last().estimatedYield.minorUnits > 0)
    }

    @Test
    fun `loan payments reduce debt after effective annual interest`() {
        val year = 365L * 86_400_000L
        val profile = LoanProfile(
            accountId = "loan",
            annualInterestBasisPoints = 1_000,
            monthlyPayment = money(100_000),
            paymentDueDay = 15,
            openedAtEpochMillis = 1_000L,
        )

        val overview = calculateLoanOverview(
            profile,
            openingDebt = money(1_000_000),
            payments = listOf(
                LoanPayment("payment", "loan", money(100_000), 1_000L + year, null, null),
            ),
            atEpochMillis = 1_000L + year,
        )

        assertTrue(overview.currentDebt.minorUnits in 999_999..1_000_001)
        assertTrue(overview.estimatedInterest.minorUnits in 99_999..100_001)
        assertEquals(100_000, overview.nextPayment.minorUnits)
    }

    private fun purchase(
        id: String = "purchase",
        principal: Long,
        installments: Int,
        rate: Int,
    ) = InstallmentPurchase(
        id = id,
        accountId = "card",
        merchant = "Comercio",
        principal = money(principal),
        installmentCount = installments,
        annualInterestBasisPoints = rate,
        purchasedAtEpochMillis = 1L,
        firstPaymentAtEpochMillis = 2L,
        categoryId = null,
        note = null,
    )

    private fun money(value: Long) = Money(value, CurrencyCode.COP)
}
