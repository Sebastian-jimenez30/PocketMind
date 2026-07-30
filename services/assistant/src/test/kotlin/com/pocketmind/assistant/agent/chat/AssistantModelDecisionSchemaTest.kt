package com.pocketmind.assistant.agent.chat

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class AssistantModelDecisionSchemaTest {
    @Test
    fun `OpenAI schema requires every declared property`() {
        val schema = createAssistantDecisionOutputStructure(
            Json {
                encodeDefaults = true
                explicitNulls = false
                ignoreUnknownKeys = false
            },
        ).schema.schema

        val properties = schema.getValue("properties").jsonObject.keys
        val required = schema.getValue("required")
            .jsonArray
            .map { it.jsonPrimitive.content }
            .toSet()

        assertEquals(properties, required)
        assertTrue("reply" in required)
        assertTrue("intent" in required)
        assertTrue("promotionalRatePeriods" in required)
        assertTrue("paymentType" in required)
        assertTrue("savingsMovementType" in required)
        assertTrue("transactionId" in required)
        assertTrue("missingFields" in required)
    }

    @Test
    fun `additional decisions use a finite non-recursive schema`() {
        val schema = createAssistantDecisionOutputStructure(
            Json {
                encodeDefaults = true
                explicitNulls = false
                ignoreUnknownKeys = false
            },
        ).schema.schema
        val items = schema.getValue("properties")
            .jsonObject
            .getValue("additionalDecisions")
            .jsonObject
            .getValue("items")
            .jsonObject
        val reference = items["\$ref"]?.jsonPrimitive?.content

        assertFalse(
            reference.orEmpty().contains("AssistantModelDecision"),
            "The additional action cannot reference the root decision recursively.",
        )
        if (reference != null) {
            val definition = reference.substringAfterLast('/')
            assertTrue(
                definition in schema.getValue("\$defs").jsonObject,
                "Every schema reference must have a matching definition.",
            )
        }
        val additionalSchema = reference
            ?.substringAfterLast('/')
            ?.let { schema.getValue("\$defs").jsonObject.getValue(it).jsonObject }
            ?: items
        val additionalProperties = additionalSchema
            .getValue("properties")
            .jsonObject
            .keys
        val additionalRequired = additionalSchema
            .getValue("required")
            .jsonArray
            .map { it.jsonPrimitive.content }
            .toSet()

        assertEquals(additionalProperties, additionalRequired)
        assertFalse("additionalDecisions" in additionalProperties)
    }

    @Test
    fun `conversation response is represented inside the structured contract`() {
        val encoded = Json.encodeToString(
            AssistantModelDecision(
                action = AssistantDecisionAction.RESPOND,
                reply = "¡Hola! Puedo ayudarte con tus finanzas.",
            ),
        )

        assertContains(encoded, "\"action\":\"respond\"")
        assertContains(encoded, "\"reply\":\"¡Hola!")
    }

    @Test
    fun `advanced financial action is represented inside the structured contract`() {
        val encoded = Json.encodeToString(
            AssistantModelDecision(
                action = AssistantDecisionAction.PROPOSE,
                intent = AssistantFinancialIntent.RECORD_CARD_PURCHASE,
                amountMinorUnits = 1_200_000,
                primaryProductReference = "card-id",
                merchant = "Celular",
                installmentCount = 6,
                promotionalRatePeriods = listOf(
                    AssistantPromotionalRatePeriod(1, 3, 0),
                ),
            ),
        )

        assertContains(encoded, "\"intent\":\"record_card_purchase\"")
        assertContains(encoded, "\"installmentCount\":6")
        assertContains(encoded, "\"annualInterestBasisPoints\":0")
    }

    @Test
    fun `multiple actions are encoded without nested additional decisions`() {
        val encoded = Json.encodeToString(
            AssistantModelDecision(
                action = AssistantDecisionAction.PROPOSE,
                intent = AssistantFinancialIntent.RECORD_INCOME,
                amountMinorUnits = 30_000,
                additionalDecisions = listOf(
                    AssistantAdditionalDecision(
                        action = AssistantDecisionAction.PROPOSE,
                        intent = AssistantFinancialIntent.RECORD_EXPENSE,
                        amountMinorUnits = 5_000,
                        merchant = "Tienda",
                    ),
                ),
            ),
        )

        assertContains(encoded, "\"additionalDecisions\":[")
        assertContains(encoded, "\"intent\":\"record_expense\"")
    }
}
