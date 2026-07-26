package com.pocketmind.shared.domain.usecase

import com.pocketmind.shared.domain.model.CreditCardPayment
import com.pocketmind.shared.domain.model.CreditCardProfile
import com.pocketmind.shared.domain.model.FinancialTransaction
import com.pocketmind.shared.domain.model.InstallmentPurchase
import com.pocketmind.shared.domain.model.SavingsMovement
import com.pocketmind.shared.domain.model.SavingsProfile
import com.pocketmind.shared.domain.repository.ManualFinanceRepository

/** Domain entry point for manual cards and savings. Platform UI never depends on Room. */
class ManualFinanceUseCases(
    private val repository: ManualFinanceRepository,
) {
    fun observeCreditCardProfiles() = repository.observeCreditCardProfiles()
    fun observeInstallmentPurchases() = repository.observeInstallmentPurchases()
    fun observeCreditCardPayments() = repository.observeCreditCardPayments()
    fun observeSavingsProfiles() = repository.observeSavingsProfiles()
    fun observeSavingsMovements() = repository.observeSavingsMovements()

    suspend fun getCreditCardProfile(accountId: String) = repository.getCreditCardProfile(accountId)
    suspend fun getSavingsProfile(accountId: String) = repository.getSavingsProfile(accountId)
    suspend fun saveCreditCardProfile(profile: CreditCardProfile) = repository.saveCreditCardProfile(profile)
    suspend fun saveSavingsProfile(profile: SavingsProfile) = repository.saveSavingsProfile(profile)

    suspend fun recordPurchase(purchase: InstallmentPurchase, ledgerTransaction: FinancialTransaction) =
        repository.saveInstallmentPurchase(purchase, ledgerTransaction)

    suspend fun recordCardPayment(payment: CreditCardPayment, ledgerTransaction: FinancialTransaction) =
        repository.saveCreditCardPayment(payment, ledgerTransaction)

    suspend fun recordSavingsMovement(movement: SavingsMovement, ledgerTransaction: FinancialTransaction?) =
        repository.saveSavingsMovement(movement, ledgerTransaction)
}
