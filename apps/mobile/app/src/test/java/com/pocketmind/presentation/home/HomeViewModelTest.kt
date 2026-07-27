package com.pocketmind.presentation.home

import com.pocketmind.shared.domain.model.CreditCardPayment
import com.pocketmind.shared.domain.model.CreditCardProfile
import com.pocketmind.shared.domain.model.FinancialAccount
import com.pocketmind.shared.domain.model.FinancialTransaction
import com.pocketmind.shared.domain.model.InstallmentPurchase
import com.pocketmind.shared.domain.model.SavingsMovement
import com.pocketmind.shared.domain.model.SavingsProfile
import com.pocketmind.shared.domain.repository.FinancialAccountRepository
import com.pocketmind.shared.domain.repository.ManualFinanceRepository
import com.pocketmind.shared.domain.repository.TransactionRepository
import com.pocketmind.shared.domain.usecase.ObserveActiveFinancialAccountsUseCase
import com.pocketmind.shared.domain.usecase.ManualFinanceUseCases
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeViewModelTest {
    @Test
    fun `starts in loading while dashboard source has no value`() {
        val accounts = object : FinancialAccountRepository {
            override fun observeActive() = emptyFlow<List<FinancialAccount>>()
            override suspend fun getById(id: String): FinancialAccount? = null
            override suspend fun save(account: FinancialAccount) = Unit
        }
        val transactions = object : TransactionRepository {
            override fun observeAll() = emptyFlow<List<FinancialTransaction>>()
            override suspend fun getById(id: String): FinancialTransaction? = null
            override suspend fun save(transaction: FinancialTransaction) = Unit
            override suspend fun delete(id: String) = Unit
        }
        val manualFinance = object : ManualFinanceRepository {
            override fun observeCreditCardProfiles() = emptyFlow<List<CreditCardProfile>>()
            override fun observeInstallmentPurchases() = emptyFlow<List<InstallmentPurchase>>()
            override fun observeCreditCardPayments() = emptyFlow<List<CreditCardPayment>>()
            override fun observeSavingsProfiles() = emptyFlow<List<SavingsProfile>>()
            override fun observeSavingsMovements() = emptyFlow<List<SavingsMovement>>()
            override suspend fun getCreditCardProfile(accountId: String): CreditCardProfile? = null
            override suspend fun getSavingsProfile(accountId: String): SavingsProfile? = null
            override suspend fun saveCreditCardProfile(profile: CreditCardProfile) = Unit
            override suspend fun saveSavingsProfile(profile: SavingsProfile) = Unit
            override suspend fun saveInstallmentPurchase(
                purchase: InstallmentPurchase,
                ledgerTransaction: FinancialTransaction,
            ) = Unit
            override suspend fun saveCreditCardPayment(
                payment: CreditCardPayment,
                ledgerTransaction: FinancialTransaction,
            ) = Unit
            override suspend fun saveSavingsMovement(
                movement: SavingsMovement,
                ledgerTransaction: FinancialTransaction?,
            ) = Unit
        }

        val viewModel = HomeViewModel(
            ObserveActiveFinancialAccountsUseCase(accounts),
            transactions,
            ManualFinanceUseCases(manualFinance),
        )

        assertEquals(HomeUiState.Loading, viewModel.uiState.value)
    }
}
