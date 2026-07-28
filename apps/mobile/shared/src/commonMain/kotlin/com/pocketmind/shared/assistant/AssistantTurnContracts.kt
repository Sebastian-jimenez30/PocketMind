package com.pocketmind.shared.assistant

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

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
    val amountMinorUnits: Long,
    val currency: String,
    val primaryProductId: String,
    val primaryProductName: String,
    val destinationProductId: String? = null,
    val destinationProductName: String? = null,
    val merchant: String? = null,
    val categoryId: String? = null,
    val occurredAtEpochMillis: Long,
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
