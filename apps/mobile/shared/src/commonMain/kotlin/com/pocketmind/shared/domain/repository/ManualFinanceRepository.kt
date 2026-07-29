package com.pocketmind.shared.domain.repository

import com.pocketmind.shared.domain.model.CreditCardPayment
import com.pocketmind.shared.domain.model.CreditCardProfile
import com.pocketmind.shared.domain.model.InstallmentPurchase
import com.pocketmind.shared.domain.model.FinancialAccount
import com.pocketmind.shared.domain.model.FinancialProductConfiguration
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
    suspend fun saveProduct(
        account: FinancialAccount,
        configuration: FinancialProductConfiguration,
    )
    suspend fun saveInstallmentPurchase(purchase: InstallmentPurchase, ledgerTransaction: FinancialTransaction)
    suspend fun saveCreditCardPayment(
        payment: CreditCardPayment,
        ledgerTransaction: FinancialTransaction,
        sourceSavingsMovement: SavingsMovement?,
    )
    suspend fun saveSavingsMovement(
        movement: SavingsMovement,
        ledgerTransaction: FinancialTransaction?,
        relatedSavingsMovement: SavingsMovement?,
    )
    suspend fun saveLoanPayment(
        payment: LoanPayment,
        ledgerTransaction: FinancialTransaction,
        sourceSavingsMovement: SavingsMovement?,
    )
    /**
     * Removes every deterministic local effect produced by a reversible
     * movement command, including its ledger and product-specific records.
     */
    suspend fun deleteRecordedMovement(commandId: String)
}
