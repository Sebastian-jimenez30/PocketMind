package com.pocketmind.assistant.application

import com.pocketmind.assistant.agent.tools.AssistantReadToolRegistryFactory
import com.pocketmind.assistant.auth.AuthenticatedUser
import com.pocketmind.assistant.auth.SupabaseAccessToken
import com.pocketmind.assistant.config.AssistantConfig
import com.pocketmind.assistant.config.source
import com.pocketmind.assistant.config.validValues
import com.pocketmind.assistant.domain.turn.AssistantTurnHandler
import com.pocketmind.assistant.infrastructure.openai.KoogRuntimeFactory
import com.pocketmind.assistant.infrastructure.supabase.SupabaseAssistantMemoryRepository
import com.pocketmind.shared.assistant.AssistantChatMessage
import com.pocketmind.shared.assistant.AssistantTurnResponse
import com.pocketmind.shared.assistant.AssistantTurnStatus
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull

class ApplicationModuleTest {
    @Test
    fun `health is public and never exposes configuration secrets`() = testApplication {
        application {
            assistantModule(testDependencies())
        }

        val response = client.get("/health")
        val body = response.bodyAsText()

        assertEquals(HttpStatusCode.OK, response.status)
        assertContains(body, "\"status\":\"ok\"")
        assertContains(body, "\"koog\":\"configured\"")
        assertContains(body, "\"financialReadTools\":\"configured\"")
        assertContains(body, "\"assistantTextCore\":\"configured\"")
        assertFalse(body.contains("openai-test"))
        assertFalse(body.contains("publishable-test"))
        assertNotNull(response.headers[HttpHeaders.XRequestId])
    }

    @Test
    fun `protected route rejects missing token with typed error`() = testApplication {
        application {
            assistantModule(testDependencies())
        }

        val response = client.get("/v1/session")

        assertEquals(HttpStatusCode.Unauthorized, response.status)
        assertContains(response.bodyAsText(), "\"code\":\"UNAUTHORIZED\"")
    }

    @Test
    fun `protected route returns the verified Supabase identity`() = testApplication {
        application {
            assistantModule(testDependencies(validToken = TEST_JWT))
        }

        val response = client.get("/v1/session") {
            bearerAuth(TEST_JWT)
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertContains(response.bodyAsText(), "\"userId\":\"$TEST_USER_ID\"")
        assertContains(response.bodyAsText(), "\"role\":\"authenticated\"")
    }

    @Test
    fun `authenticated text turn returns the handler response`() = testApplication {
        val message = AssistantChatMessage(
            id = "22222222-2222-4222-8222-222222222222",
            turnId = "33333333-3333-4333-8333-333333333333",
            role = "assistant",
            content = "Necesito el producto.",
            createdAt = "2026-07-28T03:00:00Z",
        )
        application {
            assistantModule(
                testDependencies(
                    validToken = TEST_JWT,
                    turnHandler = { _, request ->
                        AssistantTurnResponse(
                            conversationId =
                                "11111111-1111-4111-8111-111111111111",
                            turnId = message.turnId,
                            status = AssistantTurnStatus.CLARIFICATION,
                            reply = message.content,
                            userMessage = message.copy(
                                id = request.clientMessageId,
                                role = "user",
                                content = request.content,
                            ),
                            assistantMessage = message,
                        )
                    },
                ),
            )
        }

        val response = client.post("/v1/assistant/turn") {
            bearerAuth(TEST_JWT)
            contentType(ContentType.Application.Json)
            setBody(
                """
                    {
                      "clientMessageId":"44444444-4444-4444-8444-444444444444",
                      "content":"Recibí dinero"
                    }
                """.trimIndent(),
            )
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertContains(response.bodyAsText(), "\"status\":\"clarification\"")
        assertContains(response.bodyAsText(), "Necesito el producto")
    }

    @Test
    fun `protected route rejects an invalid token`() = testApplication {
        application {
            assistantModule(testDependencies(validToken = TEST_JWT))
        }

        val response = client.get("/v1/session") {
            bearerAuth("invalid.token.value")
        }

        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `authenticated user can recover only conversations returned by RLS`() = testApplication {
        val memoryEngine = MockEngine {
            respond(
                content = """
                    [{
                      "id":"11111111-1111-4111-8111-111111111111",
                      "user_id":"aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa",
                      "title":"Julio",
                      "status":"active",
                      "locale":"es-CO",
                      "prompt_version":"assistant-v1",
                      "tool_schema_version":1,
                      "last_message_at":null,
                      "created_at":"2026-07-28T03:00:00Z",
                      "updated_at":"2026-07-28T03:00:00Z"
                    }]
                """.trimIndent(),
                status = HttpStatusCode.OK,
                headers = headersOf(
                    HttpHeaders.ContentType,
                    ContentType.Application.Json.toString(),
                ),
            )
        }
        application {
            assistantModule(
                testDependencies(
                    validToken = TEST_JWT,
                    memoryEngine = memoryEngine,
                ),
            )
        }

        val response = client.get("/v1/assistant/conversations") {
            bearerAuth(TEST_JWT)
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertContains(
            response.bodyAsText(),
            "\"id\":\"11111111-1111-4111-8111-111111111111\"",
        )
        assertContains(response.bodyAsText(), "\"title\":\"Julio\"")
    }
}

private fun testDependencies(
    validToken: String? = null,
    memoryEngine: MockEngine = MockEngine {
        error("Assistant memory was not expected in this test.")
    },
    turnHandler: AssistantTurnHandler = AssistantTurnHandler { _, _ ->
        error("Assistant turn was not expected in this test.")
    },
): AppDependencies {
    val config = AssistantConfig.load(source(validValues()))
    val memoryClient = HttpClient(memoryEngine) {
        install(ContentNegotiation) {
            json(
                Json {
                    ignoreUnknownKeys = true
                    explicitNulls = false
                },
            )
        }
    }
    val memoryRepository = SupabaseAssistantMemoryRepository(memoryClient, config)
    return AppDependencies(
        config = config,
        tokenVerifier = { token ->
            if (token == validToken) {
                AuthenticatedUser(
                    userId = TEST_USER_ID,
                    role = "authenticated",
                    accessToken = SupabaseAccessToken(token),
                )
            } else {
                null
            }
        },
        koogRuntimeFactory = KoogRuntimeFactory(config),
        memoryRepository = memoryRepository,
        readToolRegistryFactory = AssistantReadToolRegistryFactory(),
        turnHandler = turnHandler,
    )
}

private const val TEST_JWT = "header.payload.signature"
private const val TEST_USER_ID = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa"
