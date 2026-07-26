package com.pocketmind.shared.domain.repository

import com.pocketmind.shared.domain.model.FinancialAccount
import kotlinx.coroutines.flow.Flow

/** Local-first access to accounts and cash containers. */
interface FinancialAccountRepository {
    fun observeActive(): Flow<List<FinancialAccount>>
    suspend fun getById(id: String): FinancialAccount?
    suspend fun save(account: FinancialAccount)
}
