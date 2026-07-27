package com.pocketmind.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Coalesced transactional outbox. SQLite triggers keep one latest operation per
 * record, so every repository write becomes synchronizable without coupling the
 * domain layer to the network.
 */
@Entity(
    tableName = "sync_outbox",
    primaryKeys = ["entityType", "entityId"],
)
data class SyncOutboxEntity(
    val entityType: String,
    val entityId: String,
    val operation: String,
    val queuedAtEpochMillis: Long,
    val attemptCount: Int = 0,
    val lastError: String? = null,
)

/** Local ownership and observable synchronization state for the active session. */
@Entity(tableName = "sync_control")
data class SyncControlEntity(
    @PrimaryKey val id: Int = SINGLETON_ID,
    val userId: String? = null,
    val isApplyingRemote: Boolean = false,
    val isInitialSyncCompleted: Boolean = false,
    val isSyncing: Boolean = false,
    val lastSyncedAtEpochMillis: Long? = null,
    val lastError: String? = null,
) {
    companion object {
        const val SINGLETON_ID = 1
    }
}
