package com.pocketmind.shared.domain.usecase

import com.pocketmind.shared.domain.model.Budget
import com.pocketmind.shared.domain.model.BudgetProgress
import com.pocketmind.shared.domain.model.BudgetStatus
import com.pocketmind.shared.domain.model.evaluateBudget
import com.pocketmind.shared.domain.repository.BudgetRepository
import com.pocketmind.shared.domain.repository.TransactionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first

class ObserveBudgetSummariesUseCase(
    private val budgetRepository: BudgetRepository,
    private val transactionRepository: TransactionRepository,
) {
    fun execute(nowEpochMillisProvider: () -> Long): Flow<List<BudgetProgress>> {
        return combine(
            budgetRepository.observeAll(),
            transactionRepository.observeAll(),
        ) { budgets, transactions ->
            val now = nowEpochMillisProvider()
            budgets.map { budget ->
                evaluateBudget(budget, transactions, now)
            }.sortedByDescending { it.percentage }
        }
    }
}

class CreateBudgetUseCase(
    private val budgetRepository: BudgetRepository,
) {
    suspend fun execute(budget: Budget): Result<Unit> {
        val allBudgets = budgetRepository.observeAll().first()
        val overlaps = allBudgets.any { existing ->
            existing.id != budget.id &&
                existing.categoryId == budget.categoryId &&
                existing.status != BudgetStatus.FINISHED &&
                existing.status != BudgetStatus.PAUSED &&
                budget.startDateEpochMillis <= existing.endDateEpochMillis &&
                budget.endDateEpochMillis >= existing.startDateEpochMillis
        }
        if (overlaps) {
            return Result.failure(
                IllegalArgumentException("Ya existe un presupuesto activo para esta categoría en este período.")
            )
        }
        budgetRepository.save(budget)
        return Result.success(Unit)
    }
}

class UpdateBudgetUseCase(
    private val budgetRepository: BudgetRepository,
) {
    suspend fun execute(budget: Budget): Result<Unit> {
        budgetRepository.save(budget)
        return Result.success(Unit)
    }
}

class DeleteBudgetUseCase(
    private val budgetRepository: BudgetRepository,
) {
    suspend fun execute(id: String): Result<Unit> {
        budgetRepository.delete(id)
        return Result.success(Unit)
    }
}
