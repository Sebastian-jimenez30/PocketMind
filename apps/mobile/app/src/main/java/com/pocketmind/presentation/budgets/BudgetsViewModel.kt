package com.pocketmind.presentation.budgets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pocketmind.shared.domain.model.Budget
import com.pocketmind.shared.domain.model.BudgetPeriodType
import com.pocketmind.shared.domain.model.BudgetProgress
import com.pocketmind.shared.domain.model.CurrencyCode
import com.pocketmind.shared.domain.model.CustomCategory
import com.pocketmind.shared.domain.model.Money
import com.pocketmind.shared.domain.model.TransactionCategoryId
import com.pocketmind.shared.domain.model.FinancialTransaction
import com.pocketmind.shared.domain.repository.TransactionRepository
import com.pocketmind.shared.domain.usecase.CreateBudgetUseCase
import com.pocketmind.shared.domain.usecase.DeleteBudgetUseCase
import com.pocketmind.shared.domain.usecase.DeleteCustomCategoryUseCase
import com.pocketmind.shared.domain.usecase.ObserveBudgetSummariesUseCase
import com.pocketmind.shared.domain.usecase.ObserveCustomCategoriesUseCase
import com.pocketmind.shared.domain.usecase.SaveCustomCategoryUseCase
import com.pocketmind.shared.domain.usecase.UpdateBudgetUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import javax.inject.Inject

data class BudgetsUiState(
    val isLoading: Boolean = true,
    val progressList: List<BudgetProgress> = emptyList(),
    val customCategories: List<CustomCategory> = emptyList(),
    val transactions: List<FinancialTransaction> = emptyList(),
    val error: String? = null,
)

@HiltViewModel
class BudgetsViewModel @Inject constructor(
    private val observeBudgetSummariesUseCase: ObserveBudgetSummariesUseCase,
    private val observeCustomCategoriesUseCase: ObserveCustomCategoriesUseCase,
    private val saveCustomCategoryUseCase: SaveCustomCategoryUseCase,
    private val deleteCustomCategoryUseCase: DeleteCustomCategoryUseCase,
    private val createBudgetUseCase: CreateBudgetUseCase,
    private val updateBudgetUseCase: UpdateBudgetUseCase,
    private val deleteBudgetUseCase: DeleteBudgetUseCase,
    private val transactionRepository: TransactionRepository,
) : ViewModel() {

    val uiState: StateFlow<BudgetsUiState> = combine(
        observeBudgetSummariesUseCase.execute { System.currentTimeMillis() },
        observeCustomCategoriesUseCase(),
        transactionRepository.observeAll(),
    ) { progressList, customCategories, transactions ->
        BudgetsUiState(
            isLoading = false,
            progressList = progressList,
            customCategories = customCategories,
            transactions = transactions,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = BudgetsUiState(isLoading = true),
    )



    fun createBudget(
        name: String,
        categoryId: String,
        maxAmountMinorUnits: Long,
        currency: CurrencyCode,
        periodType: BudgetPeriodType,
        isRecurring: Boolean = true,
        notificationThresholdPercent: Int = 80,
    ) {
        viewModelScope.launch {
            val (startDate, endDate) = calculatePeriodBounds(periodType)
            val budget = Budget(
                id = UUID.randomUUID().toString(),
                name = name.trim(),
                categoryId = categoryId,
                maxAmount = Money(maxAmountMinorUnits, currency),
                periodType = periodType,
                startDateEpochMillis = startDate,
                endDateEpochMillis = endDate,
                isRecurring = isRecurring,
                notificationThresholdPercent = notificationThresholdPercent,
            )
            createBudgetUseCase.execute(budget)
        }
    }

    fun updateBudget(
        id: String,
        name: String,
        categoryId: String,
        maxAmountMinorUnits: Long,
        currency: CurrencyCode,
        periodType: BudgetPeriodType,
        isRecurring: Boolean = true,
        notificationThresholdPercent: Int = 80,
    ) {
        viewModelScope.launch {
            val (startDate, endDate) = calculatePeriodBounds(periodType)
            val budget = Budget(
                id = id,
                name = name.trim(),
                categoryId = categoryId,
                maxAmount = Money(maxAmountMinorUnits, currency),
                periodType = periodType,
                startDateEpochMillis = startDate,
                endDateEpochMillis = endDate,
                isRecurring = isRecurring,
                notificationThresholdPercent = notificationThresholdPercent,
            )
            updateBudgetUseCase.execute(budget)
        }
    }

    fun deleteBudget(id: String) {
        viewModelScope.launch {
            deleteBudgetUseCase.execute(id)
        }
    }

    fun createCustomCategory(name: String, onCreated: (String) -> Unit = {}) {
        viewModelScope.launch {
            val cat = CustomCategory(id = UUID.randomUUID().toString(), name = name.trim(), createdAtEpochMillis = System.currentTimeMillis())
            saveCustomCategoryUseCase(cat)
            onCreated(cat.id)
        }
    }

    fun updateCustomCategory(id: String, newName: String) {
        viewModelScope.launch {
            val cat = CustomCategory(id = id, name = newName.trim(), createdAtEpochMillis = System.currentTimeMillis())
            saveCustomCategoryUseCase(cat)
        }
    }

    fun deleteCustomCategory(id: String) {
        viewModelScope.launch {
            deleteCustomCategoryUseCase(id)
        }
    }


    private fun calculatePeriodBounds(periodType: BudgetPeriodType): Pair<Long, Long> {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val (start, end) = when (periodType) {
            BudgetPeriodType.WEEKLY -> {
                val monday = today.with(DayOfWeek.MONDAY)
                val sunday = monday.plusDays(6)
                monday to sunday
            }
            BudgetPeriodType.BIWEEKLY -> {
                val startDay = if (today.dayOfMonth <= 15) today.withDayOfMonth(1) else today.withDayOfMonth(16)
                val endDay = if (today.dayOfMonth <= 15) today.withDayOfMonth(15) else today.withDayOfMonth(today.lengthOfMonth())
                startDay to endDay
            }
            BudgetPeriodType.MONTHLY, BudgetPeriodType.CUSTOM -> {
                val startDay = today.withDayOfMonth(1)
                val endDay = today.withDayOfMonth(today.lengthOfMonth())
                startDay to endDay
            }
        }
        val startEpochMillis = start.atStartOfDay(zone).toInstant().toEpochMilli()
        val endEpochMillis = end.atTime(23, 59, 59, 999_000_000).atZone(zone).toInstant().toEpochMilli()
        return startEpochMillis to endEpochMillis
    }
}
