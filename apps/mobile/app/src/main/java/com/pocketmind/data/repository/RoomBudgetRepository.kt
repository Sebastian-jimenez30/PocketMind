package com.pocketmind.data.repository

import com.pocketmind.data.local.dao.BudgetDao
import com.pocketmind.data.local.entity.BudgetEntity
import com.pocketmind.shared.domain.model.Budget
import com.pocketmind.shared.domain.model.BudgetPeriodType
import com.pocketmind.shared.domain.model.BudgetStatus
import com.pocketmind.shared.domain.model.CurrencyCode
import com.pocketmind.shared.domain.model.Money
import com.pocketmind.shared.domain.model.TransactionCategoryId
import com.pocketmind.shared.domain.repository.BudgetRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class RoomBudgetRepository @Inject constructor(
    private val budgetDao: BudgetDao,
) : BudgetRepository {
    override fun observeAll(): Flow<List<Budget>> =
        budgetDao.observeAll().map { entities -> entities.map(BudgetEntity::toDomain) }

    override suspend fun getById(id: String): Budget? =
        budgetDao.getById(id)?.toDomain()

    override suspend fun save(budget: Budget) =
        budgetDao.upsert(budget.toEntity())

    override suspend fun delete(id: String) =
        budgetDao.deleteById(id)
}

private fun BudgetEntity.toDomain() = Budget(
    id = id,
    name = name,
    categoryId = categoryId,
    maxAmount = Money(maxAmountMinorUnits, CurrencyCode.valueOf(currency)),
    periodType = BudgetPeriodType.valueOf(periodType),
    startDateEpochMillis = startDateEpochMillis,
    endDateEpochMillis = endDateEpochMillis,
    isRecurring = isRecurring,
    status = BudgetStatus.valueOf(status),
    notificationThresholdPercent = notificationThresholdPercent,
)

private fun Budget.toEntity() = BudgetEntity(
    id = id,
    name = name,
    categoryId = categoryId,
    maxAmountMinorUnits = maxAmount.minorUnits,
    currency = maxAmount.currency.name,
    periodType = periodType.name,
    startDateEpochMillis = startDateEpochMillis,
    endDateEpochMillis = endDateEpochMillis,
    isRecurring = isRecurring,
    status = status.name,
    notificationThresholdPercent = notificationThresholdPercent,
)

