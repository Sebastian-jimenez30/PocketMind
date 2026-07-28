package com.pocketmind.assistant.agent.memory

import ai.koog.agents.snapshot.feature.AgentCheckpointData
import ai.koog.agents.snapshot.providers.PersistenceStorageProvider
import ai.koog.agents.snapshot.providers.PersistenceUtils
import ai.koog.agents.snapshot.providers.filters.AgentCheckpointPredicateFilter
import com.pocketmind.assistant.auth.AuthenticatedUser
import com.pocketmind.assistant.domain.memory.AssistantMemoryRepository
import com.pocketmind.assistant.domain.memory.NewAssistantCheckpoint
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import java.nio.charset.StandardCharsets
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.UUID

/**
 * User-bound Koog checkpoint storage.
 *
 * A provider instance belongs to exactly one authenticated user and one
 * conversation. Koog's sessionId must match that conversation, so a graph
 * cannot accidentally read a checkpoint from a different session.
 */
@OptIn(kotlin.time.ExperimentalTime::class)
class SupabaseKoogPersistenceStorageProvider(
    private val repository: AssistantMemoryRepository,
    private val session: AuthenticatedUser,
    private val conversationId: String,
    private val graphVersion: String,
    private val clock: Clock = Clock.systemUTC(),
    private val retention: Duration = DEFAULT_RETENTION,
    private val json: Json = PersistenceUtils.defaultCheckpointJson,
) : PersistenceStorageProvider<AgentCheckpointPredicateFilter> {
    override suspend fun getCheckpoints(
        sessionId: String,
        filter: AgentCheckpointPredicateFilter?,
    ): List<AgentCheckpointData> {
        requireMatchingSession(sessionId)
        val checkpoints = repository.listCheckpoints(session, conversationId)
            .map { record ->
                json.decodeFromJsonElement(
                    AgentCheckpointData.serializer(),
                    record.state,
                )
            }

        return if (filter == null) {
            checkpoints
        } else {
            checkpoints.filter(filter::check)
        }
    }

    override suspend fun saveCheckpoint(
        sessionId: String,
        agentCheckpointData: AgentCheckpointData,
    ) {
        requireMatchingSession(sessionId)
        val state = json.encodeToJsonElement(
            AgentCheckpointData.serializer(),
            agentCheckpointData,
        ).jsonObject
        val checkpointCreatedAt = Instant.parse(agentCheckpointData.createdAt.toString())
        val retentionBase = maxOf(clock.instant(), checkpointCreatedAt)

        repository.saveCheckpoint(
            session = session,
            value = NewAssistantCheckpoint(
                id = deterministicCheckpointId(agentCheckpointData.checkpointId),
                conversationId = conversationId,
                checkpointKey = agentCheckpointData.checkpointId,
                graphVersion = graphVersion,
                checkpointVersion = agentCheckpointData.version,
                state = state,
                checkpointCreatedAt = checkpointCreatedAt,
                expiresAt = retentionBase.plus(retention),
            ),
        )
    }

    override suspend fun getLatestCheckpoint(
        sessionId: String,
        filter: AgentCheckpointPredicateFilter?,
    ): AgentCheckpointData? = getCheckpoints(sessionId, filter)
        .maxByOrNull(AgentCheckpointData::createdAt)

    private fun requireMatchingSession(sessionId: String) {
        require(sessionId == conversationId) {
            "Koog session does not match the bound PocketMind conversation."
        }
    }

    private fun deterministicCheckpointId(checkpointKey: String): String =
        UUID.nameUUIDFromBytes(
            "${session.userId}:$conversationId:$checkpointKey"
                .toByteArray(StandardCharsets.UTF_8),
        ).toString()

    private companion object {
        val DEFAULT_RETENTION: Duration = Duration.ofDays(7)
    }
}
