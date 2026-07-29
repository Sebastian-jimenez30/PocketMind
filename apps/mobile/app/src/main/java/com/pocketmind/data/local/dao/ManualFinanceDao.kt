package com.pocketmind.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.pocketmind.data.local.entity.CreditCardPaymentEntity
import com.pocketmind.data.local.entity.CreditCardProfileEntity
import com.pocketmind.data.local.entity.InstallmentPurchaseEntity
import com.pocketmind.data.local.entity.SavingsMovementEntity
import com.pocketmind.data.local.entity.SavingsProfileEntity
import com.pocketmind.data.local.entity.LoanPaymentEntity
import com.pocketmind.data.local.entity.LoanProfileEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ManualFinanceDao {
    @Query("SELECT * FROM credit_card_profiles")
    fun observeCreditCardProfiles(): Flow<List<CreditCardProfileEntity>>

    @Query("SELECT * FROM installment_purchases ORDER BY purchasedAtEpochMillis DESC")
    fun observeInstallmentPurchases(): Flow<List<InstallmentPurchaseEntity>>

    @Query("SELECT * FROM credit_card_payments ORDER BY paidAtEpochMillis DESC")
    fun observeCreditCardPayments(): Flow<List<CreditCardPaymentEntity>>

    @Query("SELECT * FROM savings_profiles")
    fun observeSavingsProfiles(): Flow<List<SavingsProfileEntity>>

    @Query("SELECT * FROM savings_movements ORDER BY occurredAtEpochMillis DESC")
    fun observeSavingsMovements(): Flow<List<SavingsMovementEntity>>

    @Query("SELECT * FROM loan_profiles")
    fun observeLoanProfiles(): Flow<List<LoanProfileEntity>>

    @Query("SELECT * FROM loan_payments ORDER BY paidAtEpochMillis DESC")
    fun observeLoanPayments(): Flow<List<LoanPaymentEntity>>

    @Query("SELECT * FROM credit_card_profiles WHERE accountId = :accountId LIMIT 1")
    suspend fun getCreditCardProfile(accountId: String): CreditCardProfileEntity?

    @Query("SELECT * FROM savings_profiles WHERE accountId = :accountId LIMIT 1")
    suspend fun getSavingsProfile(accountId: String): SavingsProfileEntity?

    @Query("SELECT * FROM loan_profiles WHERE accountId = :accountId LIMIT 1")
    suspend fun getLoanProfile(accountId: String): LoanProfileEntity?

    @Upsert suspend fun upsertCreditCardProfile(profile: CreditCardProfileEntity)
    @Upsert suspend fun upsertInstallmentPurchase(purchase: InstallmentPurchaseEntity)
    @Upsert suspend fun upsertCreditCardPayment(payment: CreditCardPaymentEntity)
    @Upsert suspend fun upsertSavingsProfile(profile: SavingsProfileEntity)
    @Upsert suspend fun upsertSavingsMovement(movement: SavingsMovementEntity)
    @Upsert suspend fun upsertLoanProfile(profile: LoanProfileEntity)
    @Upsert suspend fun upsertLoanPayment(payment: LoanPaymentEntity)

    @Query("DELETE FROM credit_card_profiles WHERE accountId = :accountId")
    suspend fun deleteCreditCardProfile(accountId: String)

    @Query("DELETE FROM savings_profiles WHERE accountId = :accountId")
    suspend fun deleteSavingsProfile(accountId: String)

    @Query("DELETE FROM loan_profiles WHERE accountId = :accountId")
    suspend fun deleteLoanProfile(accountId: String)

    @Query("DELETE FROM installment_purchases WHERE id = :id")
    suspend fun deleteInstallmentPurchase(id: String)

    @Query("DELETE FROM savings_movements WHERE id IN (:ids)")
    suspend fun deleteSavingsMovementEffects(ids: Set<String>)

    @Query("DELETE FROM credit_card_payments WHERE id = :id")
    suspend fun deleteCreditCardPayment(id: String)

    @Query("DELETE FROM loan_payments WHERE id = :id")
    suspend fun deleteLoanPayment(id: String)
}
