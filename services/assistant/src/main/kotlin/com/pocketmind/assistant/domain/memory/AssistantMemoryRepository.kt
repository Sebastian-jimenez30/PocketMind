package com.pocketmind.assistant.domain.memory

import com.pocketmind.assistant.auth.AuthenticatedUser

interface AssistantMemoryRepository {
    suspend fun createConversation(
        session: AuthenticatedUser,
        value: NewConversation,
    ): AssistantConversation

    suspend fun getConversation(
        session: AuthenticatedUser,
        conversationId: String,
    ): AssistantConversation?

    suspend fun listConversations(
        session: AuthenticatedUser,
        limit: Int = 50,
    ): List<AssistantConversation>

    suspend fun deleteConversation(
        session: AuthenticatedUser,
        conversationId: String,
    ): Boolean

    suspend fun appendMessage(
        session: AuthenticatedUser,
        value: NewMessage,
    ): AssistantMessage

    suspend fun findMessageByClientMessageId(
        session: AuthenticatedUser,
        clientMessageId: String,
    ): AssistantMessage?

    suspend fun listMessages(
        session: AuthenticatedUser,
        conversationId: String,
        limit: Int = 100,
    ): List<AssistantMessage>

    suspend fun createDraft(
        session: AuthenticatedUser,
        value: NewCommandDraft,
    ): AssistantCommandDraft

    suspend fun getDraft(
        session: AuthenticatedUser,
        draftId: String,
    ): AssistantCommandDraft?

    suspend fun getDraftByIdempotencyKey(
        session: AuthenticatedUser,
        idempotencyKey: String,
    ): AssistantCommandDraft?

    suspend fun reviseProposedDraft(
        session: AuthenticatedUser,
        draftId: String,
        expectedVersion: Long,
        revision: ProposedDraftRevision,
    ): AssistantCommandDraft

    suspend fun transitionDraft(
        session: AuthenticatedUser,
        draftId: String,
        transition: DraftTransition,
    ): AssistantCommandDraft

    suspend fun listDraftEvents(
        session: AuthenticatedUser,
        draftId: String,
    ): List<AssistantCommandEvent>

    suspend fun upsertProductAlias(
        session: AuthenticatedUser,
        value: NewProductAlias,
    ): AssistantProductAlias

    suspend fun listProductAliases(
        session: AuthenticatedUser,
    ): List<AssistantProductAlias>

    suspend fun deleteProductAlias(
        session: AuthenticatedUser,
        aliasId: String,
    ): Boolean

    suspend fun saveCheckpoint(
        session: AuthenticatedUser,
        value: NewAssistantCheckpoint,
    ): AssistantCheckpoint

    suspend fun listCheckpoints(
        session: AuthenticatedUser,
        conversationId: String,
    ): List<AssistantCheckpoint>
}

class AssistantMemoryConflictException(
    operation: String,
) : IllegalStateException(
    "Assistant memory operation '$operation' did not match the expected version or state.",
)

class AssistantMemoryRemoteException(
    operation: String,
    val statusCode: Int,
) : IllegalStateException(
    "Assistant memory operation '$operation' failed with status $statusCode.",
)
