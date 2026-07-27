package com.pocketmind.data.sync

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SupabaseFinanceSyncDataSource @Inject constructor(
    private val supabase: SupabaseClient,
) {
    suspend fun upsert(record: RemoteFinanceRecordWrite) {
        supabase.postgrest.from(TABLE).upsert(record)
    }

    suspend fun fetchSnapshot(userId: String): List<RemoteFinanceRecord> =
        supabase.postgrest.from(TABLE)
            .select {
                filter { eq("user_id", userId) }
            }
            .decodeList()

    private companion object {
        const val TABLE = "finance_sync_records"
    }
}
