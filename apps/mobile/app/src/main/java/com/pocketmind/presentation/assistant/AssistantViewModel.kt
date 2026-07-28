package com.pocketmind.presentation.assistant

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pocketmind.data.assistant.AssistantRepository
import com.pocketmind.data.assistant.AssistantRequestException
import com.pocketmind.data.sync.SyncCoordinator
import com.pocketmind.shared.assistant.AssistantCommandDraft
import com.pocketmind.shared.assistant.AssistantDraftPreview
import com.pocketmind.shared.assistant.AssistantDraftState
import com.pocketmind.shared.assistant.AssistantTurnRequest
import com.pocketmind.shared.domain.command.FinancialCommandCodec
import com.pocketmind.shared.domain.command.FinancialCommandResult
import com.pocketmind.shared.domain.usecase.ExecuteFinancialCommandUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class AssistantDraftUiState {
    PROPOSED,
    PROCESSING,
    COMPLETED,
    CANCELLED,
    FAILED,
    COMPLETION_PENDING,
}

data class AssistantUiDraft(
    val preview: AssistantDraftPreview,
    val state: AssistantDraftUiState = AssistantDraftUiState.PROPOSED,
    val message: String? = null,
)

data class AssistantUiMessage(
    val id: String,
    val role: String,
    val content: String,
    val draft: AssistantUiDraft? = null,
)

data class AssistantUiState(
    val input: String = "",
    val messages: List<AssistantUiMessage> = emptyList(),
    val isSending: Boolean = false,
    val activeDraftId: String? = null,
    val errorMessage: String? = null,
)

private data class PendingTurn(
    val content: String,
    val clientMessageId: String,
)

