package com.pocketmind.shared.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BudgetEvaluatorTest {
    private val startMillis = 1_700_000_000_000L // arbitrary start
    private val endMillis = startMillis + 30L * 86_400_000L // 30 days later

    private val foodBudget = Budget(
        id = "budget-food-1",
        name = "Comida Mensual",
        categoryId = TransactionCategoryId.FOOD,
        maxAmount = Money(500_000, CurrencyCode.COP),
        periodType = BudgetPeriodType.MONTHLY,
        startDateEpochMillis = startMillis,
        endDateEpochMillis = endMillis,
        isRecurring = true,
        status = BudgetStatus.ACTIVE,
        notificationThresholdPercent = 80,
    )

    @Test
    fun `budget calculation sums expense and subtracts income refunds`() {
        val tx1 = FinancialTransaction(
            id = "tx-1",
            accountId = "acc-1",
            type = TransactionType.EXPENSE,
            amount = Money(200_000, CurrencyCode.COP),
            occurredAtEpochMillis = startMillis + 1000L,
            categoryId = TransactionCategoryId.FOOD.name,
            source = TransactionSource.MANUAL,
        )
        val tx2 = FinancialTransaction(
            id = "tx-2",
            accountId = "acc-1",
            type = TransactionType.INCOME,
            amount = Money(20_000, CurrencyCode.COP), // refund of 20,000
            occurredAtEpochMillis = startMillis + 2000L,
            categoryId = TransactionCategoryId.FOOD.name,
            source = TransactionSource.MANUAL,
        )

        val progress = evaluateBudget(
            budget = foodBudget,
            transactions = listOf(tx1, tx2),
            nowEpochMillis = startMillis + 15L * 86_400_000L,
        )

        assertEquals(180_000, progress.spentAmount.minorUnits)
        assertEquals(320_000, progress.availableAmount.minorUnits)
        assertEquals(36.0, progress.percentage, 0.01)
        assertEquals(BudgetStatus.ACTIVE, progress.status)
        assertEquals(2, progress.transactionsCount)
    }

    @Test
    fun `ignored transactions do not affect budget calculation`() {
        val tx1 = FinancialTransaction(
            id = "tx-1",
            accountId = "acc-1",
            type = TransactionType.EXPENSE,
            amount = Money(200_000, CurrencyCode.COP),
            occurredAtEpochMillis = startMillis + 1000L,
            categoryId = TransactionCategoryId.FOOD.name,
            source = TransactionSource.MANUAL,
            status = TransactionStatus.IGNORED,
        )

        val progress = evaluateBudget(
            budget = foodBudget,
            transactions = listOf(tx1),
            nowEpochMillis = startMillis + 10L * 86_400_000L,
        )

        assertEquals(0, progress.spentAmount.minorUnits)
        assertEquals(500_000, progress.availableAmount.minorUnits)
        assertEquals(0, progress.transactionsCount)
    }

    @Test
    fun `budget transitions to near limit when threshold is reached`() {
        val tx1 = FinancialTransaction(
            id = "tx-1",
            accountId = "acc-1",
            type = TransactionType.EXPENSE,
            amount = Money(410_000, CurrencyCode.COP), // 82% of 500,000
            occurredAtEpochMillis = startMillis + 1000L,
            categoryId = TransactionCategoryId.FOOD.name,
            source = TransactionSource.MANUAL,
        )

        val progress = evaluateBudget(
            budget = foodBudget,
            transactions = listOf(tx1),
            nowEpochMillis = startMillis + 10L * 86_400_000L,
        )

        assertEquals(410_000, progress.spentAmount.minorUnits)
        assertEquals(BudgetStatus.NEAR_LIMIT, progress.status)
        assertTrue(progress.alerts.contains(BudgetAlert.THRESHOLD_REACHED))
    }

    @Test
    fun `budget transitions to exceeded when 100 percent is reached`() {
        val tx1 = FinancialTransaction(
            id = "tx-1",
            accountId = "acc-1",
            type = TransactionType.EXPENSE,
            amount = Money(550_000, CurrencyCode.COP), // 110% of 500,000
            occurredAtEpochMillis = startMillis + 1000L,
            categoryId = TransactionCategoryId.FOOD.name,
            source = TransactionSource.MANUAL,
        )

        val progress = evaluateBudget(
            budget = foodBudget,
            transactions = listOf(tx1),
            nowEpochMillis = startMillis + 10L * 86_400_000L,
        )

        assertEquals(550_000, progress.spentAmount.minorUnits)
        assertEquals(-50_000, progress.availableAmount.minorUnits)
        assertEquals(BudgetStatus.EXCEEDED, progress.status)
        assertTrue(progress.alerts.contains(BudgetAlert.LIMIT_EXCEEDED))
    }

    @Test
    fun `budget transitions to finished when now exceeds end date`() {
        val progress = evaluateBudget(
            budget = foodBudget,
            transactions = emptyList(),
            nowEpochMillis = endMillis + 10_000L,
        )

        assertEquals(BudgetStatus.FINISHED, progress.status)
        assertTrue(progress.alerts.contains(BudgetAlert.PERIOD_FINISHED))
    }

    @Test
    fun `next recurring period creates next budget with identical duration`() {
        val nextBudget = foodBudget.nextRecurringPeriod()
        assertEquals(endMillis + 1L, nextBudget.startDateEpochMillis)
        assertEquals(endMillis + 1L + (endMillis - startMillis), nextBudget.endDateEpochMillis)
        assertEquals(BudgetStatus.ACTIVE, nextBudget.status)
    }
}
