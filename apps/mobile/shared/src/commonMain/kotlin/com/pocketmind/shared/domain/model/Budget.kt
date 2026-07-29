package com.pocketmind.shared.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class BudgetPeriodType {
    @SerialName("monthly")
    MONTHLY,

    @SerialName("weekly")
    WEEKLY,

    @SerialName("biweekly")
    BIWEEKLY,

    @SerialName("custom")
    CUSTOM,
}

@Serializable
enum class BudgetStatus {
    @SerialName("active")
    ACTIVE,

    @SerialName("near_limit")
    NEAR_LIMIT,

    @SerialName("exceeded")
    EXCEEDED,

    @SerialName("finished")
    FINISHED,

    @SerialName("paused")
    PAUSED,
}

@Serializable
enum class BudgetAlert {
    @SerialName("threshold_reached")
    THRESHOLD_REACHED,

    @SerialName("limit_exceeded")
    LIMIT_EXCEEDED,

    @SerialName("period_finished")
    PERIOD_FINISHED,

    @SerialName("projection_exceeds_limit")
    PROJECTION_EXCEEDS_LIMIT,
}

@Serializable
data class Budget(
    val id: String,
    val name: String,
    @SerialName("category_id")
    val categoryId: String,
    @SerialName("max_amount")
    val maxAmount: Money,
    @SerialName("period_type")
    val periodType: BudgetPeriodType,
    @SerialName("start_date_epoch_millis")
    val startDateEpochMillis: Long,
    @SerialName("end_date_epoch_millis")
    val endDateEpochMillis: Long,
    @SerialName("is_recurring")
    val isRecurring: Boolean = true,
    val status: BudgetStatus = BudgetStatus.ACTIVE,
    @SerialName("notification_threshold_percent")
    val notificationThresholdPercent: Int = 80,
) {
    init {
        require(id.isNotBlank()) { "A budget id is required." }
        require(name.isNotBlank()) { "A budget name is required." }
        require(categoryId.isNotBlank()) { "A category id is required." }
        require(maxAmount.isPositive) { "A budget max amount must be positive." }
        require(startDateEpochMillis > 0) { "A budget start date is required." }
        require(endDateEpochMillis >= startDateEpochMillis) { "A budget end date cannot be before its start date." }
        require(notificationThresholdPercent in 1..100) { "Threshold percent must be between 1 and 100." }
    }
}

/** Real-time evaluation summary of a budget against user transactions. */
@Serializable
data class BudgetProgress(
    val budget: Budget,
    @SerialName("spent_amount")
    val spentAmount: Money,
    @SerialName("available_amount")
    val availableAmount: Money,
    val percentage: Double,
    @SerialName("daily_average")
    val dailyAverage: Money,
    @SerialName("projected_spent")
    val projectedSpent: Money,
    @SerialName("difference_from_previous_period")
    val differenceFromPreviousPeriod: Money? = null,
    @SerialName("transactions_count")
    val transactionsCount: Int,
    val status: BudgetStatus,
    val alerts: List<BudgetAlert>,
)

fun BudgetPeriodType.defaultDurationMillis(): Long = when (this) {
    BudgetPeriodType.WEEKLY -> 7L * 86_400_000L
    BudgetPeriodType.BIWEEKLY -> 14L * 86_400_000L
    BudgetPeriodType.MONTHLY -> 30L * 86_400_000L
    BudgetPeriodType.CUSTOM -> 30L * 86_400_000L
}

fun Budget.nextRecurringPeriod(): Budget {
    val duration = endDateEpochMillis - startDateEpochMillis
    val newStart = endDateEpochMillis + 1L
    val newEnd = newStart + duration
    return copy(
        id = "${id}-next-${newStart}",
        startDateEpochMillis = newStart,
        endDateEpochMillis = newEnd,
        status = BudgetStatus.ACTIVE,
    )
}
