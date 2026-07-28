package com.pocketmind.shared.assistant

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals

class AssistantDraftContractsTest {
    private val json = Json {
        encodeDefaults = true
    }

    @Test
    fun `draft lifecycle uses stable wire states and keeps executable payload`() {
        val draft = AssistantCommandDraft(
            id = "11111111-1111-4111-8111-111111111111",
            conversationId = "22222222-2222-4222-8222-222222222222",
            commandType = "record_expense",
            commandPayload = buildJsonObject {
                put("schema_version", 1)
            },
            commandSchemaVersion = 1,
            state = AssistantDraftState.CONFIRMED,
            idempotencyKey = "assistant-turn:test",
            payloadHash = "hash",
            financialStateVersion = 42,
            version = 2,
            expiresAt = "2026-07-28T18:00:00Z",
            createdAt = "2026-07-28T17:00:00Z",
            updatedAt = "2026-07-28T17:01:00Z",
        )

        val encoded = json.encodeToString(AssistantCommandDraft.serializer(), draft)
        val decoded = json.decodeFromString(AssistantCommandDraft.serializer(), encoded)

        assertEquals(AssistantDraftState.CONFIRMED, decoded.state)
        assertEquals(1, decoded.commandPayload["schema_version"]?.toString()?.toInt())
        assertEquals(42, decoded.financialStateVersion)
    }

    @Test
    fun `cancel request carries the state and exact optimistic version`() {
        val request = AssistantDraftTransitionRequest(
            expectedVersion = 3,
            expectedState = AssistantDraftState.PROPOSED,
        )

        val encoded = json.encodeToString(
            AssistantDraftTransitionRequest.serializer(),
            request,
        )

        assertEquals(
            """{"expectedVersion":3,"expectedState":"proposed"}""",
            encoded,
        )
    }

    @Test
    fun `revision request preserves exact version and structured command`() {
        val request = AssistantDraftRevisionRequest(
            expectedVersion = 4,
            commandPayload = buildJsonObject {
                put("type", "record_expense")
                put("command_id", "33333333-3333-4333-8333-333333333333")
            },
        )

        val encoded = json.encodeToString(
            AssistantDraftRevisionRequest.serializer(),
            request,
        )
        val decoded = json.decodeFromString(
            AssistantDraftRevisionRequest.serializer(),
            encoded,
        )

        assertEquals(4, decoded.expectedVersion)
        assertEquals(
            "\"record_expense\"",
            decoded.commandPayload["type"].toString(),
        )
    }
}
