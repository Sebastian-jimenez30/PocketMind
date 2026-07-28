package com.pocketmind.presentation.assistant

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pocketmind.data.assistant.AssistantRepository
import com.pocketmind.shared.assistant.AssistantDraftPreview
import com.pocketmind.shared.assistant.AssistantTurnRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AssistantUiMessage(
    val id: String,
    val role: String,
    val content: String,
    val draft: AssistantDraftPreview? = null,
)

data class AssistantUiState(
    val input: String = "",
    val messages: List<AssistantUiMessage> = emptyList(),
    val isSending: Boolean = false,
    val errorMessage: String? = null,
)

private data class PendingTurn(
    val content: String,
    val clientMessageId: String,
)

@HiltViewModel
class AssistantViewModel @Inject constructor(
    private val repository: AssistantRepository,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val mutableState = MutableStateFlow(AssistantUiState())
    val uiState: StateFlow<AssistantUiState> = mutableState.asStateFlow()
    private var pendingTurn: PendingTurn? = null

    fun onInputChanged(value: String) {
        mutableState.update {
            it.copy(input = value.take(MAX_INPUT_LENGTH), errorMessage = null)
        }
    }

    fun send() {
        val content = mutableState.value.input.trim()
        if (content.isEmpty() || mutableState.value.isSending) return
        submit(PendingTurn(content, UUID.randomUUID().toString()))
    }

    fun retry() {
        pendingTurn?.let(::submit)
    }

    fun dismissError() {
        mutableState.update { it.copy(errorMessage = null) }
    }

    private fun submit(turn: PendingTurn) {
        pendingTurn = turn
        viewModelScope.launch {
            mutableState.update { it.copy(isSending = true, errorMessage = null) }
            try {
                val response = repository.sendTurn(
                    AssistantTurnRequest(
                        conversationId = savedStateHandle[CONVERSATION_ID],
                        clientMessageId = turn.clientMessageId,
                        content = turn.content,
                    ),
                )
                savedStateHandle[CONVERSATION_ID] = response.conversationId
                val newMessages = listOf(
                    AssistantUiMessage(
                        id = response.userMessage.id,
                        role = response.userMessage.role,
                        content = response.userMessage.content,
                    ),
                    AssistantUiMessage(
                        id = response.assistantMessage.id,
                        role = response.assistantMessage.role,
                        content = response.reply,
                        draft = response.draft,
                    ),
                )
                mutableState.update {
                    it.copy(
                        input = "",
                        messages = it.messages + newMessages,
                        isSending = false,
                    )
                }
                pendingTurn = null
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                mutableState.update {
                    it.copy(
                        isSending = false,
                        errorMessage = failure.safeMessage(),
                    )
                }
            }
        }
    }

    private fun Exception.safeMessage(): String =
        message
            ?.takeIf { it.startsWith("El asistente") || it.startsWith("Tu sesión") }
            ?: "No pude procesar el mensaje. Revisa tu conexión e inténtalo de nuevo."

    private companion object {
        const val CONVERSATION_ID = "assistantConversationId"
        const val MAX_INPUT_LENGTH = 4_000
    }
}
