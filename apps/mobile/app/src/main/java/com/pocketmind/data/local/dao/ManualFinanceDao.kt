package com.pocketmind.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.pocketmind.data.local.entity.CreditCardPaymentEntity
import com.pocketmind.data.local.entity.CreditCardProfileEntity
import com.pocketmind.data.local.entity.InstallmentPurchaseEntity
import com.pocketmind.data.local.entity.SavingsMovementEntity
import com.pocketmind.data.local.entity.SavingsProfileEntity
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

    @Query("SELECT * FROM credit_card_profiles WHERE accountId = :accountId LIMIT 1")
    suspend fun getCreditCardProfile(accountId: String): CreditCardProfileEntity?

    @Query("SELECT * FROM savings_profiles WHERE accountId = :accountId LIMIT 1")
    suspend fun getSavingsProfile(accountId: String): SavingsProfileEntity?

    @Upsert suspend fun upsertCreditCardProfile(profile: CreditCardProfileEntity)
    @Upsert suspend fun upsertInstallmentPurchase(purchase: InstallmentPurchaseEntity)
    @Upsert suspend fun upsertCreditCardPayment(payment: CreditCardPaymentEntity)
    @Upsert suspend fun upsertSavingsProfile(profile: SavingsProfileEntity)
    @Upsert suspend fun upsertSavingsMovement(movement: SavingsMovementEntity)
}
