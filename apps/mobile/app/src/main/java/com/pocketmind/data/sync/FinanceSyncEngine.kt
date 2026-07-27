package com.pocketmind.data.sync

import androidx.room.withTransaction
import com.pocketmind.data.local.PocketMindDatabase
import com.pocketmind.data.local.dao.SyncDao
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
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement

@Singleton
class FinanceSyncEngine @Inject constructor(
    private val database: PocketMindDatabase,
    private val syncDao: SyncDao,
    private val remote: SupabaseFinanceSyncDataSource,
) {
    private val mutex = Mutex()
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    val status: Flow<SyncStatus> =
        combine(syncDao.observeControl(), syncDao.observePendingCount()) { control, pending ->
            SyncStatus(
                isSyncing = control?.isSyncing == true,
                pendingChanges = pending,
                initialSyncCompleted = control?.isInitialSyncCompleted == true,
                lastSyncedAtEpochMillis = control?.lastSyncedAtEpochMillis,
                lastError = control?.lastError,
            )
        }

    /**
     * Binds the device cache to a single authenticated user, adopting legacy
     * local data on the first upgrade and clearing it when accounts change.
     * A network failure never makes the local app unusable.
     */
    suspend fun bootstrap(userId: String): Result<Unit> = mutex.withLock {
        runCatching {
            prepareLocalOwnership(userId)
            synchronizeLocked(userId)
        }.onFailure { error ->
            syncDao.setSyncing(value = false, error = error.userMessage())
        }
    }

    suspend fun synchronize(userId: String): Result<Unit> = mutex.withLock {
        runCatching {
            prepareLocalOwnership(userId)
            synchronizeLocked(userId)
        }.onFailure { error ->
            syncDao.setSyncing(value = false, error = error.userMessage())
        }
    }

    suspend fun clearAfterSignOut() = mutex.withLock {
        database.withTransaction {
            clearFinanceData()
            syncDao.clearOutbox()
            syncDao.assignUser(userId = null, initialSyncCompleted = false)
        }
    }

    suspend fun hasPendingChanges(): Boolean = syncDao.pendingCount() > 0

    private suspend fun prepareLocalOwnership(userId: String) {
        database.withTransaction {
            val existingControl = syncDao.getControl()
            val control = if (existingControl == null) {
                val created = SyncControlEntity()
                syncDao.upsertControl(created)
                created
            } else {
                existingControl
            }
            when {
                control.userId == userId -> Unit
                control.userId == null && syncDao.localRecordCount() > 0 -> {
                    syncDao.assignUser(userId, initialSyncCompleted = false)
                    enqueueAllLocalRecords()
                }
                else -> {
                    syncDao.setApplyingRemote(true)
                    clearFinanceData()
                    syncDao.clearOutbox()
                    syncDao.assignUser(userId, initialSyncCompleted = false)
                }
            }
        }
    }

    private suspend fun synchronizeLocked(userId: String) {
        syncDao.setSyncing(value = true, error = null)
        pushPendingChanges(userId)
        val snapshot = remote.fetchSnapshot(userId)
        if (snapshot.any { it.schemaVersion > CURRENT_SCHEMA_VERSION }) {
            throw UnsupportedRemoteSchemaException()
        }

        database.withTransaction {
            if (syncDao.pendingCount() != 0) {
                throw ConcurrentLocalWriteException()
            }
            syncDao.setApplyingRemote(true)
            try {
                clearFinanceData()
                applySnapshot(snapshot)
                syncDao.markSynchronized(System.currentTimeMillis())
            } finally {
                syncDao.setApplyingRemote(false)
            }
        }
    }

    private suspend fun pushPendingChanges(userId: String) {
        val ordered = orderSyncChanges(syncDao.getPendingChanges())

        ordered.forEach { change ->
            runCatching {
                val type = requireNotNull(SyncEntityType.fromWireName(change.entityType)) {
                    "Tipo de sincronización desconocido: ${change.entityType}"
                }
                val requestedOperation = SyncOperation.valueOf(change.operation)
                val payload = if (requestedOperation == SyncOperation.UPSERT) {
                    encodeLocalPayload(type, change.entityId)
                } else {
                    null
                }
                val effectiveDelete = requestedOperation == SyncOperation.DELETE || payload == null
                remote.upsert(
                    RemoteFinanceRecordWrite(
                        userId = userId,
                        entityType = type.wireName,
                        entityId = change.entityId,
                        payload = if (effectiveDelete) null else payload,
                        isDeleted = effectiveDelete,
                    ),
                )
                syncDao.removePendingChange(change.entityType, change.entityId, change.queuedAtEpochMillis)
            }.getOrElse { error ->
                syncDao.markPendingFailure(change.entityType, change.entityId, error.userMessage())
                throw error
            }
        }
    }

    private suspend fun encodeLocalPayload(type: SyncEntityType, id: String): JsonElement? =
        when (type) {
            SyncEntityType.FINANCIAL_SETUP ->
                syncDao.getFinancialSetup()?.let { json.encodeToJsonElement(it) }
            SyncEntityType.ACCOUNT ->
                syncDao.getAccount(id)?.let { json.encodeToJsonElement(it) }
            SyncEntityType.TRANSACTION ->
                syncDao.getTransaction(id)?.let { json.encodeToJsonElement(it) }
            SyncEntityType.INCOME_SOURCE ->
                syncDao.getIncomeSource(id)?.let { json.encodeToJsonElement(it) }
            SyncEntityType.DEBT ->
                syncDao.getDebt(id)?.let { json.encodeToJsonElement(it) }
            SyncEntityType.SAVINGS_PLAN ->
                syncDao.getSavingsPlan(id)?.let { json.encodeToJsonElement(it) }
            SyncEntityType.RECURRING_OBLIGATION ->
                syncDao.getRecurringObligation(id)?.let { json.encodeToJsonElement(it) }
            SyncEntityType.CREDIT_CARD_PROFILE ->
                syncDao.getCreditCardProfile(id)?.let { json.encodeToJsonElement(it) }
            SyncEntityType.INSTALLMENT_PURCHASE ->
                syncDao.getInstallmentPurchase(id)?.let { json.encodeToJsonElement(it) }
            SyncEntityType.CREDIT_CARD_PAYMENT ->
                syncDao.getCreditCardPayment(id)?.let { json.encodeToJsonElement(it) }
            SyncEntityType.SAVINGS_PROFILE ->
                syncDao.getSavingsProfile(id)?.let { json.encodeToJsonElement(it) }
            SyncEntityType.SAVINGS_MOVEMENT ->
                syncDao.getSavingsMovement(id)?.let { json.encodeToJsonElement(it) }
            SyncEntityType.LOAN_PROFILE ->
                syncDao.getLoanProfile(id)?.let { json.encodeToJsonElement(it) }
            SyncEntityType.LOAN_PAYMENT ->
                syncDao.getLoanPayment(id)?.let { json.encodeToJsonElement(it) }
        }

    private suspend fun applySnapshot(snapshot: List<RemoteFinanceRecord>) {
        val active = snapshot.filter {
            !it.isDeleted &&
                it.schemaVersion <= CURRENT_SCHEMA_VERSION &&
                it.payload != null &&
                SyncEntityType.fromWireName(it.entityType) != null
        }

        decodeSingle<FinancialSetupEntity>(active, SyncEntityType.FINANCIAL_SETUP)
            ?.let { syncDao.upsertFinancialSetup(it) }
        syncDao.upsertAccounts(decodeList(active, SyncEntityType.ACCOUNT))
        syncDao.upsertIncomeSources(decodeList(active, SyncEntityType.INCOME_SOURCE))
        syncDao.upsertDebts(decodeList(active, SyncEntityType.DEBT))
        syncDao.upsertSavingsPlans(decodeList(active, SyncEntityType.SAVINGS_PLAN))
        syncDao.upsertRecurringObligations(decodeList(active, SyncEntityType.RECURRING_OBLIGATION))
        syncDao.upsertCreditCardProfiles(decodeList(active, SyncEntityType.CREDIT_CARD_PROFILE))
        syncDao.upsertSavingsProfiles(decodeList(active, SyncEntityType.SAVINGS_PROFILE))
        syncDao.upsertLoanProfiles(decodeList(active, SyncEntityType.LOAN_PROFILE))
        syncDao.upsertTransactions(decodeList(active, SyncEntityType.TRANSACTION))
        syncDao.upsertInstallmentPurchases(decodeList(active, SyncEntityType.INSTALLMENT_PURCHASE))
        syncDao.upsertCreditCardPayments(decodeList(active, SyncEntityType.CREDIT_CARD_PAYMENT))
        syncDao.upsertSavingsMovements(decodeList(active, SyncEntityType.SAVINGS_MOVEMENT))
        syncDao.upsertLoanPayments(decodeList(active, SyncEntityType.LOAN_PAYMENT))
    }

    private inline fun <reified T> decodeSingle(
        records: List<RemoteFinanceRecord>,
        type: SyncEntityType,
    ): T? = records.firstOrNull { it.entityType == type.wireName }?.payload?.let {
        json.decodeFromJsonElement(it)
    }

    private inline fun <reified T> decodeList(
        records: List<RemoteFinanceRecord>,
        type: SyncEntityType,
    ): List<T> = records.asSequence()
        .filter { it.entityType == type.wireName }
        .mapNotNull { it.payload }
        .map { json.decodeFromJsonElement<T>(it) }
        .toList()

    private suspend fun enqueueAllLocalRecords() {
        val now = System.currentTimeMillis()
        syncDao.enqueueFinancialSetup(now)
        syncDao.enqueueAccounts(now)
        syncDao.enqueueIncomeSources(now)
        syncDao.enqueueDebts(now)
        syncDao.enqueueSavingsPlans(now)
        syncDao.enqueueRecurringObligations(now)
        syncDao.enqueueCreditCardProfiles(now)
        syncDao.enqueueSavingsProfiles(now)
        syncDao.enqueueLoanProfiles(now)
        syncDao.enqueueTransactions(now)
        syncDao.enqueueInstallmentPurchases(now)
        syncDao.enqueueCreditCardPayments(now)
        syncDao.enqueueSavingsMovements(now)
        syncDao.enqueueLoanPayments(now)
    }

    private suspend fun clearFinanceData() {
        syncDao.clearLoanPayments()
        syncDao.clearSavingsMovements()
        syncDao.clearCreditCardPayments()
        syncDao.clearInstallmentPurchases()
        syncDao.clearTransactions()
        syncDao.clearLoanProfiles()
        syncDao.clearSavingsProfiles()
        syncDao.clearCreditCardProfiles()
        syncDao.clearRecurringObligations()
        syncDao.clearSavingsPlans()
        syncDao.clearDebts()
        syncDao.clearIncomeSources()
        syncDao.clearFinancialSetup()
        syncDao.clearAccounts()
    }
}

