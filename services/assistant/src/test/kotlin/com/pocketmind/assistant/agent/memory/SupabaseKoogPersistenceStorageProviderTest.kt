package com.pocketmind.assistant.agent.memory

import ai.koog.agents.snapshot.feature.AgentCheckpointData
import ai.koog.agents.snapshot.feature.GraphCheckpointProperties
import com.pocketmind.assistant.auth.AuthenticatedUser
import com.pocketmind.assistant.auth.SupabaseAccessToken
import com.pocketmind.assistant.domain.memory.AssistantCheckpoint
import com.pocketmind.assistant.domain.memory.AssistantCommandDraft
import com.pocketmind.assistant.domain.memory.AssistantCommandEvent
import com.pocketmind.assistant.domain.memory.AssistantConversation
import com.pocketmind.assistant.domain.memory.AssistantMemoryRepository
import com.pocketmind.assistant.domain.memory.AssistantMessage
import com.pocketmind.assistant.domain.memory.AssistantProductAlias
import com.pocketmind.assistant.domain.memory.DraftTransition
import com.pocketmind.assistant.domain.memory.NewAssistantCheckpoint
import com.pocketmind.assistant.domain.memory.NewCommandDraft
import com.pocketmind.assistant.domain.memory.NewConversation
import com.pocketmind.assistant.domain.memory.NewMessage
import com.pocketmind.assistant.domain.memory.NewProductAlias
import com.pocketmind.assistant.domain.memory.ProposedDraftRevision
import kotlinx.coroutines.test.runTest
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

@OptIn(kotlin.time.ExperimentalTime::class)
class SupabaseKoogPersistenceStorageProviderTest {
    @Test
    fun `checkpoint round trip stays bound to user and conversation`() = runTest {
        val repository = CheckpointOnlyRepository()
        val provider = provider(repository)
        val checkpoint = AgentCheckpointData(
            checkpointId = "checkpoint-1",
            createdAt = kotlin.time.Instant.parse("2026-07-28T03:05:00Z"),
            messageHistory = emptyList(),
            version = 3L,
            graphProperties = GraphCheckpointProperties(nodePath = "resolve-product"),
        )

        provider.saveCheckpoint(CONVERSATION_ID, checkpoint)
        val restored = provider.getLatestCheckpoint(CONVERSATION_ID)

        assertNotNull(restored)
        assertEquals("checkpoint-1", restored.checkpointId)
        assertEquals(3L, restored.version)
        assertEquals("resolve-product", restored.graphProperties?.nodePath)
        assertEquals(USER_ID, repository.saved?.userId)
        assertEquals(
            Instant.parse("2026-08-04T03:10:00Z"),
            repository.saved?.expiresAt,
        )
    }

    @Test
    fun `provider rejects a different Koog session before storage access`() = runTest {
        val repository = CheckpointOnlyRepository()
        val provider = provider(repository)

        assertFailsWith<IllegalArgumentException> {
            provider.getCheckpoints("another-conversation")
        }
        assertEquals(0, repository.readCount)
    }
}

private fun provider(
    repository: AssistantMemoryRepository,
): SupabaseKoogPersistenceStorageProvider =
    SupabaseKoogPersistenceStorageProvider(
        repository = repository,
        session = AuthenticatedUser(
            userId = USER_ID,
            role = "authenticated",
            accessToken = SupabaseAccessToken("access-token"),
        ),
        conversationId = CONVERSATION_ID,
        graphVersion = "assistant-v1",
        clock = Clock.fixed(
            Instant.parse("2026-07-28T03:10:00Z"),
            ZoneOffset.UTC,
        ),
        retention = Duration.ofDays(7),
    )

private class CheckpointOnlyRepository : AssistantMemoryRepository {
    var saved: AssistantCheckpoint? = null
    var readCount: Int = 0

    override suspend fun saveCheckpoint(
        session: AuthenticatedUser,
        value: NewAssistantCheckpoint,
    ): AssistantCheckpoint = AssistantCheckpoint(
        id = value.id,
        conversationId = value.conversationId,
        userId = session.userId,
        checkpointKey = value.checkpointKey,
        graphVersion = value.graphVersion,
        checkpointVersion = value.checkpointVersion,
        state = value.state,
        checkpointCreatedAt = value.checkpointCreatedAt,
        expiresAt = value.expiresAt,
    ).also { saved = it }

    override suspend fun listCheckpoints(
        session: AuthenticatedUser,
        conversationId: String,
    ): List<AssistantCheckpoint> {
        readCount += 1
        return listOfNotNull(saved)
    }

    override suspend fun createConversation(
        session: AuthenticatedUser,
        value: NewConversation,
    ): AssistantConversation = unsupported()

    override suspend fun getConversation(
        session: AuthenticatedUser,
        conversationId: String,
    ): AssistantConversation? = unsupported()

    override suspend fun listConversations(
        session: AuthenticatedUser,
        limit: Int,
    ): List<AssistantConversation> = unsupported()

    override suspend fun deleteConversation(
        session: AuthenticatedUser,
        conversationId: String,
    ): Boolean = unsupported()

    override suspend fun appendMessage(
        session: AuthenticatedUser,
        value: NewMessage,
    ): AssistantMessage = unsupported()

    override suspend fun findMessageByClientMessageId(
        session: AuthenticatedUser,
        clientMessageId: String,
    ): AssistantMessage? = unsupported()

    override suspend fun listMessages(
        session: AuthenticatedUser,
        conversationId: String,
        limit: Int,
    ): List<AssistantMessage> = unsupported()

    override suspend fun createDraft(
        session: AuthenticatedUser,
        value: NewCommandDraft,
    ): AssistantCommandDraft = unsupported()

    override suspend fun getDraft(
        session: AuthenticatedUser,
        draftId: String,
    ): AssistantCommandDraft? = unsupported()

    override suspend fun getDraftByIdempotencyKey(
        session: AuthenticatedUser,
        idempotencyKey: String,
    ): AssistantCommandDraft? = unsupported()

    override suspend fun getDraftByCommandId(
        session: AuthenticatedUser,
        commandId: String,
    ): AssistantCommandDraft? = unsupported()

    override suspend fun reviseDraft(
        session: AuthenticatedUser,
        draftId: String,
        expectedState: com.pocketmind.assistant.domain.memory.DraftState,
        expectedVersion: Long,
        revision: ProposedDraftRevision,
    ): AssistantCommandDraft = unsupported()

    override suspend fun transitionDraft(
        session: AuthenticatedUser,
        draftId: String,
        transition: DraftTransition,
    ): AssistantCommandDraft = unsupported()

    override suspend fun listDraftEvents(
        session: AuthenticatedUser,
        draftId: String,
    ): List<AssistantCommandEvent> = unsupported()

    override suspend fun upsertProductAlias(
        session: AuthenticatedUser,
        value: NewProductAlias,
    ): AssistantProductAlias = unsupported()

    override suspend fun listProductAliases(
        session: AuthenticatedUser,
    ): List<AssistantProductAlias> = unsupported()

    override suspend fun deleteProductAlias(
        session: AuthenticatedUser,
        aliasId: String,
    ): Boolean = unsupported()
}

private fun unsupported(): Nothing = error("Not used by this checkpoint test.")

private const val USER_ID = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa"
private const val CONVERSATION_ID = "11111111-1111-4111-8111-111111111111"
