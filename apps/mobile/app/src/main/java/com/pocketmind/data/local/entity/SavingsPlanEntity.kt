package com.pocketmind.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(tableName = "savings_plans")
data class SavingsPlanEntity(
    @PrimaryKey val id: String,
    val name: String,
    val type: String,
    val currentAmountMinorUnits: Long,
    val targetAmountMinorUnits: Long?,
    val monthlyContributionMinorUnits: Long?,
    val currency: String,
    val annualYieldBasisPoints: Int?,
    val targetDateEpochMillis: Long?,
    val isActive: Boolean,
)
