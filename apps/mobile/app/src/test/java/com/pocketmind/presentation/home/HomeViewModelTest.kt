package com.pocketmind.presentation.home

import com.pocketmind.shared.domain.model.DashboardSummary
import com.pocketmind.shared.domain.model.FinancialAccount
import com.pocketmind.shared.domain.model.FinancialTransaction
import com.pocketmind.shared.domain.repository.DashboardRepository
import com.pocketmind.shared.domain.repository.FinancialAccountRepository
import com.pocketmind.shared.domain.repository.TransactionRepository
import com.pocketmind.shared.domain.usecase.ObserveActiveFinancialAccountsUseCase
import com.pocketmind.shared.domain.usecase.ObserveDashboardSummaryUseCase
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeViewModelTest {
    @Test
    fun `starts in loading while dashboard source has no value`() {
        val repository = object : DashboardRepository {
            override fun observeSummary() = emptyFlow<DashboardSummary>()
        }

        val accounts = object : FinancialAccountRepository {
            override fun observeActive() = emptyFlow<List<FinancialAccount>>()
            override suspend fun getById(id: String): FinancialAccount? = null
            override suspend fun save(account: FinancialAccount) = Unit
        }
        val transactions = object : TransactionRepository {
            override fun observeAll() = emptyFlow<List<FinancialTransaction>>()
            override suspend fun getById(id: String): FinancialTransaction? = null
            override suspend fun save(transaction: FinancialTransaction) = Unit
        }

        val viewModel = HomeViewModel(
            ObserveDashboardSummaryUseCase(repository),
            ObserveActiveFinancialAccountsUseCase(accounts),
            transactions,
        )

        assertEquals(HomeUiState.Loading, viewModel.uiState.value)
    }
}
