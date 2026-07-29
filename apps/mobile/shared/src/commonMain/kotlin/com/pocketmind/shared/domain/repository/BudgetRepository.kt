package com.pocketmind.shared.domain.repository

import com.pocketmind.shared.domain.model.Budget
import kotlinx.coroutines.flow.Flow

/** Contract implemented by platform persistence and synchronization layers for budgets. */
interface BudgetRepository {
    fun observeAll(): Flow<List<Budget>>

    suspend fun getById(id: String): Budget?

    suspend fun save(budget: Budget)

    suspend fun delete(id: String)
}
