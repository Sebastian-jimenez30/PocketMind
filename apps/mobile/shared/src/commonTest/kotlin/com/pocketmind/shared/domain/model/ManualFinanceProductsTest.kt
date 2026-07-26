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
