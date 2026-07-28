package com.pocketmind.assistant.auth

import io.ktor.server.auth.Principal

data class AuthenticatedUser(
    val userId: String,
    val role: String,
) : Principal

fun interface SupabaseTokenVerifier {
    suspend fun verify(token: String): AuthenticatedUser?
}
