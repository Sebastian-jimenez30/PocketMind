package com.pocketmind.assistant.infrastructure.supabase

import com.pocketmind.assistant.config.AssistantConfig
import com.pocketmind.assistant.config.source
import com.pocketmind.assistant.config.validValues
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RemoteSupabaseTokenVerifierTest {
    @Test
    fun `maps an authenticated Supabase user without exposing profile data`() = runTest {
        val config = AssistantConfig.load(source(validValues()))
        val client = HttpClient(
            MockEngine { request ->
                assertEquals(
                    "Bearer $TEST_JWT",
                    request.headers[HttpHeaders.Authorization],
                )
                assertEquals(
                    "publishable-test",
                    request.headers["apikey"],
                )
                respond(
                    content = """{"id":"user-123","role":"authenticated","email":"private@example.com"}""",
                    status = HttpStatusCode.OK,
                    headers = headersOf(
                        HttpHeaders.ContentType,
                        ContentType.Application.Json.toString(),
                    ),
                )
            },
        ) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }

        val user = RemoteSupabaseTokenVerifier(client, config).verify(TEST_JWT)

        assertEquals("user-123", user?.userId)
        assertEquals("authenticated", user?.role)
        client.close()
    }

    @Test
    fun `rejects malformed token without contacting Supabase`() = runTest {
        var requestWasMade = false
        val client = HttpClient(
            MockEngine {
                requestWasMade = true
                error("Supabase must not be contacted")
            },
        )
        val config = AssistantConfig.load(source(validValues()))

        val user = RemoteSupabaseTokenVerifier(client, config).verify("not-a-jwt")

        assertNull(user)
        assertTrue(!requestWasMade)
        client.close()
    }
}

private const val TEST_JWT = "headerheader.payloadpayload.signaturesignature"
