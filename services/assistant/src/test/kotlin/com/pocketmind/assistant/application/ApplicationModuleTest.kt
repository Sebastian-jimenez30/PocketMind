package com.pocketmind.assistant.application

import com.pocketmind.assistant.auth.AuthenticatedUser
import com.pocketmind.assistant.config.AssistantConfig
import com.pocketmind.assistant.config.source
import com.pocketmind.assistant.config.validValues
import com.pocketmind.assistant.infrastructure.openai.KoogRuntimeFactory
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
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
        assertContains(response.bodyAsText(), "\"userId\":\"user-123\"")
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
}

private fun testDependencies(validToken: String? = null): AppDependencies {
    val config = AssistantConfig.load(source(validValues()))
    return AppDependencies(
        config = config,
        tokenVerifier = { token ->
            if (token == validToken) {
                AuthenticatedUser(userId = "user-123", role = "authenticated")
            } else {
                null
            }
        },
        koogRuntimeFactory = KoogRuntimeFactory(config),
    )
}

private const val TEST_JWT = "header.payload.signature"
