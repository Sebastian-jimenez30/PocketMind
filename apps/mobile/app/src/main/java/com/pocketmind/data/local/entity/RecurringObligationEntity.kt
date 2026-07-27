package com.pocketmind.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(tableName = "recurring_obligations")
data class RecurringObligationEntity(
    @PrimaryKey val id: String,
    val name: String,
    val amountMinorUnits: Long,
    val currency: String,
    val recurrence: String,
    val dueDayOfMonth: Int?,
    val isActive: Boolean,
)
