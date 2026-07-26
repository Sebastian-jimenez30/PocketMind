package com.pocketmind.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "debts")
data class DebtEntity(
    @PrimaryKey val id: String,
    val name: String,
    val outstandingBalanceMinorUnits: Long,
    val currency: String,
    val interestRateAnnualBasisPoints: Int?,
    val installmentAmountMinorUnits: Long?,
    val dueDayOfMonth: Int?,
    val nextDueAtEpochMillis: Long?,
    val isActive: Boolean,
)
