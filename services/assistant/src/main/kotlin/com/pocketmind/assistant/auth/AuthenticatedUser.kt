package com.pocketmind.assistant.auth

import io.ktor.server.auth.Principal

class AuthenticatedUser(
    val userId: String,
    val role: String,
    internal val accessToken: SupabaseAccessToken,
) : Principal

/**
 * Keeps the caller JWT available for RLS-protected Supabase requests without
 * allowing accidental interpolation in logs or exception messages.
 */
class SupabaseAccessToken internal constructor(
    private val value: String,
) {
    internal fun reveal(): String = value

    override fun toString(): String = "[REDACTED]"
}

fun interface SupabaseTokenVerifier {
    suspend fun verify(token: String): AuthenticatedUser?
}
