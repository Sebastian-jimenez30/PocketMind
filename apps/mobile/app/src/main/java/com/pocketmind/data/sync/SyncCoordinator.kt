package com.pocketmind.data.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.BackoffPolicy
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/** Minimal synchronization boundary required after an authentication session becomes active. */
fun interface SessionBootstrapper {
    suspend fun bootstrapCurrentSession(): Result<Unit>
}

@Singleton
class SyncCoordinator @Inject constructor(
    @ApplicationContext context: Context,
    private val supabase: SupabaseClient,
    private val engine: FinanceSyncEngine,
) : SessionBootstrapper {
    private val workManager = WorkManager.getInstance(context)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var started = false

    val status = engine.status

    fun start() {
        if (started) return
        started = true
        schedulePeriodicSync()
        scope.launch {
            engine.status
                .distinctUntilChanged { old, new -> old.pendingChanges == new.pendingChanges }
                .collect { status ->
                    if (status.pendingChanges > 0) enqueueImmediateSync()
                }
        }
    }

    override suspend fun bootstrapCurrentSession(): Result<Unit> = runCatching {
        val user = supabase.auth.retrieveUserForCurrentSession()
        engine.bootstrap(user.id).getOrThrow()
    }

    suspend fun syncCurrentSession(): Result<Unit> = runCatching {
        val user = supabase.auth.retrieveUserForCurrentSession()
        engine.synchronize(user.id).getOrThrow()
    }

    suspend fun syncInBackground(): Result<Unit> {
        val userId = supabase.auth.currentUserOrNull()?.id ?: return Result.success(Unit)
        return engine.synchronize(userId)
    }

    suspend fun prepareSignOut(): Result<Unit> {
        val result = syncCurrentSession()
        return if (result.isFailure && engine.hasPendingChanges()) {
            Result.failure(
                IllegalStateException(
                    "Hay cambios pendientes. Conéctate a Internet antes de cerrar sesión para no perderlos.",
                ),
            )
        } else {
            Result.success(Unit)
        }
    }

    suspend fun clearAfterSignOut() = engine.clearAfterSignOut()

    fun requestForegroundSync() {
        enqueueImmediateSync()
    }

    private fun enqueueImmediateSync() {
        val request = OneTimeWorkRequestBuilder<FinanceSyncWorker>()
            .setConstraints(networkConstraints())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        workManager.enqueueUniqueWork(IMMEDIATE_WORK, ExistingWorkPolicy.KEEP, request)
    }

    private fun schedulePeriodicSync() {
        val request = PeriodicWorkRequestBuilder<FinanceSyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(networkConstraints())
            .build()
        workManager.enqueueUniquePeriodicWork(
            PERIODIC_WORK,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    private fun networkConstraints(): Constraints =
        Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

    private companion object {
        const val IMMEDIATE_WORK = "pocketmind-finance-sync-now"
        const val PERIODIC_WORK = "pocketmind-finance-sync-periodic"
    }
}
