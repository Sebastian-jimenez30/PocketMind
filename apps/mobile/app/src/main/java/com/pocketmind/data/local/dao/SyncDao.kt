package com.pocketmind.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.pocketmind.data.local.entity.AccountEntity
import com.pocketmind.data.local.entity.CreditCardPaymentEntity
import com.pocketmind.data.local.entity.CreditCardProfileEntity
import com.pocketmind.data.local.entity.DebtEntity
import com.pocketmind.data.local.entity.FinancialSetupEntity
import com.pocketmind.data.local.entity.IncomeSourceEntity
import com.pocketmind.data.local.entity.InstallmentPurchaseEntity
import com.pocketmind.data.local.entity.LoanPaymentEntity
import com.pocketmind.data.local.entity.LoanProfileEntity
import com.pocketmind.data.local.entity.RecurringObligationEntity
import com.pocketmind.data.local.entity.SavingsMovementEntity
import com.pocketmind.data.local.entity.SavingsPlanEntity
import com.pocketmind.data.local.entity.SavingsProfileEntity
import com.pocketmind.data.local.entity.SyncControlEntity
import com.pocketmind.data.local.entity.SyncOutboxEntity
import com.pocketmind.data.local.entity.TransactionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SyncDao {
    @Query("SELECT * FROM sync_control WHERE id = 1")
    fun observeControl(): Flow<SyncControlEntity?>

    @Query("SELECT * FROM sync_control WHERE id = 1")
    suspend fun getControl(): SyncControlEntity?

    @Upsert
    suspend fun upsertControl(control: SyncControlEntity)

    @Query("UPDATE sync_control SET isApplyingRemote = :value WHERE id = 1")
    suspend fun setApplyingRemote(value: Boolean)

    @Query("UPDATE sync_control SET isSyncing = :value, lastError = :error WHERE id = 1")
    suspend fun setSyncing(value: Boolean, error: String?)

    @Query(
        "UPDATE sync_control SET isSyncing = 0, isInitialSyncCompleted = 1, " +
            "lastSyncedAtEpochMillis = :syncedAt, lastError = NULL WHERE id = 1",
    )
    suspend fun markSynchronized(syncedAt: Long)

    @Query("SELECT * FROM sync_outbox")
    suspend fun getPendingChanges(): List<SyncOutboxEntity>

    @Query("SELECT COUNT(*) FROM sync_outbox")
    fun observePendingCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM sync_outbox")
    suspend fun pendingCount(): Int

    @Query(
        "DELETE FROM sync_outbox WHERE entityType = :entityType AND entityId = :entityId " +
            "AND queuedAtEpochMillis = :queuedAt",
    )
    suspend fun removePendingChange(entityType: String, entityId: String, queuedAt: Long)

    @Query(
        "UPDATE sync_outbox SET attemptCount = attemptCount + 1, lastError = :error " +
            "WHERE entityType = :entityType AND entityId = :entityId",
    )
    suspend fun markPendingFailure(entityType: String, entityId: String, error: String)

    @Query("DELETE FROM sync_outbox")
    suspend fun clearOutbox()

    @Query(
        "UPDATE sync_control SET userId = :userId, isApplyingRemote = 0, " +
            "isInitialSyncCompleted = :initialSyncCompleted, isSyncing = 0, " +
            "lastSyncedAtEpochMillis = NULL, lastError = NULL WHERE id = 1",
    )
    suspend fun assignUser(userId: String?, initialSyncCompleted: Boolean)

    @Query(
        "SELECT (SELECT COUNT(*) FROM accounts) + (SELECT COUNT(*) FROM transactions) + " +
            "(SELECT COUNT(*) FROM income_sources) + (SELECT COUNT(*) FROM debts) + " +
            "(SELECT COUNT(*) FROM savings_plans) + (SELECT COUNT(*) FROM recurring_obligations) + " +
            "(SELECT COUNT(*) FROM credit_card_profiles) + (SELECT COUNT(*) FROM installment_purchases) + " +
            "(SELECT COUNT(*) FROM credit_card_payments) + (SELECT COUNT(*) FROM savings_profiles) + " +
            "(SELECT COUNT(*) FROM savings_movements) + (SELECT COUNT(*) FROM loan_profiles) + " +
            "(SELECT COUNT(*) FROM loan_payments) + (SELECT COUNT(*) FROM financial_setup)",
    )
    suspend fun localRecordCount(): Int

    @Query(
        "INSERT OR REPLACE INTO sync_outbox " +
            "(entityType, entityId, operation, queuedAtEpochMillis, attemptCount, lastError) " +
            "SELECT 'FINANCIAL_SETUP', CAST(id AS TEXT), 'UPSERT', :queuedAt, 0, NULL FROM financial_setup",
    )
    suspend fun enqueueFinancialSetup(queuedAt: Long)

    @Query(
        "INSERT OR REPLACE INTO sync_outbox " +
            "(entityType, entityId, operation, queuedAtEpochMillis, attemptCount, lastError) " +
            "SELECT 'ACCOUNT', id, 'UPSERT', :queuedAt, 0, NULL FROM accounts",
    )
    suspend fun enqueueAccounts(queuedAt: Long)

    @Query(
        "INSERT OR REPLACE INTO sync_outbox " +
            "(entityType, entityId, operation, queuedAtEpochMillis, attemptCount, lastError) " +
            "SELECT 'TRANSACTION', id, 'UPSERT', :queuedAt, 0, NULL FROM transactions",
    )
    suspend fun enqueueTransactions(queuedAt: Long)

    @Query(
        "INSERT OR REPLACE INTO sync_outbox " +
            "(entityType, entityId, operation, queuedAtEpochMillis, attemptCount, lastError) " +
            "SELECT 'INCOME_SOURCE', id, 'UPSERT', :queuedAt, 0, NULL FROM income_sources",
    )
    suspend fun enqueueIncomeSources(queuedAt: Long)

    @Query(
        "INSERT OR REPLACE INTO sync_outbox " +
            "(entityType, entityId, operation, queuedAtEpochMillis, attemptCount, lastError) " +
            "SELECT 'DEBT', id, 'UPSERT', :queuedAt, 0, NULL FROM debts",
    )
    suspend fun enqueueDebts(queuedAt: Long)

    @Query(
        "INSERT OR REPLACE INTO sync_outbox " +
            "(entityType, entityId, operation, queuedAtEpochMillis, attemptCount, lastError) " +
            "SELECT 'SAVINGS_PLAN', id, 'UPSERT', :queuedAt, 0, NULL FROM savings_plans",
    )
    suspend fun enqueueSavingsPlans(queuedAt: Long)

    @Query(
        "INSERT OR REPLACE INTO sync_outbox " +
            "(entityType, entityId, operation, queuedAtEpochMillis, attemptCount, lastError) " +
            "SELECT 'RECURRING_OBLIGATION', id, 'UPSERT', :queuedAt, 0, NULL FROM recurring_obligations",
    )
    suspend fun enqueueRecurringObligations(queuedAt: Long)

    @Query(
        "INSERT OR REPLACE INTO sync_outbox " +
            "(entityType, entityId, operation, queuedAtEpochMillis, attemptCount, lastError) " +
            "SELECT 'CREDIT_CARD_PROFILE', accountId, 'UPSERT', :queuedAt, 0, NULL FROM credit_card_profiles",
    )
    suspend fun enqueueCreditCardProfiles(queuedAt: Long)

    @Query(
        "INSERT OR REPLACE INTO sync_outbox " +
            "(entityType, entityId, operation, queuedAtEpochMillis, attemptCount, lastError) " +
            "SELECT 'INSTALLMENT_PURCHASE', id, 'UPSERT', :queuedAt, 0, NULL FROM installment_purchases",
    )
    suspend fun enqueueInstallmentPurchases(queuedAt: Long)

    @Query(
        "INSERT OR REPLACE INTO sync_outbox " +
            "(entityType, entityId, operation, queuedAtEpochMillis, attemptCount, lastError) " +
            "SELECT 'CREDIT_CARD_PAYMENT', id, 'UPSERT', :queuedAt, 0, NULL FROM credit_card_payments",
    )
    suspend fun enqueueCreditCardPayments(queuedAt: Long)

    @Query(
        "INSERT OR REPLACE INTO sync_outbox " +
            "(entityType, entityId, operation, queuedAtEpochMillis, attemptCount, lastError) " +
            "SELECT 'SAVINGS_PROFILE', accountId, 'UPSERT', :queuedAt, 0, NULL FROM savings_profiles",
    )
    suspend fun enqueueSavingsProfiles(queuedAt: Long)

    @Query(
        "INSERT OR REPLACE INTO sync_outbox " +
            "(entityType, entityId, operation, queuedAtEpochMillis, attemptCount, lastError) " +
            "SELECT 'SAVINGS_MOVEMENT', id, 'UPSERT', :queuedAt, 0, NULL FROM savings_movements",
    )
    suspend fun enqueueSavingsMovements(queuedAt: Long)

    @Query(
        "INSERT OR REPLACE INTO sync_outbox " +
            "(entityType, entityId, operation, queuedAtEpochMillis, attemptCount, lastError) " +
            "SELECT 'LOAN_PROFILE', accountId, 'UPSERT', :queuedAt, 0, NULL FROM loan_profiles",
    )
    suspend fun enqueueLoanProfiles(queuedAt: Long)

    @Query(
        "INSERT OR REPLACE INTO sync_outbox " +
            "(entityType, entityId, operation, queuedAtEpochMillis, attemptCount, lastError) " +
            "SELECT 'LOAN_PAYMENT', id, 'UPSERT', :queuedAt, 0, NULL FROM loan_payments",
    )
    suspend fun enqueueLoanPayments(queuedAt: Long)

    @Query("SELECT * FROM financial_setup WHERE id = 1")
    suspend fun getFinancialSetup(): FinancialSetupEntity?
    @Query("SELECT * FROM accounts WHERE id = :id") suspend fun getAccount(id: String): AccountEntity?
    @Query("SELECT * FROM transactions WHERE id = :id") suspend fun getTransaction(id: String): TransactionEntity?
    @Query("SELECT * FROM income_sources WHERE id = :id") suspend fun getIncomeSource(id: String): IncomeSourceEntity?
    @Query("SELECT * FROM debts WHERE id = :id") suspend fun getDebt(id: String): DebtEntity?
    @Query("SELECT * FROM savings_plans WHERE id = :id") suspend fun getSavingsPlan(id: String): SavingsPlanEntity?
    @Query("SELECT * FROM recurring_obligations WHERE id = :id") suspend fun getRecurringObligation(id: String): RecurringObligationEntity?
    @Query("SELECT * FROM credit_card_profiles WHERE accountId = :id") suspend fun getCreditCardProfile(id: String): CreditCardProfileEntity?
    @Query("SELECT * FROM installment_purchases WHERE id = :id") suspend fun getInstallmentPurchase(id: String): InstallmentPurchaseEntity?
    @Query("SELECT * FROM credit_card_payments WHERE id = :id") suspend fun getCreditCardPayment(id: String): CreditCardPaymentEntity?
    @Query("SELECT * FROM savings_profiles WHERE accountId = :id") suspend fun getSavingsProfile(id: String): SavingsProfileEntity?
    @Query("SELECT * FROM savings_movements WHERE id = :id") suspend fun getSavingsMovement(id: String): SavingsMovementEntity?
    @Query("SELECT * FROM loan_profiles WHERE accountId = :id") suspend fun getLoanProfile(id: String): LoanProfileEntity?
    @Query("SELECT * FROM loan_payments WHERE id = :id") suspend fun getLoanPayment(id: String): LoanPaymentEntity?

    @Upsert suspend fun upsertFinancialSetup(item: FinancialSetupEntity)
    @Upsert suspend fun upsertAccounts(items: List<AccountEntity>)
    @Upsert suspend fun upsertTransactions(items: List<TransactionEntity>)
    @Upsert suspend fun upsertIncomeSources(items: List<IncomeSourceEntity>)
    @Upsert suspend fun upsertDebts(items: List<DebtEntity>)
    @Upsert suspend fun upsertSavingsPlans(items: List<SavingsPlanEntity>)
    @Upsert suspend fun upsertRecurringObligations(items: List<RecurringObligationEntity>)
    @Upsert suspend fun upsertCreditCardProfiles(items: List<CreditCardProfileEntity>)
    @Upsert suspend fun upsertInstallmentPurchases(items: List<InstallmentPurchaseEntity>)
    @Upsert suspend fun upsertCreditCardPayments(items: List<CreditCardPaymentEntity>)
    @Upsert suspend fun upsertSavingsProfiles(items: List<SavingsProfileEntity>)
    @Upsert suspend fun upsertSavingsMovements(items: List<SavingsMovementEntity>)
    @Upsert suspend fun upsertLoanProfiles(items: List<LoanProfileEntity>)
    @Upsert suspend fun upsertLoanPayments(items: List<LoanPaymentEntity>)

    @Query("DELETE FROM loan_payments") suspend fun clearLoanPayments()
    @Query("DELETE FROM savings_movements") suspend fun clearSavingsMovements()
    @Query("DELETE FROM credit_card_payments") suspend fun clearCreditCardPayments()
    @Query("DELETE FROM installment_purchases") suspend fun clearInstallmentPurchases()
    @Query("DELETE FROM transactions") suspend fun clearTransactions()
    @Query("DELETE FROM loan_profiles") suspend fun clearLoanProfiles()
    @Query("DELETE FROM savings_profiles") suspend fun clearSavingsProfiles()
    @Query("DELETE FROM credit_card_profiles") suspend fun clearCreditCardProfiles()
    @Query("DELETE FROM recurring_obligations") suspend fun clearRecurringObligations()
    @Query("DELETE FROM savings_plans") suspend fun clearSavingsPlans()
    @Query("DELETE FROM debts") suspend fun clearDebts()
    @Query("DELETE FROM income_sources") suspend fun clearIncomeSources()
    @Query("DELETE FROM financial_setup") suspend fun clearFinancialSetup()
    @Query("DELETE FROM accounts") suspend fun clearAccounts()
}
