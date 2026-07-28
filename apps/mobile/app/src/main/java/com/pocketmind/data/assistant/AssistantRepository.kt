package com.pocketmind.data.assistant

import com.pocketmind.shared.assistant.AssistantCommandDraft
import com.pocketmind.shared.assistant.AssistantDraftState
import com.pocketmind.shared.assistant.AssistantTurnRequest
import com.pocketmind.shared.assistant.AssistantTurnResponse
import com.pocketmind.shared.domain.command.FinancialCommandResult

interface AssistantRepository {
    suspend fun sendTurn(request: AssistantTurnRequest): AssistantTurnResponse
    suspend fun getDraft(draftId: String): AssistantCommandDraft
    suspend fun confirmDraft(draftId: String, expectedVersion: Long): AssistantCommandDraft
    suspend fun cancelDraft(
        draftId: String,
        expectedVersion: Long,
        expectedState: AssistantDraftState,
    ): AssistantCommandDraft

    suspend fun completeDraft(
        draftId: String,
        expectedVersion: Long,
        result: FinancialCommandResult,
    ): AssistantCommandDraft

    suspend fun failDraft(
        draftId: String,
        expectedVersion: Long,
        result: FinancialCommandResult,
        errorCode: String,
    ): AssistantCommandDraft
}

class AssistantRequestException(
    val publicMessage: String,
    cause: Throwable? = null,
) : IllegalStateException(publicMessage, cause)
