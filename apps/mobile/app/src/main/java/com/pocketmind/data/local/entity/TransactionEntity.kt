package com.pocketmind.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "transactions", foreignKeys = [ForeignKey(entity = AccountEntity::class, parentColumns = ["id"], childColumns = ["accountId"], onDelete = ForeignKey.RESTRICT)], indices = [Index("accountId"), Index("occurredAtEpochMillis")])
data class TransactionEntity(
    @PrimaryKey val id: String,
    val accountId: String,
    val type: String,
    val amountMinorUnits: Long,
    val currency: String,
    val occurredAtEpochMillis: Long,
    val categoryId: String?, val merchant: String?, val note: String?, val source: String, val status: String,
)
