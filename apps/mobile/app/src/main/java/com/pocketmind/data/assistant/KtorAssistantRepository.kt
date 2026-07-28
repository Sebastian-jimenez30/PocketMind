package com.pocketmind.data.assistant

import com.pocketmind.BuildConfig
import com.pocketmind.shared.assistant.AssistantCommandDraft
import com.pocketmind.shared.assistant.AssistantDraftCompletionRequest
import com.pocketmind.shared.assistant.AssistantDraftFailureRequest
import com.pocketmind.shared.assistant.AssistantDraftState
import com.pocketmind.shared.assistant.AssistantDraftRevisionRequest
import com.pocketmind.shared.assistant.AssistantDraftTransitionRequest
import com.pocketmind.shared.assistant.AssistantTurnRequest
import com.pocketmind.shared.assistant.AssistantTurnResponse
import com.pocketmind.shared.domain.command.FinancialCommandResult
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.ResponseException
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject

class KtorAssistantRepository @Inject constructor(
    private val client: HttpClient,
    private val supabase: SupabaseClient,
) : AssistantRepository {
    override suspend fun sendTurn(
        request: AssistantTurnRequest,
    ): AssistantTurnResponse = retryTransientRequest {
        authenticatedRequest { baseUrl, accessToken ->
            client.post("$baseUrl/v1/assistant/turn") {
                bearerAuth(accessToken)
                contentType(ContentType.Application.Json)
                setBody(request)
            }.body()
        }
    }

    override suspend fun getDraft(draftId: String): AssistantCommandDraft =
        authenticatedRequest { baseUrl, accessToken ->
            client.get("$baseUrl/v1/assistant/drafts/$draftId") {
                bearerAuth(accessToken)
            }.body()
        }

    override suspend fun confirmDraft(
        draftId: String,
        expectedVersion: Long,
    ): AssistantCommandDraft = authenticatedRequest { baseUrl, accessToken ->
        client.post("$baseUrl/v1/assistant/drafts/$draftId/confirm") {
            bearerAuth(accessToken)
            contentType(ContentType.Application.Json)
            setBody(AssistantDraftTransitionRequest(expectedVersion))
        }.body()
    }

    override suspend fun reviseDraft(
        draftId: String,
        expectedVersion: Long,
        commandPayload: kotlinx.serialization.json.JsonObject,
    ): AssistantCommandDraft = try {
        authenticatedRequest { baseUrl, accessToken ->
            client.post("$baseUrl/v1/assistant/drafts/$draftId/revise") {
                bearerAuth(accessToken)
                contentType(ContentType.Application.Json)
                setBody(AssistantDraftRevisionRequest(expectedVersion, commandPayload))
            }.body()
        }
    } catch (failure: AssistantRequestException) {
        val status = (failure.cause as? ResponseException)?.response?.status
        if (status == HttpStatusCode.NotFound) {
            throw AssistantRequestException(
                publicMessage = "Servicio sin actualizar.",
                cause = failure,
            )
        }
        throw failure
    }

    override suspend fun cancelDraft(
        draftId: String,
        expectedVersion: Long,
        expectedState: AssistantDraftState,
    ): AssistantCommandDraft = authenticatedRequest { baseUrl, accessToken ->
        client.post("$baseUrl/v1/assistant/drafts/$draftId/cancel") {
            bearerAuth(accessToken)
            contentType(ContentType.Application.Json)
            setBody(
                AssistantDraftTransitionRequest(
                    expectedVersion = expectedVersion,
                    expectedState = expectedState,
                ),
            )
        }.body()
    }

    override suspend fun completeDraft(
        draftId: String,
        expectedVersion: Long,
        result: FinancialCommandResult,
    ): AssistantCommandDraft = authenticatedRequest { baseUrl, accessToken ->
        client.post("$baseUrl/v1/assistant/drafts/$draftId/complete") {
            bearerAuth(accessToken)
            contentType(ContentType.Application.Json)
            setBody(
                AssistantDraftCompletionRequest(
                    expectedVersion = expectedVersion,
                    executionResult = result.toJsonObject(),
                ),
            )
        }.body()
    }

    override suspend fun failDraft(
        draftId: String,
        expectedVersion: Long,
        result: FinancialCommandResult,
        errorCode: String,
    ): AssistantCommandDraft = authenticatedRequest { baseUrl, accessToken ->
        client.post("$baseUrl/v1/assistant/drafts/$draftId/fail") {
            bearerAuth(accessToken)
            contentType(ContentType.Application.Json)
            setBody(
                AssistantDraftFailureRequest(
                    expectedVersion = expectedVersion,
                    executionResult = result.toJsonObject(),
                    errorCode = errorCode,
                ),
            )
        }.body()
    }

    private suspend inline fun <T> authenticatedRequest(
        crossinline request: suspend (baseUrl: String, accessToken: String) -> T,
    ): T {
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
            request(baseUrl, accessToken)
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

    private suspend fun <T> retryTransientRequest(
        request: suspend () -> T,
    ): T {
        repeat(MAX_SEND_ATTEMPTS) { attempt ->
            try {
                return request()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                val isLastAttempt = attempt == MAX_SEND_ATTEMPTS - 1
                if (isLastAttempt || !failure.isTransientAssistantFailure()) {
                    throw failure
                }
                delay(SEND_RETRY_DELAY_MILLIS)
            }
        }
        error("Unreachable retry state.")
    }

    private fun Throwable.isTransientAssistantFailure(): Boolean {
        val underlying = (this as? AssistantRequestException)?.cause ?: this
        val status = (underlying as? ResponseException)?.response?.status
        return status == HttpStatusCode.TooManyRequests ||
            status?.value?.let { it in 500..599 } == true ||
            underlying is IOException ||
            underlying is HttpRequestTimeoutException
    }

    private fun FinancialCommandResult.toJsonObject() =
        resultJson.encodeToJsonElement(FinancialCommandResult.serializer(), this).jsonObject

    private companion object {
        const val MAX_SEND_ATTEMPTS = 3
        const val SEND_RETRY_DELAY_MILLIS = 3_000L
        val resultJson = Json {
            encodeDefaults = true
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
