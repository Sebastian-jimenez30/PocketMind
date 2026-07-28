package com.pocketmind.data.assistant

import com.pocketmind.BuildConfig
import com.pocketmind.shared.assistant.AssistantTurnRequest
import com.pocketmind.shared.assistant.AssistantTurnResponse
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.ResponseException
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import javax.inject.Inject
import kotlinx.serialization.Serializable

class KtorAssistantRepository @Inject constructor(
    private val client: HttpClient,
    private val supabase: SupabaseClient,
) : AssistantRepository {
    override suspend fun sendTurn(
        request: AssistantTurnRequest,
    ): AssistantTurnResponse {
        val baseUrl = BuildConfig.ASSISTANT_BASE_URL.trim().trimEnd('/')
        check(baseUrl.isNotEmpty()) {
            "El asistente todavía no está configurado en este dispositivo."
        }
        supabase.auth.awaitInitialization()
        val accessToken = supabase.auth.currentAccessTokenOrNull()
        check(!accessToken.isNullOrBlank()) {
            "Tu sesión expiró. Inicia sesión nuevamente."
        }
        return try {
            client.post("$baseUrl/v1/assistant/turn") {
                bearerAuth(accessToken)
                contentType(ContentType.Application.Json)
                setBody(request)
            }.body()
        } catch (failure: ResponseException) {
            val remoteError = try {
                failure.response.body<AssistantErrorEnvelope>()
            } catch (_: Exception) {
                null
            }
            throw AssistantRequestException(
                publicMessage = remoteError?.error?.message
                    ?: "El asistente no pudo responder en este momento. Inténtalo nuevamente.",
                cause = failure,
            )
        }
    }
}

@Serializable
private data class AssistantErrorEnvelope(
    val error: AssistantErrorBody,
)

@Serializable
private data class AssistantErrorBody(
    val code: String,
    val message: String,
    val requestId: String,
)
