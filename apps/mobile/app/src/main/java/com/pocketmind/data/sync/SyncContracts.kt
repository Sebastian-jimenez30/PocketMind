package com.pocketmind.data.sync

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

enum class SyncEntityType(val wireName: String, val upsertPriority: Int) {
    FINANCIAL_SETUP("FINANCIAL_SETUP", 0),
    ACCOUNT("ACCOUNT", 10),
    INCOME_SOURCE("INCOME_SOURCE", 20),
    DEBT("DEBT", 20),
    SAVINGS_PLAN("SAVINGS_PLAN", 20),
    RECURRING_OBLIGATION("RECURRING_OBLIGATION", 20),
    CREDIT_CARD_PROFILE("CREDIT_CARD_PROFILE", 30),
    SAVINGS_PROFILE("SAVINGS_PROFILE", 30),
    LOAN_PROFILE("LOAN_PROFILE", 30),
    TRANSACTION("TRANSACTION", 40),
    INSTALLMENT_PURCHASE("INSTALLMENT_PURCHASE", 40),
    CREDIT_CARD_PAYMENT("CREDIT_CARD_PAYMENT", 40),
    SAVINGS_MOVEMENT("SAVINGS_MOVEMENT", 40),
    LOAN_PAYMENT("LOAN_PAYMENT", 40);

    companion object {
        fun fromWireName(value: String): SyncEntityType? =
            entries.firstOrNull { it.wireName == value }
    }
}

enum class SyncOperation {
    UPSERT,
    DELETE,
}

data class SyncStatus(
    val isSyncing: Boolean = false,
    val pendingChanges: Int = 0,
    val initialSyncCompleted: Boolean = false,
    val lastSyncedAtEpochMillis: Long? = null,
    val lastError: String? = null,
)

@Serializable
data class RemoteFinanceRecordWrite(
    @SerialName("user_id") val userId: String,
    @SerialName("entity_type") val entityType: String,
    @SerialName("entity_id") val entityId: String,
    @SerialName("schema_version") val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val payload: JsonElement?,
    @SerialName("is_deleted") val isDeleted: Boolean,
)

@Serializable
data class RemoteFinanceRecord(
    @SerialName("user_id") val userId: String,
    @SerialName("entity_type") val entityType: String,
    @SerialName("entity_id") val entityId: String,
    @SerialName("schema_version") val schemaVersion: Int,
    val payload: JsonElement?,
    @SerialName("is_deleted") val isDeleted: Boolean,
    @SerialName("updated_at_epoch_millis") val updatedAtEpochMillis: Long,
)

const val CURRENT_SCHEMA_VERSION = 1
