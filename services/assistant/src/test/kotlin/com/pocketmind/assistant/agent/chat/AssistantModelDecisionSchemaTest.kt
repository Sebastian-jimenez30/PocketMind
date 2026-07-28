package com.pocketmind.assistant.agent.chat

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
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
        assertTrue("missingFields" in required)
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
}
