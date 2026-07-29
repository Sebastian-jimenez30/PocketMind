package com.pocketmind.presentation.analysis

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pocketmind.shared.domain.model.CurrencyCode
import com.pocketmind.shared.domain.model.Money
import com.pocketmind.shared.domain.model.TransactionCategoryId
import com.pocketmind.shared.domain.model.TransactionType
import com.pocketmind.shared.domain.repository.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn


data class CategoryAmount(val categoryId: String, val amount: Money)

data class AnalysisUiState(
    val income: Money = Money(0, CurrencyCode.COP),
    val expense: Money = Money(0, CurrencyCode.COP),
    val categories: List<CategoryAmount> = emptyList(),
    val customCategories: List<com.pocketmind.shared.domain.model.CustomCategory> = emptyList(),
    val isLoading: Boolean = true,
)

@HiltViewModel
class AnalysisViewModel @Inject constructor(
    transactionRepository: TransactionRepository,
    observeCustomCategories: com.pocketmind.shared.domain.usecase.ObserveCustomCategoriesUseCase,
) : ViewModel() {
    val uiState: StateFlow<AnalysisUiState> = combine(
        transactionRepository.observeAll(),
        observeCustomCategories(),
    ) { transactions, customCategories ->
        val start = LocalDate.now().withDayOfMonth(1)
            .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val month = transactions.filter { it.occurredAtEpochMillis >= start }
        val income = month.filter { it.type == TransactionType.INCOME }.sumOf { it.amount.minorUnits }
        val expenses = month.filter { it.type == TransactionType.EXPENSE }
        AnalysisUiState(
            income = Money(income, CurrencyCode.COP),
            expense = Money(expenses.sumOf { it.amount.minorUnits }, CurrencyCode.COP),
            categories = expenses.groupBy {
                it.categoryId ?: TransactionCategoryId.OTHER.name
            }.map { (categoryId, items) ->
                CategoryAmount(categoryId, Money(items.sumOf { it.amount.minorUnits }, CurrencyCode.COP))
            }.sortedByDescending { it.amount.minorUnits },
            customCategories = customCategories,
            isLoading = false,
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        AnalysisUiState(),
    )
}

