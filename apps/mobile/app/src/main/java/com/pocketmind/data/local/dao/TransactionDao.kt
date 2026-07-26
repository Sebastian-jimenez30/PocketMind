package com.pocketmind.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.pocketmind.data.local.entity.TransactionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {
    @Query("SELECT * FROM transactions ORDER BY occurredAtEpochMillis DESC") fun observeAll(): Flow<List<TransactionEntity>>
    @Query("SELECT * FROM transactions WHERE id = :id LIMIT 1") suspend fun getById(id: String): TransactionEntity?
    @Upsert suspend fun upsert(transaction: TransactionEntity)
    @Query("DELETE FROM transactions WHERE id = :id") suspend fun deleteById(id: String)
}
