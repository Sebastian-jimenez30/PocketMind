package com.pocketmind.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(tableName = "income_sources")
data class IncomeSourceEntity(
    @PrimaryKey val id: String,
    val name: String,
    val expectedAmountMinorUnits: Long,
    val currency: String,
    val recurrence: String,
    val nextExpectedAtEpochMillis: Long?,
    val isActive: Boolean,
)