@HiltViewModel
class AssistantViewModel @Inject constructor(
    private val repository: AssistantRepository,
    private val executeFinancialCommand: ExecuteFinancialCommandUseCase,
    private val syncCoordinator: SyncCoordinator,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val mutableState = MutableStateFlow(AssistantUiState())
    val uiState: StateFlow<AssistantUiState> = mutableState.asStateFlow()
    private var pendingTurn: PendingTurn? = null

    init {
        savedStateHandle.get<String>(PENDING_EXECUTION_DRAFT_ID)
            ?.let { draftId ->
                mutableState.update { it.copy(activeDraftId = draftId) }
                recoverConfirmedDraft(draftId)
            }
    }

    fun onInputChanged(value: String) {
        mutableState.update {
            it.copy(input = value.take(MAX_INPUT_LENGTH), errorMessage = null)
        }
    }

    fun send() {
        val content = mutableState.value.input.trim()
        if (
            content.isEmpty() ||
            mutableState.value.isSending ||
            mutableState.value.activeDraftId != null
        ) {
            return
        }
        val proposedDraft = mutableState.value.messages
            .asReversed()
            .firstNotNullOfOrNull { it.draft }
            ?.takeIf { it.state == AssistantDraftUiState.PROPOSED }
        if (proposedDraft != null) {
            when (content.normalizedAction()) {
                in CONFIRMATION_MESSAGES -> {
                    appendLocalUserMessage(content)
                    confirmDraft(proposedDraft)
                    return
                }
                in CANCELLATION_MESSAGES -> {
                    appendLocalUserMessage(content)
                    cancelDraft(proposedDraft)
                    return
                }
            }
        }
        submit(PendingTurn(content, UUID.randomUUID().toString()))
    }

    fun retry() {
        pendingTurn?.let(::submit)
    }

    fun dismissError() {
        mutableState.update { it.copy(errorMessage = null) }
    }

    fun confirmDraft(draft: AssistantUiDraft) {
        if (!beginDraftAction(draft.preview.id)) return
        viewModelScope.launch {
            updateDraft(draft.preview.id) {
                it.copy(state = AssistantDraftUiState.PROCESSING, message = null)
            }
            try {
                synchronizeFinancialState()
                val confirmed = repository.confirmDraft(
                    draftId = draft.preview.id,
                    expectedVersion = draft.preview.version,
                )
                savedStateHandle[PENDING_EXECUTION_DRAFT_ID] = confirmed.id
                executeConfirmedDraft(confirmed)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                val recovered = runCatching {
                    repository.getDraft(draft.preview.id)
                }.getOrNull()
                when (recovered?.state) {
                    AssistantDraftState.CONFIRMED -> {
                        savedStateHandle[PENDING_EXECUTION_DRAFT_ID] = recovered.id
                        executeConfirmedDraft(recovered)
                    }
                    AssistantDraftState.COMPLETED -> {
                        savedStateHandle.remove<String>(PENDING_EXECUTION_DRAFT_ID)
                        finishDraftAction(
                            draft.preview.id,
                            AssistantDraftUiState.COMPLETED,
                            "Movimiento guardado. La sincronización continuará en segundo plano.",
                        )
                    }
                    AssistantDraftState.CANCELLED,
                    AssistantDraftState.EXPIRED,
                    -> {
                        savedStateHandle.remove<String>(PENDING_EXECUTION_DRAFT_ID)
                        finishDraftAction(
                            draft.preview.id,
                            AssistantDraftUiState.CANCELLED,
                            failure.safeMessage(),
                        )
                    }
                    else -> {
                        finishDraftAction(
                            draftId = draft.preview.id,
                            state = AssistantDraftUiState.PROPOSED,
                            message = failure.safeMessage(),
                        )
                    }
                }
            }
        }
    }

    fun cancelDraft(draft: AssistantUiDraft) {
        cancelOrEditDraft(draft, editAfterCancellation = false)
    }

    fun editDraft(draft: AssistantUiDraft) {
        cancelOrEditDraft(draft, editAfterCancellation = true)
    }

    fun retryDraftCompletion(draft: AssistantUiDraft) {
        if (!beginDraftAction(draft.preview.id)) return
        recoverConfirmedDraft(draft.preview.id)
    }

    private fun cancelOrEditDraft(
        draft: AssistantUiDraft,
        editAfterCancellation: Boolean,
    ) {
        if (!beginDraftAction(draft.preview.id)) return
        viewModelScope.launch {
            updateDraft(draft.preview.id) {
                it.copy(state = AssistantDraftUiState.PROCESSING, message = null)
            }
            try {
                repository.cancelDraft(
                    draftId = draft.preview.id,
                    expectedVersion = draft.preview.version,
                    expectedState = AssistantDraftState.PROPOSED,
                )
                finishCancellation(draft, editAfterCancellation)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                val recovered = runCatching {
                    repository.getDraft(draft.preview.id)
                }.getOrNull()
                if (recovered?.state == AssistantDraftState.CANCELLED) {
                    finishCancellation(draft, editAfterCancellation)
                } else {
                    finishDraftAction(
                        draftId = draft.preview.id,
                        state = AssistantDraftUiState.PROPOSED,
                        message = failure.safeMessage(),
                    )
                }
            }
        }
    }

    private fun finishCancellation(
        draft: AssistantUiDraft,
        editAfterCancellation: Boolean,
    ) {
        val editText = if (editAfterCancellation) {
            draft.preview.editPrompt()
        } else {
            mutableState.value.input
        }
        mutableState.update { state ->
            state.copy(
                input = editText,
                activeDraftId = null,
                messages = state.messages.updateDraft(draft.preview.id) {
                    it.copy(
                        state = AssistantDraftUiState.CANCELLED,
                        message = if (editAfterCancellation) {
                            "Propuesta abierta para corregir. Envíala cuando esté lista."
                        } else {
                            "Propuesta cancelada. No se guardó ningún movimiento."
                        },
                    )
                },
            )
        }
    }

    private fun recoverConfirmedDraft(draftId: String) {
        viewModelScope.launch {
            try {
                val draft = repository.getDraft(draftId)
                when (draft.state) {
                    AssistantDraftState.CONFIRMED -> {
                        synchronizeFinancialState()
                        executeConfirmedDraft(draft)
                    }
                    AssistantDraftState.COMPLETED -> {
                        savedStateHandle.remove<String>(PENDING_EXECUTION_DRAFT_ID)
                        finishDraftAction(
                            draftId,
                            AssistantDraftUiState.COMPLETED,
                            "Movimiento guardado. La sincronización continuará en segundo plano.",
                        )
                    }
                    AssistantDraftState.FAILED -> {
                        savedStateHandle.remove<String>(PENDING_EXECUTION_DRAFT_ID)
                        finishDraftAction(
                            draftId,
                            AssistantDraftUiState.FAILED,
                            "No se pudo guardar esta propuesta. Puedes corregirla e intentarlo de nuevo.",
                        )
                    }
                    else -> {
                        savedStateHandle.remove<String>(PENDING_EXECUTION_DRAFT_ID)
                        finishDraftAction(
                            draftId,
                            AssistantDraftUiState.PROPOSED,
                            "La propuesta ya no está confirmada. Genera una nueva para continuar.",
                        )
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                finishDraftAction(
                    draftId = draftId,
                    state = AssistantDraftUiState.COMPLETION_PENDING,
                    message = "El movimiento puede estar guardado. Reintenta para verificar y cerrar el proceso.",
                )
            }
        }
    }

    private suspend fun executeConfirmedDraft(draft: AssistantCommandDraft) {
        val command = FinancialCommandCodec.decode(draft.commandPayload.toString())
            .getOrElse {
                runCatching {
                    repository.cancelDraft(
                        draftId = draft.id,
                        expectedVersion = draft.version,
                        expectedState = AssistantDraftState.CONFIRMED,
                    )
                }
                savedStateHandle.remove<String>(PENDING_EXECUTION_DRAFT_ID)
                finishDraftAction(
                    draft.id,
                    AssistantDraftUiState.FAILED,
                    "Esta propuesta usa un formato que la aplicación no puede ejecutar.",
                )
                return
            }
        val result = try {
            executeFinancialCommand(command)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            finishDraftAction(
                draft.id,
                AssistantDraftUiState.COMPLETION_PENDING,
                "No pudimos verificar el guardado. Reintenta para continuar de forma segura.",
            )
            return
        }
        when (result) {
            is FinancialCommandResult.Success -> {
                try {
                    repository.completeDraft(
                        draftId = draft.id,
                        expectedVersion = draft.version,
                        result = result,
                    )
                    savedStateHandle.remove<String>(PENDING_EXECUTION_DRAFT_ID)
                    finishDraftAction(
                        draft.id,
                        AssistantDraftUiState.COMPLETED,
                        "Movimiento guardado. La sincronización continuará en segundo plano.",
                    )
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    finishDraftAction(
                        draft.id,
                        AssistantDraftUiState.COMPLETION_PENDING,
                        "El movimiento se guardó. Reintenta para cerrar la confirmación.",
                    )
                }
            }
            is FinancialCommandResult.Rejected -> {
                val failureRecorded = runCatching {
                    repository.failDraft(
                        draftId = draft.id,
                        expectedVersion = draft.version,
                        result = result,
                        errorCode = result.errors.firstOrNull()?.name
                            ?.lowercase()
                            ?: "command_rejected",
                    )
                }.isSuccess
                if (failureRecorded) {
                    savedStateHandle.remove<String>(PENDING_EXECUTION_DRAFT_ID)
                    finishDraftAction(
                        draft.id,
                        AssistantDraftUiState.FAILED,
                        "Tus datos cambiaron y esta propuesta ya no se puede guardar. Corrígela para continuar.",
                    )
                } else {
                    finishDraftAction(
                        draft.id,
                        AssistantDraftUiState.COMPLETION_PENDING,
                        "No se guardó el movimiento. Reintenta para cerrar la propuesta de forma segura.",
                    )
                }
            }
        }
    }

    private suspend fun synchronizeFinancialState() {
        syncCoordinator.syncCurrentSession().getOrElse { failure ->
            throw AssistantRequestException(
                publicMessage =
                    "No pudimos sincronizar tus productos. Revisa tu conexión antes de guardar.",
                cause = failure,
            )
        }
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
                        draft = response.draft?.let(::AssistantUiDraft),
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

    private fun appendLocalUserMessage(content: String) {
        mutableState.update { state ->
            state.copy(
                input = "",
                messages = state.messages + AssistantUiMessage(
                    id = UUID.randomUUID().toString(),
                    role = "user",
                    content = content,
                ),
            )
        }
    }

    private fun beginDraftAction(draftId: String): Boolean {
        if (mutableState.value.activeDraftId != null || mutableState.value.isSending) {
            return false
        }
        mutableState.update { it.copy(activeDraftId = draftId, errorMessage = null) }
        return true
    }

    private fun updateDraft(
        draftId: String,
        transform: (AssistantUiDraft) -> AssistantUiDraft,
    ) {
        mutableState.update { state ->
            state.copy(messages = state.messages.updateDraft(draftId, transform))
        }
    }

    private fun finishDraftAction(
        draftId: String,
        state: AssistantDraftUiState,
        message: String,
    ) {
        mutableState.update { current ->
            val hasDraft = current.messages.any { it.draft?.preview?.id == draftId }
            current.copy(
                activeDraftId = null,
                messages = if (hasDraft) {
                    current.messages.updateDraft(draftId) {
                        it.copy(state = state, message = message)
                    }
                } else {
                    current.messages + AssistantUiMessage(
                        id = "draft-recovery-$draftId",
                        role = "assistant",
                        content = message,
                    )
                },
            )
        }
    }

    private fun Exception.safeMessage(): String = when (this) {
        is AssistantRequestException -> publicMessage
        else -> message
            ?.takeIf { it.startsWith("El asistente") || it.startsWith("Tu sesión") }
            ?: "El asistente no pudo responder ahora. Tu mensaje sigue aquí para reintentarlo."
    }

    private fun AssistantDraftPreview.editPrompt(): String {
        val action = when (commandType) {
            "record_income" -> "ingreso"
            "record_expense" -> "gasto"
            "transfer" -> "transferencia"
            "create_product" -> "nuevo producto"
            "update_product" -> "cambio de producto"
            "archive_product" -> "archivo de producto"
            "record_card_purchase" -> "compra con tarjeta"
            "record_card_payment" -> "pago de tarjeta"
            "record_savings_movement" -> "movimiento de ahorro"
            "record_loan_payment" -> "pago de préstamo"
            "update_transaction" -> "corrección de movimiento"
            "delete_transaction" -> "eliminación de movimiento"
            else -> "acción"
        }
        val destination = destinationProductName?.let { " hacia $it" }.orEmpty()
        val amount = amountMinorUnits?.let { " de $it ${currency.orEmpty()}" }.orEmpty()
        return "Corrige este $action$amount en $primaryProductName$destination: "
    }

    private companion object {
        const val CONVERSATION_ID = "assistantConversationId"
        const val PENDING_EXECUTION_DRAFT_ID = "assistantPendingExecutionDraftId"
        const val MAX_INPUT_LENGTH = 4_000
        val CONFIRMATION_MESSAGES = setOf(
            "si",
            "guardar",
            "guardalo",
            "confirmar",
            "confirmalo",
            "si guardalo",
            "si confirmar",
        )
        val CANCELLATION_MESSAGES = setOf(
            "no",
            "cancelar",
            "cancelalo",
            "no guardar",
        )
    }
}

private fun List<AssistantUiMessage>.updateDraft(
    draftId: String,
    transform: (AssistantUiDraft) -> AssistantUiDraft,
): List<AssistantUiMessage> = map { message ->
    if (message.draft?.preview?.id == draftId) {
        message.copy(draft = transform(message.draft))
    } else {
        message
    }
}

private fun String.normalizedAction(): String =
    lowercase()
        .replace("á", "a")
        .replace("é", "e")
        .replace("í", "i")
        .replace("ó", "o")
        .replace("ú", "u")
        .replace(Regex("[^a-z0-9 ]"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
