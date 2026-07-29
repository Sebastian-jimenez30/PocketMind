package com.pocketmind.presentation.savings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pocketmind.presentation.accounts.ProductListItem
import com.pocketmind.shared.domain.model.FinancialAccount
import com.pocketmind.shared.domain.model.FinancialAccountType
import com.pocketmind.shared.domain.model.Money
import com.pocketmind.shared.domain.model.calculateSavingsProjection
import com.pocketmind.shared.domain.usecase.ManualFinanceUseCases
import com.pocketmind.shared.domain.usecase.ObserveActiveFinancialAccountsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class SavingsViewModel @Inject constructor(
    observeAccounts: ObserveActiveFinancialAccountsUseCase,
    manualFinance: ManualFinanceUseCases,
) : ViewModel() {
    val savingsAccounts: StateFlow<List<ProductListItem>> = combine(
        observeAccounts(),
        manualFinance.observeSavingsProfiles(),
        manualFinance.observeSavingsMovements(),
    ) { accounts, savings, savingsMovements ->
        accounts
            .filter { it.type == FinancialAccountType.SAVINGS }
            .map { account ->
                val amount = savings.firstOrNull { it.accountId == account.id }?.let { profile ->
                    calculateSavingsProjection(
                        profile,
                        account.openingBalance,
                        savingsMovements,
                        System.currentTimeMillis(),
                    ).currentBalance
                } ?: account.openingBalance
                ProductListItem(account, amount)
            }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}
