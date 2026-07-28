package com.pocketmind.assistant.application

import com.pocketmind.assistant.api.ApiError
import com.pocketmind.assistant.api.ApiErrorResponse
import com.pocketmind.assistant.api.ApiException
import com.pocketmind.assistant.api.CompleteDraftRequest
import com.pocketmind.assistant.api.ConversationDetailResponse
import com.pocketmind.assistant.api.ConversationResponse
import com.pocketmind.assistant.api.CreateConversationRequest
import com.pocketmind.assistant.api.DraftResponse
import com.pocketmind.assistant.api.DraftTransitionRequest
import com.pocketmind.assistant.api.HealthResponse
import com.pocketmind.assistant.api.MessageResponse
import com.pocketmind.assistant.api.SessionResponse
import com.pocketmind.assistant.auth.AuthenticatedUser
import com.pocketmind.assistant.domain.memory.AssistantCommandDraft
import com.pocketmind.assistant.domain.memory.AssistantConversation
import com.pocketmind.assistant.domain.memory.AssistantMemoryConflictException
import com.pocketmind.assistant.domain.memory.AssistantMemoryRemoteException
import com.pocketmind.assistant.domain.memory.AssistantMessage
import com.pocketmind.assistant.domain.memory.DraftState
import com.pocketmind.assistant.domain.memory.DraftTransition
import com.pocketmind.assistant.domain.memory.NewConversation
import com.pocketmind.assistant.domain.finance.FinancialContextException
import com.pocketmind.assistant.domain.finance.FinancialContextProblem
import com.pocketmind.assistant.domain.finance.FinancialContextRemoteException
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.application.log
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.bearer
import io.ktor.server.auth.principal
import io.ktor.server.plugins.ContentTransformationException
import io.ktor.server.plugins.callid.CallId
import io.ktor.server.plugins.callid.callId
import io.ktor.server.plugins.callid.callIdMdc
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.httpMethod
import io.ktor.server.request.path
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import org.slf4j.event.Level

