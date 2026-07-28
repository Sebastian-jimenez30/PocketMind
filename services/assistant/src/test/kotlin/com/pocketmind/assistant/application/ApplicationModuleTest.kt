package com.pocketmind.assistant.application

import com.pocketmind.assistant.agent.tools.AssistantReadToolRegistryFactory
import com.pocketmind.assistant.auth.AuthenticatedUser
import com.pocketmind.assistant.auth.SupabaseAccessToken
import com.pocketmind.assistant.config.AssistantConfig
import com.pocketmind.assistant.config.source
import com.pocketmind.assistant.config.validValues
import com.pocketmind.assistant.domain.finance.FinancialContextRepository
import com.pocketmind.assistant.infrastructure.openai.KoogRuntimeFactory
import com.pocketmind.assistant.infrastructure.supabase.SupabaseAssistantMemoryRepository
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
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
        readToolRegistryFactory = AssistantReadToolRegistryFactory(
            contextRepository = FinancialContextRepository {
                error("Financial context was not expected in this test.")
            },
            memoryRepository = memoryRepository,
        ),
    )
}

private const val TEST_JWT = "header.payload.signature"
private const val TEST_USER_ID = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa"
