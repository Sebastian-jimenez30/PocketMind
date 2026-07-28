package com.pocketmind.shared.assistant

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * Stable transport contracts shared by Android, iOS and the assistant service.
 *
 * A turn may create a proposal, but it never executes a financial command.
 */
@Serializable
data class AssistantTurnRequest(
    val conversationId: String? = null,
    val clientMessageId: String,
    val content: String,
    val locale: String = "es-CO",
    val timeZoneId: String = "America/Bogota",
)

@Serializable
enum class AssistantTurnStatus {
    @SerialName("conversation")
    CONVERSATION,

    @SerialName("clarification")
    CLARIFICATION,

    @SerialName("proposal")
    PROPOSAL,

    @SerialName("unsupported")
    UNSUPPORTED,
}

@Serializable
data class AssistantChatMessage(
    val id: String,
    val turnId: String,
    val role: String,
    val content: String,
    val createdAt: String,
)

@Serializable
data class AssistantDraftPreview(
    val id: String,
    val version: Long,
    val commandType: String,
    val amountMinorUnits: Long? = null,
    val currency: String? = null,
    val primaryProductId: String,
    val primaryProductName: String,
    val destinationProductId: String? = null,
    val destinationProductName: String? = null,
    val merchant: String? = null,
    val categoryId: String? = null,
    val occurredAtEpochMillis: Long,
    val productType: String? = null,
    val installmentCount: Int? = null,
    val annualRateBasisPoints: Int? = null,
    val paymentType: String? = null,
    val movementType: String? = null,
    val expiresAt: String,
)

@Serializable
data class AssistantTurnResponse(
    val conversationId: String,
    val turnId: String,
    val status: AssistantTurnStatus,
    val reply: String,
    val userMessage: AssistantChatMessage,
    val assistantMessage: AssistantChatMessage,
    val draft: AssistantDraftPreview? = null,
)

@Serializable
enum class AssistantDraftState {
    @SerialName("proposed")
    PROPOSED,

    @SerialName("confirmed")
    CONFIRMED,

    @SerialName("cancelled")
    CANCELLED,

    @SerialName("completed")
    COMPLETED,

    @SerialName("failed")
    FAILED,

    @SerialName("expired")
    EXPIRED,
}

/**
 * Full draft returned only by authenticated lifecycle endpoints.
 *
 * The preview shown in chat intentionally omits [commandPayload]. Platforms
 * must execute this payload only after explicitly confirming the exact draft
 * version that was presented to the user.
 */
@Serializable
data class AssistantCommandDraft(
    val id: String,
    val conversationId: String,
    val commandType: String,
    val commandPayload: JsonObject,
    val commandSchemaVersion: Int,
    val state: AssistantDraftState,
    val idempotencyKey: String,
    val payloadHash: String,
    val financialStateVersion: Long,
    val executionResult: JsonObject? = null,
    val errorCode: String? = null,
    val version: Long,
    val expiresAt: String,
    val createdAt: String,
    val updatedAt: String,
)

@Serializable
data class AssistantDraftTransitionRequest(
    val expectedVersion: Long,
    val expectedState: AssistantDraftState? = null,
)

@Serializable
data class AssistantDraftCompletionRequest(
    val expectedVersion: Long,
    val executionResult: JsonObject,
)

@Serializable
data class AssistantDraftFailureRequest(
    val expectedVersion: Long,
    val executionResult: JsonObject,
    val errorCode: String,
)
