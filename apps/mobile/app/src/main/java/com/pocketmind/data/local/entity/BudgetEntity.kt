package com.pocketmind.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(
    tableName = "budgets",
    indices = [
        Index("categoryId"),
        Index("startDateEpochMillis"),
    ],
)
data class BudgetEntity(
    @PrimaryKey val id: String,
    val name: String,
    val categoryId: String,
    val maxAmountMinorUnits: Long,
    val currency: String,
    val periodType: String,
    val startDateEpochMillis: Long,
    val endDateEpochMillis: Long,
    val isRecurring: Boolean,
    val status: String,
    val notificationThresholdPercent: Int,
)
