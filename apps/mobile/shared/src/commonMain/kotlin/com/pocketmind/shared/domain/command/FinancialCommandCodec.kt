package com.pocketmind.shared.domain.command

import com.pocketmind.shared.domain.model.CURRENT_FINANCIAL_RULE_VERSION
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

const val CURRENT_FINANCIAL_COMMAND_SCHEMA_VERSION = 1

@Serializable
data class FinancialCommandEnvelope(
    @SerialName("schema_version")
    val schemaVersion: Int = CURRENT_FINANCIAL_COMMAND_SCHEMA_VERSION,
    @SerialName("rule_version")
    val ruleVersion: Int = CURRENT_FINANCIAL_RULE_VERSION,
    val command: FinancialCommand,
)

/**
 * Strict provider-neutral wire codec shared by Android, iOS and the assistant.
 *
 * Unknown or unsupported versions fail closed instead of silently executing a
 * command under rules different from those shown during confirmation.
 */
object FinancialCommandCodec {
    private val json = Json {
        classDiscriminator = "command_type"
        encodeDefaults = true
        ignoreUnknownKeys = false
    }

    fun encode(command: FinancialCommand): String =
        json.encodeToString(
            FinancialCommandEnvelope.serializer(),
            FinancialCommandEnvelope(command = command),
        )

    fun decode(value: String): Result<FinancialCommand> = runCatching {
        val envelope = json.decodeFromString(FinancialCommandEnvelope.serializer(), value)
        require(envelope.schemaVersion == CURRENT_FINANCIAL_COMMAND_SCHEMA_VERSION) {
            "Unsupported financial command schema version: ${envelope.schemaVersion}"
        }
        require(envelope.ruleVersion == CURRENT_FINANCIAL_RULE_VERSION) {
            "Unsupported financial rule version: ${envelope.ruleVersion}"
        }
        envelope.command
    }
}
