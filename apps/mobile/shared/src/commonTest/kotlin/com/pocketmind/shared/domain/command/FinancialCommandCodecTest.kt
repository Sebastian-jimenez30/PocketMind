package com.pocketmind.shared.domain.command

import com.pocketmind.shared.domain.model.CurrencyCode
import com.pocketmind.shared.domain.model.InstallmentRatePeriod
import com.pocketmind.shared.domain.model.Money
import com.pocketmind.shared.domain.model.TransactionSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FinancialCommandCodecTest {
    @Test
    fun `command round trip preserves stable type and promotional periods`() {
        val command = FinancialCommand.RecordCardPurchase(
            commandId = "purchase-1",
            cardId = "card",
            merchant = "Tienda",
            principal = Money(200_000, CurrencyCode.COP),
            installmentCount = 6,
            purchasedAtEpochMillis = 1_800_000_000_000,
            source = TransactionSource.VOICE,
            promotionalRatePeriods = listOf(
                InstallmentRatePeriod(1, 3, 0),
            ),
        )

        val encoded = FinancialCommandCodec.encode(command)
        val decoded = FinancialCommandCodec.decode(encoded).getOrThrow()

        assertEquals(command, decoded)
        assertTrue("\"command_type\":\"record_card_purchase\"" in encoded)
        assertTrue("\"schema_version\":1" in encoded)
        assertTrue("\"rule_version\":1" in encoded)
    }

    @Test
    fun `unsupported schema fails closed`() {
        val encoded = FinancialCommandCodec.encode(
            FinancialCommand.DeleteTransaction(
                commandId = "delete-1",
                transactionId = "transaction-1",
            ),
        ).replace("\"schema_version\":1", "\"schema_version\":99")

        assertTrue(FinancialCommandCodec.decode(encoded).isFailure)
    }
}
