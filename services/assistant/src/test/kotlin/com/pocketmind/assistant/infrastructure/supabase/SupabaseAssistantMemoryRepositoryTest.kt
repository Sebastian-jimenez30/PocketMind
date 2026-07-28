package com.pocketmind.assistant.infrastructure.supabase

import com.pocketmind.assistant.auth.AuthenticatedUser
import com.pocketmind.assistant.auth.SupabaseAccessToken
import com.pocketmind.assistant.config.AssistantConfig
import com.pocketmind.assistant.config.source
import com.pocketmind.assistant.config.validValues
import com.pocketmind.assistant.domain.memory.AssistantMemoryConflictException
import com.pocketmind.assistant.domain.memory.AssistantMemoryRemoteException
import com.pocketmind.assistant.domain.memory.DraftState
import com.pocketmind.assistant.domain.memory.DraftTransition
import com.pocketmind.assistant.domain.memory.NewConversation
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class SupabaseAssistantMemoryRepositoryTest {
    @Test
    fun `conversation request carries caller JWT and maps response`() = runTest {
        val client = testClient(
            MockEngine { request ->
                assertEquals(HttpMethod.Post, request.method)
                assertEquals(
                    "/rest/v1/assistant_conversations",
                    request.url.encodedPath,
                )
                assertEquals("Bearer access-token", request.headers[HttpHeaders.Authorization])
                assertEquals("publishable-test", request.headers["apikey"])
                respondJson(
                    """
                    [{
                      "id":"11111111-1111-4111-8111-111111111111",
                      "user_id":"aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa",
                      "title":"Mis finanzas",
                      "status":"active",
                      "locale":"es-CO",
                      "prompt_version":"assistant-v1",
                      "tool_schema_version":1,
                      "last_message_at":null,
                      "created_at":"2026-07-28T03:00:00Z",
                      "updated_at":"2026-07-28T03:00:00Z"
                    }]
                    """.trimIndent(),
                    HttpStatusCode.Created,
                )
            },
        )
        val repository = SupabaseAssistantMemoryRepository(client, testConfig())

        val conversation = repository.createConversation(
            session = testSession(),
            value = NewConversation(
                id = CONVERSATION_ID,
                title = "Mis finanzas",
                locale = "es-CO",
                promptVersion = "assistant-v1",
                toolSchemaVersion = 1,
            ),
        )

        assertEquals(CONVERSATION_ID, conversation.id)
        assertEquals(USER_ID, conversation.userId)
        client.close()
    }

    @Test
    fun `empty optimistic update is reported as conflict`() = runTest {
        val client = testClient(
            MockEngine {
                respondJson("[]", HttpStatusCode.OK)
            },
        )
        val repository = SupabaseAssistantMemoryRepository(client, testConfig())

        assertFailsWith<AssistantMemoryConflictException> {
            repository.transitionDraft(
                session = testSession(),
                draftId = DRAFT_ID,
                transition = DraftTransition(
                    expectedState = DraftState.PROPOSED,
                    nextState = DraftState.CONFIRMED,
                    expectedVersion = 1,
                ),
            )
        }
        client.close()
    }

    @Test
    fun `remote errors never include provider response body`() = runTest {
        val client = testClient(
            MockEngine {
                respondJson(
                    """{"message":"access-token and private financial content"}""",
                    HttpStatusCode.BadRequest,
                )
            },
        )
        val repository = SupabaseAssistantMemoryRepository(client, testConfig())

        val error = assertFailsWith<AssistantMemoryRemoteException> {
            repository.createConversation(
                session = testSession(),
                value = NewConversation(
                    id = CONVERSATION_ID,
                    title = null,
                    locale = "es-CO",
                    promptVersion = "assistant-v1",
                    toolSchemaVersion = 1,
                ),
            )
        }

        assertFalse(error.message.orEmpty().contains("access-token"))
        assertFalse(error.message.orEmpty().contains("financial content"))
        client.close()
    }

    @Test
    fun `canonical command hash does not depend on object key order`() {
        val first = buildJsonObject {
            put("amount", 35000)
            put("merchant", "Restaurante")
        }
        val second = buildJsonObject {
            put("merchant", "Restaurante")
            put("amount", 35000)
        }

        assertEquals(CanonicalJson.sha256(first), CanonicalJson.sha256(second))
        assertEquals(64, CanonicalJson.sha256(first).length)
    }
}

private fun testClient(engine: MockEngine): HttpClient = HttpClient(engine) {
    install(ContentNegotiation) {
        json(
            Json {
                ignoreUnknownKeys = true
                explicitNulls = false
            },
        )
    }
}

private fun io.ktor.client.engine.mock.MockRequestHandleScope.respondJson(
    content: String,
    status: HttpStatusCode,
) = respond(
    content = content,
    status = status,
    headers = headersOf(
        HttpHeaders.ContentType,
        ContentType.Application.Json.toString(),
    ),
)

private fun testConfig(): AssistantConfig = AssistantConfig.load(source(validValues()))

private fun testSession(): AuthenticatedUser = AuthenticatedUser(
    userId = USER_ID,
    role = "authenticated",
    accessToken = SupabaseAccessToken("access-token"),
)

private const val USER_ID = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa"
private const val CONVERSATION_ID = "11111111-1111-4111-8111-111111111111"
private const val DRAFT_ID = "22222222-2222-4222-8222-222222222222"