fun Application.assistantModule(dependencies: AppDependencies) {
    val applicationLog = log

    install(CallId) {
        retrieveFromHeader(HttpHeaders.XRequestId)
        generate { java.util.UUID.randomUUID().toString() }
        verify { requestId ->
            requestId.length in 8..128 &&
                requestId.all { it.isLetterOrDigit() || it == '-' || it == '_' }
        }
        replyToHeader(HttpHeaders.XRequestId)
    }

    install(CallLogging) {
        level = Level.INFO
        callIdMdc("requestId")
        format { call ->
            val status = call.response.status()?.value ?: 0
            "request method=${call.request.httpMethod.value} " +
                "path=${call.request.path()} status=$status"
        }
    }

    install(ContentNegotiation) {
        json(
            Json {
                ignoreUnknownKeys = true
                explicitNulls = false
            },
        )
    }

    install(StatusPages) {
        exception<ApiException> { call, cause ->
            call.respond(
                cause.status,
                call.errorResponse(cause.code, cause.publicMessage),
            )
        }
        exception<CancellationException> { _, cause ->
            throw cause
        }
        exception<AssistantMemoryConflictException> { call, _ ->
            call.respond(
                HttpStatusCode.Conflict,
                call.errorResponse(
                    code = "MEMORY_CONFLICT",
                    message = "La conversación cambió. Actualiza e inténtalo nuevamente.",
                ),
            )
        }
        exception<AssistantMemoryRemoteException> { call, cause ->
            val unauthorized = cause.statusCode == HttpStatusCode.Unauthorized.value ||
                cause.statusCode == HttpStatusCode.Forbidden.value
            call.respond(
                if (unauthorized) {
                    HttpStatusCode.Unauthorized
                } else {
                    HttpStatusCode.ServiceUnavailable
                },
                call.errorResponse(
                    code = if (unauthorized) "UNAUTHORIZED" else "MEMORY_UNAVAILABLE",
                    message = if (unauthorized) {
                        "La sesión no es válida o expiró."
                    } else {
                        "La memoria del asistente no está disponible temporalmente."
                    },
                ),
            )
        }
        exception<FinancialContextException> { call, cause ->
            val unsupported =
                cause.problem == FinancialContextProblem.UNSUPPORTED_SCHEMA
            call.respond(
                HttpStatusCode.UnprocessableEntity,
                call.errorResponse(
                    code = if (unsupported) {
                        "FINANCIAL_SCHEMA_UNSUPPORTED"
                    } else {
                        "FINANCIAL_DATA_INVALID"
                    },
                    message = if (unsupported) {
                        "Los datos financieros requieren una versión más reciente."
                    } else {
                        "Los datos financieros sincronizados no son consistentes."
                    },
                ),
            )
        }
        exception<FinancialContextRemoteException> { call, cause ->
            val unauthorized =
                cause.statusCode == HttpStatusCode.Unauthorized.value ||
                    cause.statusCode == HttpStatusCode.Forbidden.value
            call.respond(
                if (unauthorized) {
                    HttpStatusCode.Unauthorized
                } else {
                    HttpStatusCode.ServiceUnavailable
                },
                call.errorResponse(
                    code = if (unauthorized) {
                        "UNAUTHORIZED"
                    } else {
                        "FINANCIAL_CONTEXT_UNAVAILABLE"
                    },
                    message = if (unauthorized) {
                        "La sesión no es válida o expiró."
                    } else {
                        "El contexto financiero no está disponible temporalmente."
                    },
                ),
            )
        }
        exception<IllegalArgumentException> { call, _ ->
            call.respond(
                HttpStatusCode.BadRequest,
                call.errorResponse(
                    code = "INVALID_REQUEST",
                    message = "La solicitud contiene datos inválidos.",
                ),
            )
        }
        exception<ContentTransformationException> { call, _ ->
            call.respond(
                HttpStatusCode.BadRequest,
                call.errorResponse(
                    code = "INVALID_REQUEST",
                    message = "La solicitud contiene datos inválidos.",
                ),
            )
        }
        exception<Throwable> { call, cause ->
            applicationLog.error(
                "Unhandled request failure requestId={} type={}",
                call.callId,
                cause::class.simpleName ?: "Unknown",
            )
            call.respond(
                HttpStatusCode.InternalServerError,
                call.errorResponse(
                    code = "INTERNAL_ERROR",
                    message = "No fue posible procesar la solicitud.",
                ),
            )
        }
        status(HttpStatusCode.Unauthorized) { call, status ->
            call.respond(
                status,
                call.errorResponse(
                    code = "UNAUTHORIZED",
                    message = "La sesión no es válida o expiró.",
                ),
            )
        }
        status(HttpStatusCode.NotFound) { call, status ->
            call.respond(
                status,
                call.errorResponse(
                    code = "NOT_FOUND",
                    message = "El recurso solicitado no existe.",
                ),
            )
        }
    }

    install(Authentication) {
        bearer(AUTH_PROVIDER) {
            realm = "pocketmind-assistant"
            authenticate { credential ->
                dependencies.tokenVerifier.verify(credential.token)
            }
        }
    }

    routing {
        get("/health") {
            call.respond(
                HealthResponse(
                    status = "ok",
                    service = SERVICE_NAME,
                    version = dependencies.config.serviceVersion,
                    environment = dependencies.config.environment.name.lowercase(),
                    dependencies = mapOf(
                        "koog" to "configured",
                        "supabaseAuth" to "configured",
                        "supabaseMemory" to "configured",
                        "financialReadTools" to "configured",
                    ),
                ),
            )
        }

        authenticate(AUTH_PROVIDER) {
            get("/v1/session") {
                val user = call.authenticatedUser()
                call.respond(SessionResponse(userId = user.userId, role = user.role))
            }

            post("/v1/assistant/conversations") {
                val user = call.authenticatedUser()
                val request = call.receive<CreateConversationRequest>()
                request.requireValid()
                val conversation = dependencies.memoryRepository.createConversation(
                    session = user,
                    value = NewConversation(
                        id = request.id,
                        title = request.title?.trim(),
                        locale = request.locale.trim(),
                        promptVersion = dependencies.config.promptVersion,
                        toolSchemaVersion = dependencies.config.toolSchemaVersion,
                    ),
                )
                call.respond(HttpStatusCode.Created, conversation.toResponse())
            }

            get("/v1/assistant/conversations") {
                val user = call.authenticatedUser()
                val conversations = dependencies.memoryRepository
                    .listConversations(user)
                    .map(AssistantConversation::toResponse)
                call.respond(conversations)
            }

            get("/v1/assistant/conversations/{conversationId}") {
                val user = call.authenticatedUser()
                val conversationId = call.parameters.requireUuid("conversationId")
                val conversation = dependencies.memoryRepository
                    .getConversation(user, conversationId)
                    ?: throw notFound()
                val messages = dependencies.memoryRepository
                    .listMessages(user, conversationId)
                    .map(AssistantMessage::toResponse)
                call.respond(
                    ConversationDetailResponse(
                        conversation = conversation.toResponse(),
                        messages = messages,
                    ),
                )
            }

            delete("/v1/assistant/conversations/{conversationId}") {
                val user = call.authenticatedUser()
                val conversationId = call.parameters.requireUuid("conversationId")
                val deleted = dependencies.memoryRepository
                    .deleteConversation(user, conversationId)
                if (!deleted) throw notFound()
                call.respond(HttpStatusCode.NoContent)
            }

            post("/v1/assistant/drafts/{draftId}/confirm") {
                val user = call.authenticatedUser()
                val draftId = call.parameters.requireUuid("draftId")
                val request = call.receive<DraftTransitionRequest>()
                request.requirePositiveVersion()
                val draft = dependencies.memoryRepository.transitionDraft(
                    session = user,
                    draftId = draftId,
                    transition = DraftTransition(
                        expectedState = DraftState.PROPOSED,
                        nextState = DraftState.CONFIRMED,
                        expectedVersion = request.expectedVersion,
                    ),
                )
                call.respond(draft.toResponse())
            }

            post("/v1/assistant/drafts/{draftId}/cancel") {
                val user = call.authenticatedUser()
                val draftId = call.parameters.requireUuid("draftId")
                val request = call.receive<DraftTransitionRequest>()
                request.requirePositiveVersion()
                val expectedState = request.expectedState
                    ?.let(::draftStateFromWire)
                    ?.takeIf { it == DraftState.PROPOSED || it == DraftState.CONFIRMED }
                    ?: throw invalidRequest()
                val draft = dependencies.memoryRepository.transitionDraft(
                    session = user,
                    draftId = draftId,
                    transition = DraftTransition(
                        expectedState = expectedState,
                        nextState = DraftState.CANCELLED,
                        expectedVersion = request.expectedVersion,
                    ),
                )
                call.respond(draft.toResponse())
            }

            post("/v1/assistant/drafts/{draftId}/complete") {
                val user = call.authenticatedUser()
                val draftId = call.parameters.requireUuid("draftId")
                val request = call.receive<CompleteDraftRequest>()
                require(request.expectedVersion > 0)
                val draft = dependencies.memoryRepository.transitionDraft(
                    session = user,
                    draftId = draftId,
                    transition = DraftTransition(
                        expectedState = DraftState.CONFIRMED,
                        nextState = DraftState.COMPLETED,
                        expectedVersion = request.expectedVersion,
                        executionResult = request.executionResult,
                    ),
                )
                call.respond(draft.toResponse())
            }
        }
    }
}

