package com.pocketmind.presentation.budgets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pocketmind.shared.domain.model.Budget
import com.pocketmind.shared.domain.model.BudgetPeriodType
import com.pocketmind.shared.domain.model.BudgetProgress
import com.pocketmind.shared.domain.model.CurrencyCode
import com.pocketmind.shared.domain.model.Money
import com.pocketmind.shared.domain.model.TransactionCategoryId
import com.pocketmind.shared.domain.usecase.CreateBudgetUseCase
import com.pocketmind.shared.domain.usecase.DeleteBudgetUseCase
import com.pocketmind.shared.domain.usecase.ObserveBudgetSummariesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
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
    val error: String? = null,
)

@HiltViewModel
class BudgetsViewModel @Inject constructor(
    private val observeBudgetSummariesUseCase: ObserveBudgetSummariesUseCase,
    private val createBudgetUseCase: CreateBudgetUseCase,
    private val deleteBudgetUseCase: DeleteBudgetUseCase,
) : ViewModel() {

    val uiState: StateFlow<BudgetsUiState> = observeBudgetSummariesUseCase.execute { System.currentTimeMillis() }
        .map { progressList ->
            BudgetsUiState(isLoading = false, progressList = progressList)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = BudgetsUiState(isLoading = true),
        )

    fun createBudget(
        name: String,
        categoryId: TransactionCategoryId,
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

    fun deleteBudget(id: String) {
        viewModelScope.launch {
            deleteBudgetUseCase.execute(id)
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