private class ConcurrentLocalWriteException :
    IllegalStateException("Hay cambios locales nuevos; la sincronización se reintentará.")

private class UnsupportedRemoteSchemaException :
    IllegalStateException("Actualiza PocketMind para sincronizar el formato de datos más reciente.")

private fun Throwable.userMessage(): String = when (this) {
    is IOException -> "Sin conexión. Tus cambios siguen guardados en este dispositivo."
    is ConcurrentLocalWriteException, is UnsupportedRemoteSchemaException -> requireNotNull(message)
    else -> "No fue posible sincronizar. Tus cambios locales están seguros."
}

internal fun orderSyncChanges(changes: List<SyncOutboxEntity>): List<SyncOutboxEntity> =
    changes.sortedWith { left, right ->
        val leftOperation = SyncOperation.valueOf(left.operation)
        val rightOperation = SyncOperation.valueOf(right.operation)
        when {
            leftOperation != rightOperation ->
                if (leftOperation == SyncOperation.UPSERT) -1 else 1
            leftOperation == SyncOperation.DELETE ->
                syncPriority(right).compareTo(syncPriority(left))
            else -> syncPriority(left).compareTo(syncPriority(right))
        }
    }

private fun syncPriority(change: SyncOutboxEntity): Int =
    SyncEntityType.fromWireName(change.entityType)?.upsertPriority ?: Int.MAX_VALUE
