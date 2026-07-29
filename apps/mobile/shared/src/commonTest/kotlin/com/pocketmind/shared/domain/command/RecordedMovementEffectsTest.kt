package com.pocketmind.shared.domain.command

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class RecordedMovementEffectsTest {
    @Test
    fun `maps every deterministic ledger and savings effect`() {
        val effects = recordedMovementEffects("command-1")

        assertEquals("command-1", effects.commandId)
        assertEquals(
            setOf(
                "command-1",
                "purchase-command-1",
                "card-payment-command-1",
                "savings-command-1",
                "loan-payment-command-1",
            ),
            effects.ledgerTransactionIds,
        )
        assertEquals(
            setOf(
                "command-1",
                "source-savings-command-1",
                "related-savings-command-1",
            ),
            effects.savingsMovementIds,
        )
    }

    @Test
    fun `rejects a blank command id`() {
        assertFailsWith<IllegalArgumentException> {
            recordedMovementEffects(" ")
        }
    }

    @Test
    fun `resolves every ledger id to its originating command`() {
        listOf(
            "command-1",
            "purchase-command-1",
            "card-payment-command-1",
            "savings-command-1",
            "source-savings-command-1",
            "related-savings-command-1",
            "loan-payment-command-1",
        ).forEach { transactionId ->
            assertEquals(
                "command-1",
                recordedMovementCommandId(transactionId),
            )
        }
    }
}
