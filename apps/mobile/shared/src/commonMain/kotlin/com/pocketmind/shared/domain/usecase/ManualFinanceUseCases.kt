package com.pocketmind.shared.domain.usecase

import com.pocketmind.shared.domain.repository.ManualFinanceRepository

/** Read access for product overviews. Writes go through [ExecuteFinancialCommandUseCase]. */
class ManualFinanceUseCases(
    private val repository: ManualFinanceRepository,
) {
    fun observeCreditCardProfiles() = repository.observeCreditCardProfiles()
    fun observeInstallmentPurchases() = repository.observeInstallmentPurchases()
    fun observeCreditCardPayments() = repository.observeCreditCardPayments()
    fun observeSavingsProfiles() = repository.observeSavingsProfiles()
    fun observeSavingsMovements() = repository.observeSavingsMovements()
    fun observeLoanProfiles() = repository.observeLoanProfiles()
    fun observeLoanPayments() = repository.observeLoanPayments()

    suspend fun getCreditCardProfile(accountId: String) = repository.getCreditCardProfile(accountId)
    suspend fun getSavingsProfile(accountId: String) = repository.getSavingsProfile(accountId)
    suspend fun getLoanProfile(accountId: String) = repository.getLoanProfile(accountId)
}
