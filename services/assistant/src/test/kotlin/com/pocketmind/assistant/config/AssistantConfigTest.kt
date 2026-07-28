package com.pocketmind.assistant.config

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AssistantConfigTest {
    @Test
    fun `loads a complete local configuration`() {
        val config = AssistantConfig.load(source(validValues()))

        assertEquals(AssistantEnvironment.LOCAL, config.environment)
        assertEquals(8080, config.port)
        assertEquals("https://pocketmind.supabase.co", config.supabaseUrl)
        assertEquals("gpt-4o-mini", config.primaryModel)
        assertEquals("assistant-v1", config.promptVersion)
        assertEquals(1, config.toolSchemaVersion)
        assertEquals(SecretValue.REDACTED, config.openAiApiKey.toString())
    }

    @Test
    fun `reports variable names without exposing secret values`() {
        val values = validValues().toMutableMap().apply {
            remove("SUPABASE_URL")
            this["OPENAI_API_KEY"] = ""
        }

        val error = assertFailsWith<ConfigurationException> {
            AssistantConfig.load(source(values))
        }

        assertTrue("SUPABASE_URL" in error.invalidKeys)
        assertTrue("OPENAI_API_KEY" in error.invalidKeys)
        assertFalse(error.message.orEmpty().contains("publishable-test"))
    }

    @Test
    fun `production requires https for Supabase`() {
        val values = validValues().toMutableMap().apply {
            this["APP_ENV"] = "production"
            this["SUPABASE_URL"] = "http://pocketmind.supabase.co"
        }

        val error = assertFailsWith<ConfigurationException> {
            AssistantConfig.load(source(values))
        }

        assertEquals(setOf("SUPABASE_URL"), error.invalidKeys)
    }

    @Test
    fun `rejects invalid port and timeout`() {
        val values = validValues().toMutableMap().apply {
            this["PORT"] = "70000"
            this["SUPABASE_AUTH_TIMEOUT_MS"] = "100"
        }

        val error = assertFailsWith<ConfigurationException> {
            AssistantConfig.load(source(values))
        }

        assertEquals(
            setOf("PORT", "SUPABASE_AUTH_TIMEOUT_MS"),
            error.invalidKeys,
        )
    }
}

internal fun validValues(): Map<String, String> = mapOf(
    "APP_ENV" to "local",
    "PORT" to "8080",
    "SERVICE_VERSION" to "test",
    "SUPABASE_URL" to "https://pocketmind.supabase.co",
    "SUPABASE_PUBLISHABLE_KEY" to "publishable-test",
    "SUPABASE_AUTH_TIMEOUT_MS" to "5000",
    "OPENAI_API_KEY" to "openai-test",
    "POCKETMIND_AGENT_MODEL" to "gpt-4o-mini",
    "POCKETMIND_FALLBACK_MODEL" to "gpt-4o",
    "POCKETMIND_PROMPT_VERSION" to "assistant-v1",
    "POCKETMIND_TOOL_SCHEMA_VERSION" to "1",
)

internal fun source(values: Map<String, String>): EnvironmentSource =
    EnvironmentSource(values::get)
