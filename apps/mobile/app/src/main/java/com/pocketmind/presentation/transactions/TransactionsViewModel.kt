package com.pocketmind.presentation.transactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pocketmind.shared.domain.model.FinancialTransaction
import com.pocketmind.shared.domain.usecase.ObserveActiveFinancialAccountsUseCase
import com.pocketmind.shared.domain.repository.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class TransactionsUiState(
    val items: List<TransactionListItem> = emptyList(),
    val accounts: List<com.pocketmind.shared.domain.model.FinancialAccount> = emptyList(),
    val customCategories: List<com.pocketmind.shared.domain.model.CustomCategory> = emptyList(),
    val isLoading: Boolean = true,
)

data class TransactionListItem(
    val transaction: FinancialTransaction,
    val accountName: String,
    val destinationAccountName: String = "",
)

@HiltViewModel
class TransactionsViewModel @Inject constructor(
    transactionRepository: TransactionRepository,
    observeAccounts: ObserveActiveFinancialAccountsUseCase,
    observeCustomCategories: com.pocketmind.shared.domain.usecase.ObserveCustomCategoriesUseCase,
) : ViewModel() {
    val uiState: StateFlow<TransactionsUiState> = combine(
        transactionRepository.observeAll(),
        observeAccounts(),
        observeCustomCategories(),
    ) { transactions, accounts, customCategories ->
        val accountNames = accounts.associate { it.id to it.name }
        TransactionsUiState(
            items = transactions.map { transaction ->
                TransactionListItem(
                    transaction,
                    accountNames[transaction.accountId].orEmpty(),
                    transaction.relatedAccountId?.let(accountNames::get).orEmpty(),
                )
            },
            accounts = accounts,
            customCategories = customCategories,
            isLoading = false,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = TransactionsUiState(),
    )
}

