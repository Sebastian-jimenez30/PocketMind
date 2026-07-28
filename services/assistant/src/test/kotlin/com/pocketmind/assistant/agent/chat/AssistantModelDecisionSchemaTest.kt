package com.pocketmind.assistant.agent.chat

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
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
        assertTrue("intent" in required)
        assertTrue("missingFields" in required)
    }
}
