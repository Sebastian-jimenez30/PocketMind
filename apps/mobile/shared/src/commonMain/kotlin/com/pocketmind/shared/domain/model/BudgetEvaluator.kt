package com.pocketmind.shared.domain.model

/**
 * Pure, deterministic function to evaluate a budget against a list of transactions.
 * Reflector of the budget rules:
 * - Only valid EXPENSE movements increment the spent amount.
 * - IGNORED/voided movements are excluded.
 * - INCOME movements (refunds/reimbursements) in the same category decrease spent amount.
 * - Spent amount is never negative.
 */
fun evaluateBudget(
    budget: Budget,
    transactions: List<FinancialTransaction>,
    nowEpochMillis: Long,
    previousPeriodSpent: Money? = null,
): BudgetProgress {
    val validTransactions = transactions.filter { tx ->
        tx.categoryId == budget.categoryId.name &&
            tx.occurredAtEpochMillis in budget.startDateEpochMillis..budget.endDateEpochMillis &&
            tx.status != TransactionStatus.IGNORED &&
            tx.amount.currency == budget.maxAmount.currency
    }

    val expenseUnits = validTransactions
        .filter { it.type == TransactionType.EXPENSE }
        .sumOf { it.amount.minorUnits }
    val refundUnits = validTransactions
        .filter { it.type == TransactionType.INCOME }
        .sumOf { it.amount.minorUnits }
    val spentUnits = (expenseUnits - refundUnits).coerceAtLeast(0L)

    val availableUnits = budget.maxAmount.minorUnits - spentUnits

    val percentage = if (budget.maxAmount.minorUnits > 0L) {
        (spentUnits.toDouble() / budget.maxAmount.minorUnits.toDouble()) * 100.0
    } else {
        0.0
    }

    val dayMillis = 86_400_000L
    val totalDays = ((budget.endDateEpochMillis - budget.startDateEpochMillis) / dayMillis + 1L).coerceAtLeast(1L)
    val daysElapsed = if (nowEpochMillis < budget.startDateEpochMillis) {
        1L
    } else {
        ((nowEpochMillis - budget.startDateEpochMillis) / dayMillis + 1L).coerceAtMost(totalDays).coerceAtLeast(1L)
    }
    val dailyAverageUnits = spentUnits / daysElapsed
    val projectedSpentUnits = dailyAverageUnits * totalDays

    val newStatus = when {
        budget.status == BudgetStatus.PAUSED -> BudgetStatus.PAUSED
        nowEpochMillis > budget.endDateEpochMillis -> BudgetStatus.FINISHED
        percentage >= 100.0 -> BudgetStatus.EXCEEDED
        percentage >= budget.notificationThresholdPercent -> BudgetStatus.NEAR_LIMIT
        else -> BudgetStatus.ACTIVE
    }

    val alerts = buildList {
        if (percentage >= budget.notificationThresholdPercent && percentage < 100.0) {
            add(BudgetAlert.THRESHOLD_REACHED)
        }
        if (percentage >= 100.0) {
            add(BudgetAlert.LIMIT_EXCEEDED)
        }
        if (nowEpochMillis > budget.endDateEpochMillis) {
            add(BudgetAlert.PERIOD_FINISHED)
        }
        if (projectedSpentUnits > budget.maxAmount.minorUnits && nowEpochMillis <= budget.endDateEpochMillis && percentage < 100.0) {
            add(BudgetAlert.PROJECTION_EXCEEDS_LIMIT)
        }
    }

    val difference = previousPeriodSpent?.let { prev ->
        Money(spentUnits - prev.minorUnits, budget.maxAmount.currency)
    }

    return BudgetProgress(
        budget = budget.copy(status = newStatus),
        spentAmount = Money(spentUnits, budget.maxAmount.currency),
        availableAmount = Money(availableUnits, budget.maxAmount.currency),
        percentage = percentage,
        dailyAverage = Money(dailyAverageUnits, budget.maxAmount.currency),
        projectedSpent = Money(projectedSpentUnits, budget.maxAmount.currency),
        differenceFromPreviousPeriod = difference,
        transactionsCount = validTransactions.size,
        status = newStatus,
        alerts = alerts,
    )
}
