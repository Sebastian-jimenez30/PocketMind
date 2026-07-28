package com.pocketmind.assistant.domain.turn

import com.pocketmind.assistant.agent.chat.AssistantDecisionAction
import com.pocketmind.assistant.agent.chat.AssistantFinancialIntent
import com.pocketmind.assistant.agent.chat.AssistantInterpreterProduct
import com.pocketmind.assistant.agent.chat.AssistantModelDecision
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class AssistantProductReferenceSanitizerTest {

    @Test
    fun `latest visible card name replaces a stale internal reference`() {
        val decision = AssistantModelDecision(
            action = AssistantDecisionAction.PROPOSE,
            intent = AssistantFinancialIntent.RECORD_CARD_PAYMENT,
            primaryProductReference = UNKNOWN_PRODUCT_ID,
            paymentType = "FULL_BALANCE",
        )

        val sanitized = decision.withSafeProductReferences(
            products = PRODUCTS,
            latestUserMessage = "pagué toda la deuda de la tarjeta de crédito de Nu",
        )

        assertEquals("Crédito Nu", sanitized.primaryProductReference)
        assertNull(sanitized.sourceProductReference)
    }

    @Test
    fun `known internal id becomes its visible product name`() {
        val decision = AssistantModelDecision(
            action = AssistantDecisionAction.PROPOSE,
            intent = AssistantFinancialIntent.RECORD_CARD_PAYMENT,
            primaryProductReference = CARD.id,
        )

        val sanitized = decision.withSafeProductReferences(
            products = PRODUCTS,
            latestUserMessage = "pagué el saldo completo",
        )

        assertEquals(CARD.name, sanitized.primaryProductReference)
    }

    @Test
    fun `unknown internal id is removed when no product can be inferred`() {
        val decision = AssistantModelDecision(
            action = AssistantDecisionAction.PROPOSE,
            intent = AssistantFinancialIntent.RECORD_CARD_PAYMENT,
            primaryProductReference = UNKNOWN_PRODUCT_ID,
        )

        val sanitized = decision.withSafeProductReferences(
            products = PRODUCTS,
            latestUserMessage = "pagué el saldo completo",
        )

        assertNull(sanitized.primaryProductReference)
    }

    @Test
    fun `assistant reply never exposes an internal uuid`() {
        val decision = AssistantModelDecision(
            action = AssistantDecisionAction.CLARIFY,
            reply = "No pude identificar \"$UNKNOWN_PRODUCT_ID\".",
        )

        val sanitized = decision.withSafeProductReferences(
            products = PRODUCTS,
            latestUserMessage = "crédito Nu",
        )

        assertFalse(requireNotNull(sanitized.reply).contains(UNKNOWN_PRODUCT_ID))
    }

    private companion object {
        const val UNKNOWN_PRODUCT_ID = "a3924ade-4ed5-4530-bf68-bff93a152354"
        val CARD = AssistantInterpreterProduct(
            id = "card-1",
            name = "Crédito Nu",
            type = "CREDIT_CARD",
            currency = "COP",
            aliases = listOf("tarjeta Nu"),
        )
        val PRODUCTS = listOf(
            CARD,
            AssistantInterpreterProduct(
                id = "savings-1",
                name = "Ahorros Nu",
                type = "SAVINGS",
                currency = "COP",
                aliases = listOf("Nu"),
            ),
            AssistantInterpreterProduct(
                id = "bank-1",
                name = "Ahorros Bancolombia",
                type = "BANK_ACCOUNT",
                currency = "COP",
                aliases = listOf("Bancolombia"),
            ),
        )
    }
}
