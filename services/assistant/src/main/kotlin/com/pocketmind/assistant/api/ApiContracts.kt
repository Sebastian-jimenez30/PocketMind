package com.pocketmind.assistant.api

import kotlinx.serialization.Serializable

@Serializable
data class HealthResponse(
    val status: String,
    val service: String,
    val version: String,
    val environment: String,
    val dependencies: Map<String, String>,
)

@Serializable
data class SessionResponse(
    val userId: String,
    val role: String,
)

@Serializable
data class CreateConversationRequest(
    val id: String,
    val title: String? = null,
    val locale: String = "es-CO",
)

@Serializable
data class ConversationResponse(
    val id: String,
    val title: String?,
    val status: String,
    val locale: String,
    val promptVersion: String,
    val toolSchemaVersion: Int,
    val lastMessageAt: String?,
    val createdAt: String,
    val updatedAt: String,
)

@Serializable
data class ConversationDetailResponse(
    val conversation: ConversationResponse,
    val messages: List<MessageResponse>,
)

@Serializable
data class MessageResponse(
    val id: String,
    val turnId: String,
    val role: String,
    val content: String,
    val inputModality: String,
    val promptVersion: String?,
    val modelId: String?,
    val createdAt: String,
)

@Serializable
data class ApiErrorResponse(
    val error: ApiError,
)

@Serializable
data class ApiError(
    val code: String,
    val message: String,
    val requestId: String,
)
