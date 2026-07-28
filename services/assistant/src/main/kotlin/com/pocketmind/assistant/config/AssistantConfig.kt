package com.pocketmind.assistant.config

import java.net.URI

enum class AssistantEnvironment {
    LOCAL,
    TEST,
    STAGING,
    PRODUCTION,
}

/**
 * Secret wrapper whose textual representation can never reveal its value.
 *
 * The raw value is only available inside this module for outbound calls to
 * trusted providers.
 */
class SecretValue internal constructor(
    private val value: String,
) {
    internal fun reveal(): String = value

    override fun toString(): String = REDACTED

    companion object {
        const val REDACTED: String = "[REDACTED]"
    }
}

fun interface EnvironmentSource {
    fun get(name: String): String?
}

object SystemEnvironmentSource : EnvironmentSource {
    override fun get(name: String): String? = System.getenv(name)
}

data class AssistantConfig(
    val environment: AssistantEnvironment,
    val port: Int,
    val serviceVersion: String,
    val supabaseUrl: String,
    val supabasePublishableKey: SecretValue,
    val supabaseAuthTimeoutMs: Long,
    val openAiApiKey: SecretValue,
    val primaryModel: String,
    val fallbackModel: String,
    val promptVersion: String,
    val toolSchemaVersion: Int,
) {
    companion object {
        fun load(source: EnvironmentSource = SystemEnvironmentSource): AssistantConfig {
            val invalidKeys = linkedSetOf<String>()

            val environment = source.required("APP_ENV", invalidKeys)
                ?.uppercase()
                ?.let { value ->
                    AssistantEnvironment.entries.firstOrNull { it.name == value }
                        ?: invalidKeys.addAndNull("APP_ENV")
                }

            val port = source.valueOrDefault("PORT", DEFAULT_PORT.toString())
                .toIntOrNull()
                ?.takeIf { it in 1..65535 }
                ?: invalidKeys.addAndNull("PORT")

            val serviceVersion = source.valueOrDefault("SERVICE_VERSION", "dev")
                .takeIf(String::isNotBlank)
                ?: invalidKeys.addAndNull("SERVICE_VERSION")

            val supabaseUrl = source.required("SUPABASE_URL", invalidKeys)
                ?.trimEnd('/')
                ?.takeIf { it.isValidHttpUrl(environment) }
                ?: run {
                    invalidKeys += "SUPABASE_URL"
                    null
                }

            val publishableKey = source.required("SUPABASE_PUBLISHABLE_KEY", invalidKeys)
                ?.let(::SecretValue)
            val openAiKey = source.required("OPENAI_API_KEY", invalidKeys)
                ?.let(::SecretValue)

            val timeout = source.valueOrDefault(
                "SUPABASE_AUTH_TIMEOUT_MS",
                DEFAULT_AUTH_TIMEOUT_MS.toString(),
            ).toLongOrNull()
                ?.takeIf { it in 500..30_000 }
                ?: invalidKeys.addAndNull("SUPABASE_AUTH_TIMEOUT_MS")

            val primaryModel = source.valueOrDefault(
                "POCKETMIND_AGENT_MODEL",
                DEFAULT_PRIMARY_MODEL,
            )
                .takeIf(String::isNotBlank)
                ?: invalidKeys.addAndNull("POCKETMIND_AGENT_MODEL")
            val fallbackModel = source.valueOrDefault(
                "POCKETMIND_FALLBACK_MODEL",
                DEFAULT_FALLBACK_MODEL,
            ).takeIf(String::isNotBlank)
                ?: invalidKeys.addAndNull("POCKETMIND_FALLBACK_MODEL")
            val promptVersion = source.valueOrDefault(
                "POCKETMIND_PROMPT_VERSION",
                DEFAULT_PROMPT_VERSION,
            ).takeIf { it.length in 1..80 }
                ?: invalidKeys.addAndNull("POCKETMIND_PROMPT_VERSION")
            val toolSchemaVersion = source.valueOrDefault(
                "POCKETMIND_TOOL_SCHEMA_VERSION",
                DEFAULT_TOOL_SCHEMA_VERSION.toString(),
            ).toIntOrNull()
                ?.takeIf { it > 0 }
                ?: invalidKeys.addAndNull("POCKETMIND_TOOL_SCHEMA_VERSION")

            if (invalidKeys.isNotEmpty()) {
                throw ConfigurationException(invalidKeys)
            }

            return AssistantConfig(
                environment = requireNotNull(environment),
                port = requireNotNull(port),
                serviceVersion = requireNotNull(serviceVersion),
                supabaseUrl = requireNotNull(supabaseUrl),
                supabasePublishableKey = requireNotNull(publishableKey),
                supabaseAuthTimeoutMs = requireNotNull(timeout),
                openAiApiKey = requireNotNull(openAiKey),
                primaryModel = requireNotNull(primaryModel),
                fallbackModel = requireNotNull(fallbackModel),
                promptVersion = requireNotNull(promptVersion),
                toolSchemaVersion = requireNotNull(toolSchemaVersion),
            )
        }

        private const val DEFAULT_PORT = 8080
        private const val DEFAULT_AUTH_TIMEOUT_MS = 5_000L
        private const val DEFAULT_PRIMARY_MODEL = "gpt-4o-mini"
        private const val DEFAULT_FALLBACK_MODEL = "gpt-4o"
        private const val DEFAULT_PROMPT_VERSION = "assistant-v2"
        private const val DEFAULT_TOOL_SCHEMA_VERSION = 1
    }
}

class ConfigurationException(
    val invalidKeys: Set<String>,
) : IllegalStateException(
    "Missing or invalid environment variables: ${invalidKeys.sorted().joinToString()}",
)

private fun EnvironmentSource.required(
    name: String,
    invalidKeys: MutableSet<String>,
): String? = get(name)
    ?.trim()
    ?.takeIf(String::isNotBlank)
    ?: invalidKeys.addAndNull(name)

private fun EnvironmentSource.valueOrDefault(name: String, default: String): String =
    get(name)?.trim()?.takeIf(String::isNotBlank) ?: default

private fun <T> MutableSet<String>.addAndNull(key: String): T? {
    add(key)
    return null
}

private fun String.isValidHttpUrl(environment: AssistantEnvironment?): Boolean =
    runCatching { URI(this) }
        .getOrNull()
        ?.let { uri ->
            val acceptedScheme = when (environment) {
                AssistantEnvironment.STAGING,
                AssistantEnvironment.PRODUCTION,
                -> uri.scheme.equals("https", ignoreCase = true)

                else -> uri.scheme.equals("http", ignoreCase = true) ||
                    uri.scheme.equals("https", ignoreCase = true)
            }
            acceptedScheme && !uri.host.isNullOrBlank() && uri.userInfo == null
        }
        ?: false
