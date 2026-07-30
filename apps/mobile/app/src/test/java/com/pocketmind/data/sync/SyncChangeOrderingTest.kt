package com.pocketmind.data.sync

import com.pocketmind.data.local.entity.SyncOutboxEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class SyncChangeOrderingTest {
    @Test
    fun upsertsPlaceParentProductsBeforeDependentMovements() {
        val ordered = orderSyncChanges(
            listOf(
                change("TRANSACTION", "movement", "UPSERT"),
                change("ACCOUNT", "product", "UPSERT"),
            ),
        )

        assertEquals(listOf("ACCOUNT", "TRANSACTION"), ordered.map { it.entityType })
    }

    @Test
    fun customCategoriesAreUploadedBeforeBudgetsAndCategorizedTransactions() {
        val ordered = orderSyncChanges(
            listOf(
                change("TRANSACTION", "movement", "UPSERT"),
                change("BUDGET", "budget", "UPSERT"),
                change("CUSTOM_CATEGORY", "category", "UPSERT"),
            ),
        )

        assertEquals(
            listOf("CUSTOM_CATEGORY", "BUDGET", "TRANSACTION"),
            ordered.map { it.entityType },
        )
    }

    @Test
    fun deletesPlaceDependentMovementsBeforeParentProducts() {
        val ordered = orderSyncChanges(
            listOf(
                change("ACCOUNT", "product", "DELETE"),
                change("TRANSACTION", "movement", "DELETE"),
            ),
        )

        assertEquals(listOf("TRANSACTION", "ACCOUNT"), ordered.map { it.entityType })
    }

    @Test
    fun upsertsAreSentBeforeDeletionTombstones() {
        val ordered = orderSyncChanges(
            listOf(
                change("ACCOUNT", "old", "DELETE"),
                change("ACCOUNT", "new", "UPSERT"),
            ),
        )

        assertEquals(listOf("UPSERT", "DELETE"), ordered.map { it.operation })
    }

    private fun change(type: String, id: String, operation: String) =
        SyncOutboxEntity(
            entityType = type,
            entityId = id,
            operation = operation,
            queuedAtEpochMillis = 1,
        )
}
