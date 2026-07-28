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
data class ApiErrorResponse(
    val error: ApiError,
)

@Serializable
data class ApiError(
    val code: String,
    val message: String,
    val requestId: String,
)
