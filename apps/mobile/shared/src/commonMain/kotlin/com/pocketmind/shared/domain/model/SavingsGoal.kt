package com.pocketmind.shared.domain.model

/** A savings objective whose progress may be calculated from one or more accounts later. */
data class SavingsGoal(
    val id: String,
    val name: String,
    val targetAmount: Money,
    val savedAmount: Money = Money(0, targetAmount.currency),
    val targetDateEpochMillis: Long? = null,
) {
    init {
        require(id.isNotBlank()) { "A savings goal id is required." }
        require(name.isNotBlank()) { "A savings goal name is required." }
        require(targetAmount.isPositive) { "A savings goal must have a positive target." }
        require(savedAmount.currency == targetAmount.currency) { "Savings goal currencies must match." }
        require(targetDateEpochMillis == null || targetDateEpochMillis > 0) { "Target date must be valid." }
    }
}