private fun io.ktor.server.application.ApplicationCall.authenticatedUser(): AuthenticatedUser =
    principal<AuthenticatedUser>()
        ?: throw ApiException(
            status = HttpStatusCode.Unauthorized,
            code = "UNAUTHORIZED",
            publicMessage = "La sesión no es válida o expiró.",
        )

private fun CreateConversationRequest.requireValid() {
    require(runCatching { java.util.UUID.fromString(id) }.isSuccess)
    require(title == null || title.trim().length in 1..120)
    require(locale.trim().length in 2..20)
}

private fun DraftTransitionRequest.requirePositiveVersion() {
    require(expectedVersion > 0)
}

private fun io.ktor.http.Parameters.requireUuid(name: String): String {
    val value = this[name] ?: throw invalidRequest()
    require(runCatching { java.util.UUID.fromString(value) }.isSuccess)
    return value
}

private fun draftStateFromWire(value: String): DraftState =
    DraftState.entries.firstOrNull { it.wireValue == value }
        ?: throw invalidRequest()

private fun AssistantConversation.toResponse(): ConversationResponse =
    ConversationResponse(
        id = id,
        title = title,
        status = status.wireValue,
        locale = locale,
        promptVersion = promptVersion,
        toolSchemaVersion = toolSchemaVersion,
        lastMessageAt = lastMessageAt?.toString(),
        createdAt = createdAt.toString(),
        updatedAt = updatedAt.toString(),
    )

private fun AssistantMessage.toResponse(): MessageResponse = MessageResponse(
    id = id,
    turnId = turnId,
    role = role.wireValue,
    content = content,
    inputModality = inputModality.wireValue,
    promptVersion = promptVersion,
    modelId = modelId,
    createdAt = createdAt.toString(),
)

private fun AssistantCommandDraft.toResponse(): DraftResponse = DraftResponse(
    id = id,
    conversationId = conversationId,
    commandType = commandType,
    commandPayload = commandPayload,
    commandSchemaVersion = commandSchemaVersion,
    state = state.wireValue,
    idempotencyKey = idempotencyKey,
    payloadHash = payloadHash,
    financialStateVersion = financialStateVersion,
    executionResult = executionResult,
    errorCode = errorCode,
    version = version,
    expiresAt = expiresAt.toString(),
    createdAt = createdAt.toString(),
    updatedAt = updatedAt.toString(),
)

private fun notFound(): ApiException = ApiException(
    status = HttpStatusCode.NotFound,
    code = "NOT_FOUND",
    publicMessage = "El recurso solicitado no existe.",
)

private fun invalidRequest(): ApiException = ApiException(
    status = HttpStatusCode.BadRequest,
    code = "INVALID_REQUEST",
    publicMessage = "La solicitud contiene datos inválidos.",
)

private fun io.ktor.server.application.ApplicationCall.errorResponse(
    code: String,
    message: String,
): ApiErrorResponse = ApiErrorResponse(
    error = ApiError(
        code = code,
        message = message,
        requestId = callId ?: "unavailable",
    ),
)

private const val AUTH_PROVIDER = "supabase"
private const val SERVICE_NAME = "pocketmind-assistant"
