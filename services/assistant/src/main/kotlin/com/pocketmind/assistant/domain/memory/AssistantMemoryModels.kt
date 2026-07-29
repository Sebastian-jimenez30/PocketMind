package com.pocketmind.assistant.domain.memory

import kotlinx.serialization.json.JsonObject
import java.time.Instant

enum class ConversationStatus(val wireValue: String) {
    ACTIVE("active"),
    ARCHIVED("archived"),
}

enum class MessageRole(val wireValue: String) {
    USER("user"),
    ASSISTANT("assistant"),
    SYSTEM("system"),
    TOOL("tool"),
}

enum class InputModality(val wireValue: String) {
    TEXT("text"),
    VOICE_TRANSCRIPT("voice_transcript"),
    SYSTEM("system"),
    TOOL("tool"),
}

enum class DraftState(val wireValue: String) {
    PROPOSED("proposed"),
    CONFIRMED("confirmed"),
    CANCELLED("cancelled"),
    COMPLETED("completed"),
    FAILED("failed"),
    EXPIRED("expired"),
}

data class AssistantConversation(
    val id: String,
    val userId: String,
    val title: String?,
    val status: ConversationStatus,
    val locale: String,
    val promptVersion: String,
    val toolSchemaVersion: Int,
    val lastMessageAt: Instant?,
    val createdAt: Instant,
    val updatedAt: Instant,
)

data class NewConversation(
    val id: String,
    val title: String?,
    val locale: String,
    val promptVersion: String,
    val toolSchemaVersion: Int,
)

data class AssistantMessage(
    val id: String,
    val conversationId: String,
    val userId: String,
    val turnId: String,
    val clientMessageId: String?,
    val role: MessageRole,
    val content: String,
    val inputModality: InputModality,
    val promptVersion: String?,
    val modelId: String?,
    val createdAt: Instant,
)

data class NewMessage(
    val id: String,
    val conversationId: String,
    val turnId: String,
    val clientMessageId: String?,
    val role: MessageRole,
    val content: String,
    val inputModality: InputModality,
    val promptVersion: String?,
    val modelId: String?,
)

data class AssistantCommandDraft(
    val id: String,
    val conversationId: String,
    val userId: String,
    val commandType: String,
    val commandPayload: JsonObject,
    val commandSchemaVersion: Int,
    val state: DraftState,
    val idempotencyKey: String,
    val payloadHash: String,
    val financialStateVersion: Long,
    val executionResult: JsonObject?,
    val errorCode: String?,
    val version: Long,
    val expiresAt: Instant,
    val createdAt: Instant,
    val updatedAt: Instant,
)

data class NewCommandDraft(
    val id: String,
    val conversationId: String,
    val commandType: String,
    val commandPayload: JsonObject,
    val commandSchemaVersion: Int,
    val idempotencyKey: String,
    val financialStateVersion: Long,
    val expiresAt: Instant,
)

data class ProposedDraftRevision(
    val commandType: String,
    val commandPayload: JsonObject,
    val commandSchemaVersion: Int,
    val financialStateVersion: Long,
    val expiresAt: Instant,
)

data class DraftTransition(
    val expectedState: DraftState,
    val nextState: DraftState,
    val expectedVersion: Long,
    val executionResult: JsonObject? = null,
    val errorCode: String? = null,
)

data class AssistantCommandEvent(
    val id: String,
    val draftId: String,
    val eventType: String,
    val fromState: DraftState?,
    val toState: DraftState,
    val draftVersion: Long,
    val createdAt: Instant,
)

data class AssistantProductAlias(
    val id: String,
    val userId: String,
    val productId: String,
    val productType: String,
    val alias: String,
    val normalizedAlias: String,
    val createdAt: Instant,
    val updatedAt: Instant,
)

data class NewProductAlias(
    val id: String,
    val productId: String,
    val productType: String,
    val alias: String,
)

data class AssistantCheckpoint(
    val id: String,
    val conversationId: String,
    val userId: String,
    val checkpointKey: String,
    val graphVersion: String,
    val checkpointVersion: Long,
    val state: JsonObject,
    val checkpointCreatedAt: Instant,
    val expiresAt: Instant,
)

data class NewAssistantCheckpoint(
    val id: String,
    val conversationId: String,
    val checkpointKey: String,
    val graphVersion: String,
    val checkpointVersion: Long,
    val state: JsonObject,
    val checkpointCreatedAt: Instant,
    val expiresAt: Instant,
)
