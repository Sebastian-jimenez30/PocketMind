package com.pocketmind.shared.domain.repository

import com.pocketmind.shared.domain.model.FinancialSetup
import kotlinx.coroutines.flow.Flow

/** Local-first contract for the user's initial financial picture. */
interface FinancialSetupRepository {
    fun observeIsCompleted(): Flow<Boolean>
    suspend fun saveInitialSetup(setup: FinancialSetup)
}
