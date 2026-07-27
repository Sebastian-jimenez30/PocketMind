package com.pocketmind.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/** A single local marker. The server sync layer will scope this data by user later. */
@Serializable
@Entity(tableName = "financial_setup")
data class FinancialSetupEntity(
    @PrimaryKey val id: Int = SINGLETON_ID,
    val completedAtEpochMillis: Long,
) {
    companion object {
        const val SINGLETON_ID = 1
    }
}
