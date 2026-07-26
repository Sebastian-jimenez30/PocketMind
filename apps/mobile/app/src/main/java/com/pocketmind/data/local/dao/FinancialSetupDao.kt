package com.pocketmind.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.pocketmind.data.local.entity.DebtEntity
import com.pocketmind.data.local.entity.FinancialSetupEntity
import com.pocketmind.data.local.entity.IncomeSourceEntity
import com.pocketmind.data.local.entity.RecurringObligationEntity
import com.pocketmind.data.local.entity.SavingsPlanEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FinancialSetupDao {
    @Query("SELECT EXISTS(SELECT 1 FROM financial_setup WHERE id = 1)")
    fun observeIsCompleted(): Flow<Boolean>

    @Upsert suspend fun upsertSetup(setup: FinancialSetupEntity)
    @Upsert suspend fun upsertIncomeSources(items: List<IncomeSourceEntity>)
    @Upsert suspend fun upsertDebts(items: List<DebtEntity>)
    @Upsert suspend fun upsertSavingsPlans(items: List<SavingsPlanEntity>)
    @Upsert suspend fun upsertRecurringObligations(items: List<RecurringObligationEntity>)
}
