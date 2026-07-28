package com.pocketmind.assistant.testing

import com.pocketmind.assistant.auth.AuthenticatedUser
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

class ReadOnlyMemoryRepository(
    private val aliases: List<AssistantProductAlias> = emptyList(),
) : AssistantMemoryRepository {
    override suspend fun listProductAliases(
        session: AuthenticatedUser,
    ): List<AssistantProductAlias> = aliases.filter { it.userId == session.userId }

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

    override suspend fun reviseProposedDraft(
        session: AuthenticatedUser,
        draftId: String,
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

    override suspend fun deleteProductAlias(
        session: AuthenticatedUser,
        aliasId: String,
    ): Boolean = unsupported()

    override suspend fun saveCheckpoint(
        session: AuthenticatedUser,
        value: NewAssistantCheckpoint,
    ): AssistantCheckpoint = unsupported()

    override suspend fun listCheckpoints(
        session: AuthenticatedUser,
        conversationId: String,
    ): List<AssistantCheckpoint> = unsupported()
}

private fun unsupported(): Nothing =
    error("This test double only supports reading product aliases.")
