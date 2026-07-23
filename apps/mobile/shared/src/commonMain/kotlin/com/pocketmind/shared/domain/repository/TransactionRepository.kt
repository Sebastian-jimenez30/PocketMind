package com.pocketmind.shared.domain.repository

import com.pocketmind.shared.domain.model.FinancialTransaction
import kotlinx.coroutines.flow.Flow

/** Contract implemented by platform persistence and synchronization layers. */
interface TransactionRepository {
    fun observeAll(): Flow<List<FinancialTransaction>>

    suspend fun save(transaction: FinancialTransaction)
}
