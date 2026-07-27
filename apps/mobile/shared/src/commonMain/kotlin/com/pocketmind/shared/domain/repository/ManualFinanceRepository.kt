package com.pocketmind.shared.domain.repository

import com.pocketmind.shared.domain.model.CreditCardPayment
import com.pocketmind.shared.domain.model.CreditCardProfile
import com.pocketmind.shared.domain.model.InstallmentPurchase
import com.pocketmind.shared.domain.model.FinancialTransaction
import com.pocketmind.shared.domain.model.LoanPayment
import com.pocketmind.shared.domain.model.LoanProfile
import com.pocketmind.shared.domain.model.SavingsMovement
import com.pocketmind.shared.domain.model.SavingsProfile
import kotlinx.coroutines.flow.Flow

interface ManualFinanceRepository {
    fun observeCreditCardProfiles(): Flow<List<CreditCardProfile>>
    fun observeInstallmentPurchases(): Flow<List<InstallmentPurchase>>
    fun observeCreditCardPayments(): Flow<List<CreditCardPayment>>
    fun observeSavingsProfiles(): Flow<List<SavingsProfile>>
    fun observeSavingsMovements(): Flow<List<SavingsMovement>>
    fun observeLoanProfiles(): Flow<List<LoanProfile>>
    fun observeLoanPayments(): Flow<List<LoanPayment>>

    suspend fun getCreditCardProfile(accountId: String): CreditCardProfile?
    suspend fun getSavingsProfile(accountId: String): SavingsProfile?
    suspend fun getLoanProfile(accountId: String): LoanProfile?
    suspend fun saveCreditCardProfile(profile: CreditCardProfile)
    suspend fun saveSavingsProfile(profile: SavingsProfile)
    suspend fun saveLoanProfile(profile: LoanProfile)
    suspend fun saveInstallmentPurchase(purchase: InstallmentPurchase, ledgerTransaction: FinancialTransaction)
    suspend fun saveCreditCardPayment(payment: CreditCardPayment, ledgerTransaction: FinancialTransaction)
    suspend fun saveSavingsMovement(movement: SavingsMovement, ledgerTransaction: FinancialTransaction?)
    suspend fun saveLoanPayment(payment: LoanPayment, ledgerTransaction: FinancialTransaction)
}
