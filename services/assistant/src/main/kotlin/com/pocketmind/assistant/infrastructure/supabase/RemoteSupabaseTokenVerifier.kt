package com.pocketmind.assistant.infrastructure.supabase

import com.pocketmind.assistant.auth.AuthenticatedUser
import com.pocketmind.assistant.auth.SupabaseTokenVerifier
import com.pocketmind.assistant.config.AssistantConfig
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.accept
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class RemoteSupabaseTokenVerifier(
    private val client: HttpClient,
    private val config: AssistantConfig,
) : SupabaseTokenVerifier {
    override suspend fun verify(token: String): AuthenticatedUser? {
        if (!token.hasJwtShape()) return null

        val response = runCatching {
            client.get("${config.supabaseUrl}/auth/v1/user") {
                accept(ContentType.Application.Json)
                header("apikey", config.supabasePublishableKey.reveal())
                bearerAuth(token)
            }
        }.getOrNull() ?: return null

        if (!response.status.isSuccess()) return null

        val user = runCatching { response.body<SupabaseUserResponse>() }
            .getOrNull()
            ?: return null

        return user.id
            .trim()
            .takeIf(String::isNotBlank)
            ?.let { id ->
                AuthenticatedUser(
                    userId = id,
                    role = user.role?.trim().orEmpty().ifBlank { DEFAULT_ROLE },
                )
            }
    }

    private companion object {
        const val DEFAULT_ROLE = "authenticated"
    }
}

fun createSupabaseHttpClient(config: AssistantConfig): HttpClient =
    HttpClient(CIO) {
        expectSuccess = false

        install(ContentNegotiation) {
            json(
                Json {
                    ignoreUnknownKeys = true
                    explicitNulls = false
                },
            )
        }

        install(HttpTimeout) {
            requestTimeoutMillis = config.supabaseAuthTimeoutMs
            connectTimeoutMillis = config.supabaseAuthTimeoutMs
            socketTimeoutMillis = config.supabaseAuthTimeoutMs
        }

        defaultRequest {
            header(HttpHeaders.UserAgent, "PocketMind-Assistant/${config.serviceVersion}")
        }
    }

@Serializable
private data class SupabaseUserResponse(
    val id: String,
    @SerialName("role")
    val role: String? = null,
)

private fun String.hasJwtShape(): Boolean {
    if (length !in 32..16_384) return false
    val segments = split('.')
    return segments.size == 3 && segments.all { segment ->
        segment.isNotBlank() && segment.all { it.isLetterOrDigit() || it == '-' || it == '_' }
    }
}
