package com.pocketmind.assistant.application

import com.pocketmind.assistant.api.ApiError
import com.pocketmind.assistant.api.ApiErrorResponse
import com.pocketmind.assistant.api.ApiException
import com.pocketmind.assistant.api.HealthResponse
import com.pocketmind.assistant.api.SessionResponse
import com.pocketmind.assistant.auth.AuthenticatedUser
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.bearer
import io.ktor.server.auth.principal
import io.ktor.server.plugins.callid.CallId
import io.ktor.server.plugins.callid.callId
import io.ktor.server.plugins.callid.callIdMdc
import io.ktor.server.plugins.callid.generate
import io.ktor.server.plugins.callid.replyToHeader
import io.ktor.server.plugins.callid.retrieveFromHeader
import io.ktor.server.plugins.callid.verify
import io.ktor.server.plugins.callloging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.httpMethod
import io.ktor.server.request.path
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import org.slf4j.event.Level

fun Application.assistantModule(dependencies: AppDependencies) {
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
        exception<Throwable> { call, cause ->
            log.error(
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
                    ),
                ),
            )
        }

        authenticate(AUTH_PROVIDER) {
            get("/v1/session") {
                val user = call.principal<AuthenticatedUser>()
                    ?: throw ApiException(
                        status = HttpStatusCode.Unauthorized,
                        code = "UNAUTHORIZED",
                        publicMessage = "La sesión no es válida o expiró.",
                    )
                call.respond(SessionResponse(userId = user.userId, role = user.role))
            }
        }
    }
}

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
