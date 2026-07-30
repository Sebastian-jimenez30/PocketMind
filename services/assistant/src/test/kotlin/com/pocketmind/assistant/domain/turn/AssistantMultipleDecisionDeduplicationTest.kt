package com.pocketmind.assistant.domain.turn

import com.pocketmind.assistant.agent.chat.AssistantAdditionalDecision
import com.pocketmind.assistant.agent.chat.AssistantDecisionAction
import com.pocketmind.assistant.agent.chat.AssistantFinancialIntent
import com.pocketmind.assistant.agent.chat.AssistantModelDecision
import kotlin.test.Test
import kotlin.test.assertEquals

class AssistantMultipleDecisionDeduplicationTest {
    @Test
    fun `same source fragment produces only one additional movement`() {
        val result = deduplicateAdditionalDecisions(
            primary = AssistantModelDecision(
                action = AssistantDecisionAction.PROPOSE,
                intent = AssistantFinancialIntent.RECORD_INCOME,
                amountMinorUnits = 20_000,
                sourceText = "me consignaron 20000",
            ),
            additional = listOf(
                expense(sourceText = "pagué 5000 de una compra"),
                expense(sourceText = "pagué 5000 de una compra"),
            ),
        )

        assertEquals(1, result.size)
        assertEquals(5_000L, result.single().amountMinorUnits)
    }

    @Test
    fun `different explicit fragments can preserve equal movements`() {
        val result = deduplicateAdditionalDecisions(
            primary = AssistantModelDecision(
                action = AssistantDecisionAction.PROPOSE,
                intent = AssistantFinancialIntent.RECORD_INCOME,
                amountMinorUnits = 20_000,
                sourceText = "me consignaron 20000",
            ),
            additional = listOf(
                expense(sourceText = "pagué 5000 en la mañana"),
                expense(sourceText = "pagué otros 5000 en la tarde"),
            ),
        )

        assertEquals(2, result.size)
    }

    @Test
    fun `semantic fallback removes duplicates when source fragments are absent`() {
        val result = deduplicateAdditionalDecisions(
            primary = AssistantModelDecision(
                action = AssistantDecisionAction.PROPOSE,
                intent = AssistantFinancialIntent.RECORD_INCOME,
                amountMinorUnits = 20_000,
            ),
            additional = listOf(
                expense(sourceText = null),
                expense(sourceText = null),
            ),
        )

        assertEquals(1, result.size)
    }

    private fun expense(sourceText: String?): AssistantAdditionalDecision =
        AssistantAdditionalDecision(
            action = AssistantDecisionAction.PROPOSE,
            intent = AssistantFinancialIntent.RECORD_EXPENSE,
            amountMinorUnits = 5_000,
            primaryProductReference = "Bancolombia Ahorros",
            merchant = "Compra",
            sourceText = sourceText,
        )
}
