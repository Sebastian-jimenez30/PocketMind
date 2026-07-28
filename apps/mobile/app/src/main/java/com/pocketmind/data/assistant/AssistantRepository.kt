package com.pocketmind.data.assistant

import com.pocketmind.shared.assistant.AssistantTurnRequest
import com.pocketmind.shared.assistant.AssistantTurnResponse

interface AssistantRepository {
    suspend fun sendTurn(request: AssistantTurnRequest): AssistantTurnResponse
}
