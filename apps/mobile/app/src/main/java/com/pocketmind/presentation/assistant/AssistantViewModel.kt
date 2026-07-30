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
import com.pocketmind.shared.domain.command.FinancialCommand
import com.pocketmind.shared.domain.command.FinancialCommandResult
import com.pocketmind.shared.domain.model.FinancialAccount
import com.pocketmind.shared.domain.model.FinancialAccountType
import com.pocketmind.shared.domain.model.FinancialProductConfiguration
import com.pocketmind.shared.domain.model.FinancialTransaction
import com.pocketmind.shared.domain.model.CustomCategory
import com.pocketmind.shared.domain.model.Money
import com.pocketmind.shared.domain.model.TransactionCategoryId
import com.pocketmind.shared.domain.usecase.ExecuteFinancialCommandUseCase
import com.pocketmind.shared.domain.usecase.GetTransactionUseCase
import com.pocketmind.shared.domain.usecase.ObserveActiveFinancialAccountsUseCase
import com.pocketmind.shared.domain.usecase.ObserveCustomCategoriesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.jsonObject

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
) {
    val isReversibleMovement: Boolean
        get() = preview.isReversibleMovement()
}

data class AssistantUiMessage(
    val id: String,
    val role: String,
    val content: String,
    val draft: AssistantUiDraft? = null,
    val deliveryState: AssistantMessageDeliveryState = AssistantMessageDeliveryState.SENT,
    val deliveryError: String? = null,
    val clientMessageId: String? = null,
)

enum class AssistantMessageDeliveryState {
    SENT,
    SENDING,
    FAILED,
}

data class AssistantUiState(
    val input: String = "",
    val messages: List<AssistantUiMessage> = emptyList(),
    val isSending: Boolean = false,
    val activeDraftId: String? = null,
    val errorMessage: String? = null,
    val draftEditor: AssistantDraftEditorState? = null,
    val products: List<FinancialAccount> = emptyList(),
    val customCategories: List<CustomCategory> = emptyList(),
)

data class AssistantDraftEditorState(
    val draft: AssistantUiDraft,
    val command: FinancialCommand,
    val amount: String,
    val merchant: String,
    val productName: String,
    val productId: String,
    val products: List<FinancialAccount>,
    val relatedProductId: String,
    val relatedProducts: List<FinancialAccount>,
    val installmentCount: String,
    val annualRatePercent: String,
    val categoryId: String,
    val customCategories: List<CustomCategory>,
    val note: String,
    val error: String? = null,
)

private data class PendingTurn(
    val content: String,
    val clientMessageId: String,
    val localMessageId: String,
)

@HiltViewModel
class AssistantViewModel @Inject constructor(
    private val repository: AssistantRepository,
    private val executeFinancialCommand: ExecuteFinancialCommandUseCase,
    private val syncCoordinator: SyncCoordinator,
    private val observeAccounts: ObserveActiveFinancialAccountsUseCase,
    private val observeCustomCategories: ObserveCustomCategoriesUseCase,
    private val getTransaction: GetTransactionUseCase,
    private val manualFinanceRepository: com.pocketmind.shared.domain.repository.ManualFinanceRepository,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val mutableState = MutableStateFlow(AssistantUiState())
    val uiState: StateFlow<AssistantUiState> = mutableState.asStateFlow()

    init {
        viewModelScope.launch {
            observeAccounts().collect { products ->
                mutableState.update { state -> state.copy(products = products) }
            }
        }
        viewModelScope.launch {
            observeCustomCategories().collect { categories ->
                mutableState.update { state ->
                    state.copy(customCategories = categories)
                }
            }
        }
        savedStateHandle.get<String>(PENDING_EXECUTION_DRAFT_ID)
            ?.let { draftId ->
                mutableState.update { it.copy(activeDraftId = draftId) }
                recoverConfirmedDraft(draftId)
            }
        val requestedCommandId = savedStateHandle.get<String>("commandId")
            ?.takeIf(String::isNotBlank)
        val requestedTransactionId = savedStateHandle.get<String>("transactionId")
            ?.takeIf(String::isNotBlank)
        if (requestedCommandId != null && requestedTransactionId != null) {
            openHistoryDraft(requestedCommandId, requestedTransactionId)
        }
    }

    private fun openHistoryDraft(commandId: String, transactionId: String) {
        viewModelScope.launch {
            try {
                val stored = repository.getDraftByCommandId(commandId)
                require(stored.state == AssistantDraftState.COMPLETED) {
                    "El movimiento ya no está disponible."
                }
                val command = FinancialCommandCodec.decode(
                    stored.commandPayload.toString(),
                ).getOrThrow()
                val transaction = getTransaction(transactionId)
                    ?: error("El movimiento ya no está disponible.")
                val products = observeAccounts().first()
                val customCategories = observeCustomCategories().first()
                val uiDraft = AssistantUiDraft(
                    preview = command.toHistoryPreview(
                        draft = stored,
                        transaction = transaction,
                        products = products,
                    ),
                    state = AssistantDraftUiState.COMPLETED,
                    message = "Movimiento guardado.",
                )
                mutableState.update { state ->
                    state.copy(
                        messages = listOf(
                            AssistantUiMessage(
                                id = "history-${stored.id}",
                                role = "assistant",
                                content = "Puedes corregir o cancelar este movimiento.",
                                draft = uiDraft,
                            ),
                        ),
                        draftEditor = command.toEditor(
                            draft = uiDraft,
                            products = products,
                            customCategories = customCategories,
                        ),
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                mutableState.update {
                    it.copy(errorMessage = failure.safeMessage())
                }
            }
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
        submit(
            PendingTurn(
                content = content,
                clientMessageId = UUID.randomUUID().toString(),
                localMessageId = UUID.randomUUID().toString(),
            ),
            appendOptimisticMessage = true,
        )
    }

    fun retry(message: AssistantUiMessage) {
        if (mutableState.value.isSending || mutableState.value.activeDraftId != null) return
        val clientMessageId = message.clientMessageId ?: return
        submit(
            PendingTurn(message.content, clientMessageId, message.id),
            appendOptimisticMessage = false,
        )
    }

    fun confirmDraft(draft: AssistantUiDraft) {
        if (!beginDraftAction(draft.preview.id)) return
        viewModelScope.launch {
            confirmAndExecuteDraft(draft, syncAfterExecution = true)
        }
    }

    private suspend fun saveDraftsSequentially(drafts: List<AssistantUiDraft>) {
        if (drafts.isEmpty()) return
        val confirmedDrafts = mutableListOf<AssistantCommandDraft>()
        drafts.forEach { draft ->
            if (beginDraftAction(draft.preview.id)) {
                val confirmed = confirmDraftForExecution(draft)
                if (confirmed != null) {
                    confirmedDrafts += confirmed
                    mutableState.update { it.copy(activeDraftId = null) }
                }
            }
        }
        confirmedDrafts.forEach { confirmed ->
            if (beginDraftAction(confirmed.id)) {
                savedStateHandle[PENDING_EXECUTION_DRAFT_ID] = confirmed.id
                executeConfirmedDraft(
                    confirmed,
                    syncAfterExecution = false,
                )
            }
        }
        if (confirmedDrafts.isNotEmpty()) requestBackgroundSync()
    }

    private suspend fun confirmAndExecuteDraft(
        draft: AssistantUiDraft,
        syncAfterExecution: Boolean,
    ) {
        val confirmed = confirmDraftForExecution(draft) ?: return
        savedStateHandle[PENDING_EXECUTION_DRAFT_ID] = confirmed.id
        executeConfirmedDraft(confirmed, syncAfterExecution)
    }

    private suspend fun confirmDraftForExecution(
        draft: AssistantUiDraft,
    ): AssistantCommandDraft? {
        updateDraft(draft.preview.id) {
            it.copy(state = AssistantDraftUiState.PROCESSING, message = null)
        }
        return try {
            repository.confirmDraft(
                draftId = draft.preview.id,
                expectedVersion = draft.preview.version,
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            val recovered = runCatching {
                repository.getDraft(draft.preview.id)
            }.getOrNull()
            when (recovered?.state) {
                AssistantDraftState.CONFIRMED -> recovered
                AssistantDraftState.COMPLETED -> {
                    savedStateHandle.remove<String>(PENDING_EXECUTION_DRAFT_ID)
                    finishDraftAction(
                        draft.preview.id,
                        AssistantDraftUiState.COMPLETED,
                        "Movimiento guardado.",
                    )
                    null
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
                    null
                }
                else -> {
                    finishDraftAction(
                        draftId = draft.preview.id,
                        state = AssistantDraftUiState.PROPOSED,
                        message = failure.safeMessage(),
                    )
                    null
                }
            }
        }
    }

    fun cancelDraft(draft: AssistantUiDraft) {
        if (!beginDraftAction(draft.preview.id)) return
        mutableState.update { state ->
            state.copy(
                activeDraftId = null,
                messages = state.messages.map { message ->
                    if (message.draft?.preview?.id == draft.preview.id) {
                        message.copy(
                            draft = message.draft.copy(
                                state = AssistantDraftUiState.CANCELLED,
                                message = "Movimiento cancelado y eliminado.",
                            ),
                        )
                    } else {
                        message
                    }
                },
            )
        }
        viewModelScope.launch {
            try {
                val currentDraft = runCatching { repository.getDraft(draft.preview.id) }.getOrNull()
                val stateToCancel = currentDraft?.state ?: AssistantDraftState.COMPLETED
                if (
                    stateToCancel == AssistantDraftState.COMPLETED ||
                    stateToCancel == AssistantDraftState.CANCELLED
                ) {
                    require(draft.preview.isReversibleMovement()) {
                        "Esta acción no se puede deshacer desde el chat."
                    }
                }
                if (stateToCancel != AssistantDraftState.CANCELLED) {
                    repository.cancelDraft(
                        draftId = draft.preview.id,
                        expectedVersion = currentDraft?.version ?: draft.preview.version,
                        expectedState = stateToCancel,
                    )
                }
                if (
                    stateToCancel == AssistantDraftState.COMPLETED ||
                    stateToCancel == AssistantDraftState.CANCELLED
                ) {
                    manualFinanceRepository.deleteRecordedMovement(
                        draft.preview.commandId,
                    )
                }
                syncCoordinator.syncCurrentSession()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                mutableState.update { state ->
                    state.copy(
                        messages = state.messages.updateDraft(draft.preview.id) {
                            draft.copy(message = failure.safeMessage())
                        },
                    )
                }
            }
        }
    }

    fun editDraft(draft: AssistantUiDraft) {
        if (!beginDraftAction(draft.preview.id)) return
        viewModelScope.launch {
            try {
                val stored = repository.getDraft(draft.preview.id)
                val command = FinancialCommandCodec.decode(stored.commandPayload.toString())
                    .getOrThrow()
                mutableState.update {
                    it.copy(
                        activeDraftId = null,
                        draftEditor = command.toEditor(
                            draft = draft.copy(
                                preview = draft.preview.copy(version = stored.version),
                            ),
                            products = it.products,
                            customCategories = it.customCategories,
                        ),
                    )
                }
            } catch (failure: Exception) {
                finishDraftAction(
                    draft.preview.id,
                    draft.state,
                    failure.safeMessage(),
                )
            }
        }
    }

    fun updateDraftEditor(
        transform: (AssistantDraftEditorState) -> AssistantDraftEditorState,
    ) {
        mutableState.update { state ->
            state.copy(draftEditor = state.draftEditor?.let(transform)?.copy(error = null))
        }
    }

    fun closeDraftEditor() {
        mutableState.update { it.copy(draftEditor = null) }
    }

    fun saveDraftEditor() {
        val editor = mutableState.value.draftEditor ?: return
        val revisedCommand = editor.toCommand().getOrElse { failure ->
            mutableState.update {
                it.copy(draftEditor = editor.copy(
                    error = failure.message ?: "Revisa los datos ingresados.",
                ))
            }
            return
        }
        if (!beginDraftAction(editor.draft.preview.id)) return
        viewModelScope.launch {
            var revisedVersion: Long? = null
            updateDraft(editor.draft.preview.id) {
                it.copy(state = AssistantDraftUiState.PROCESSING, message = null)
            }
            try {
                val payload = kotlinx.serialization.json.Json
                    .parseToJsonElement(FinancialCommandCodec.encode(revisedCommand))
                    .jsonObject
                val revised = repository.reviseDraft(
                    draftId = editor.draft.preview.id,
                    expectedVersion = editor.draft.preview.version,
                    expectedState = editor.draft.toStoredState(),
                    commandPayload = payload,
                )
                revisedVersion = revised.version
                if (editor.draft.state == AssistantDraftUiState.COMPLETED) {
                    manualFinanceRepository.deleteRecordedMovement(
                        editor.draft.preview.commandId,
                    )
                }
                val updatedDraft = editor.draft.copy(
                    preview = editor.draft.preview.copy(
                        version = revised.version,
                        amountMinorUnits = editor.amount.toLongOrNull(),
                        currency = editor.products
                            .firstOrNull { it.id == editor.productId }
                            ?.currency
                            ?.name
                            ?: editor.draft.preview.currency,
                        merchant = editor.merchant.trim().ifBlank { null },
                        categoryId = editor.categoryId.trim().ifBlank { null },
                        primaryProductId = editor.productId
                            .takeIf(String::isNotBlank)
                            ?: editor.draft.preview.primaryProductId,
                        primaryProductName = editor.selectedProductName()
                            ?: editor.productName.trim()
                                .ifBlank { editor.draft.preview.primaryProductName },
                        destinationProductId = editor.relatedProductId
                            .takeIf(String::isNotBlank),
                        destinationProductName = editor.selectedRelatedProductName(),
                        installmentCount = editor.installmentCount.toIntOrNull(),
                        annualRateBasisPoints = editor.annualRatePercent
                            .toBasisPointsOrNull(),
                    ),
                    state = AssistantDraftUiState.PROPOSED,
                    message = null,
                )
                mutableState.update { state ->
                    state.copy(
                        activeDraftId = null,
                        draftEditor = null,
                        messages = state.messages.updateDraft(updatedDraft.preview.id) {
                            updatedDraft
                        },
                    )
                }
                if (beginDraftAction(updatedDraft.preview.id)) {
                    confirmAndExecuteDraft(
                        updatedDraft,
                        syncAfterExecution = true,
                    )
                }
            } catch (failure: Exception) {
                mutableState.update { state ->
                    val retryEditor = revisedVersion?.let { version ->
                        editor.copy(
                            draft = editor.draft.copy(
                                preview = editor.draft.preview.copy(version = version),
                                state = AssistantDraftUiState.PROPOSED,
                            ),
                            error = failure.safeMessage(),
                        )
                    } ?: editor.copy(error = failure.safeMessage())
                    state.copy(
                        activeDraftId = null,
                        draftEditor = retryEditor,
                        messages = state.messages.updateDraft(editor.draft.preview.id) {
                            retryEditor.draft
                        },
                    )
                }
            }
        }
    }

    fun retryDraftCompletion(draft: AssistantUiDraft) {
        if (!beginDraftAction(draft.preview.id)) return
        recoverConfirmedDraft(draft.preview.id)
    }

    private fun recoverConfirmedDraft(draftId: String) {
        viewModelScope.launch {
            try {
                val draft = repository.getDraft(draftId)
                when (draft.state) {
                    AssistantDraftState.CONFIRMED -> {
                        executeConfirmedDraft(draft, syncAfterExecution = true)
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

    private suspend fun executeConfirmedDraft(
        draft: AssistantCommandDraft,
        syncAfterExecution: Boolean,
    ) {
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
                if (syncAfterExecution) requestBackgroundSync()
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

    private fun requestBackgroundSync() {
        viewModelScope.launch {
            syncCoordinator.syncCurrentSession()
        }
    }

    private fun submit(
        turn: PendingTurn,
        appendOptimisticMessage: Boolean,
    ) {
        mutableState.update { state ->
            val optimistic = AssistantUiMessage(
                id = turn.localMessageId,
                role = "user",
                content = turn.content,
                deliveryState = AssistantMessageDeliveryState.SENDING,
                clientMessageId = turn.clientMessageId,
            )
            state.copy(
                input = "",
                isSending = true,
                errorMessage = null,
                messages = if (appendOptimisticMessage) {
                    state.messages + optimistic
                } else {
                    state.messages.map { message ->
                        if (message.id == turn.localMessageId) {
                            optimistic
                        } else {
                            message
                        }
                    }
                },
            )
        }
        viewModelScope.launch {
            try {
                syncCoordinator.syncPendingChangesIfNeeded().getOrThrow()
                val response = repository.sendTurn(
                    AssistantTurnRequest(
                        conversationId = savedStateHandle[CONVERSATION_ID],
                        clientMessageId = turn.clientMessageId,
                        content = turn.content,
                    ),
                )
                savedStateHandle[CONVERSATION_ID] = response.conversationId
                val serverUserMessage = AssistantUiMessage(
                    id = response.userMessage.id,
                    role = response.userMessage.role,
                    content = response.userMessage.content,
                )
                val assistantMessage = AssistantUiMessage(
                    id = response.assistantMessage.id,
                    role = response.assistantMessage.role,
                    content = response.reply,
                    draft = response.draft?.let(::AssistantUiDraft),
                )
                val allDrafts = listOfNotNull(response.draft) + response.additionalDrafts
                val additionalMessages = response.additionalDrafts.mapIndexed { idx, preview ->
                    AssistantUiMessage(
                        id = "${response.assistantMessage.id}-add-$idx",
                        role = "assistant",
                        content = "También registré:",
                        draft = AssistantUiDraft(preview),
                    )
                }
                mutableState.update { state ->
                    state.copy(
                        messages = state.messages.flatMap { message ->
                            if (message.id == turn.localMessageId) {
                                listOf(serverUserMessage, assistantMessage) + additionalMessages
                            } else {
                                listOf(message)
                            }
                        },
                        isSending = false,
                    )
                }
                saveDraftsSequentially(
                    allDrafts
                        .filter(AssistantDraftPreview::isReversibleMovement)
                        .map(::AssistantUiDraft),
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                mutableState.update { state ->
                    state.copy(
                        isSending = false,
                        messages = state.messages.map { message ->
                            if (message.id == turn.localMessageId) {
                                message.copy(
                                    deliveryState = AssistantMessageDeliveryState.FAILED,
                                    deliveryError = failure.sendFailureMessage(),
                                )
                            } else {
                                message
                            }
                        },
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
        is AssistantRequestException -> when {
            publicMessage.startsWith("Tu sesión") -> "Tu sesión venció."
            publicMessage.contains("sincronizar", ignoreCase = true) ->
                "No se pudo sincronizar."
            else -> publicMessage
        }
        else -> "No se completó."
    }

    private fun Exception.sendFailureMessage(): String =
        if (this is AssistantRequestException && publicMessage.startsWith("Tu sesión")) {
            "Tu sesión venció."
        } else {
            "No se envió."
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

private fun AssistantUiDraft.toStoredState(): AssistantDraftState = when (state) {
    AssistantDraftUiState.COMPLETED -> AssistantDraftState.COMPLETED
    AssistantDraftUiState.FAILED -> AssistantDraftState.FAILED
    else -> AssistantDraftState.PROPOSED
}

private fun AssistantDraftPreview.isReversibleMovement(): Boolean =
    commandType in REVERSIBLE_MOVEMENT_COMMAND_TYPES

private val REVERSIBLE_MOVEMENT_COMMAND_TYPES = setOf(
    "record_income",
    "record_expense",
    "transfer",
    "record_card_purchase",
    "record_card_payment",
    "record_savings_movement",
    "record_loan_payment",
)

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

private fun FinancialCommand.primaryProductId(): String? = when (this) {
    is FinancialCommand.RecordIncome -> productId
    is FinancialCommand.RecordExpense -> productId
    is FinancialCommand.Transfer -> sourceProductId
    is FinancialCommand.CreateProduct -> null
    is FinancialCommand.UpdateProduct -> null
    is FinancialCommand.ArchiveProduct -> productId
    is FinancialCommand.RecordCardPurchase -> cardId
    is FinancialCommand.RecordCardPayment -> cardId
    is FinancialCommand.RecordSavingsMovement -> savingsId
    is FinancialCommand.RecordLoanPayment -> loanId
    is FinancialCommand.UpdateTransaction -> productId
    is FinancialCommand.DeleteTransaction -> null
}

private fun FinancialCommand.compatibleProducts(
    products: List<FinancialAccount>,
): List<FinancialAccount> {
    val compatibleTypes = when (this) {
        is FinancialCommand.RecordIncome,
        is FinancialCommand.RecordExpense,
        is FinancialCommand.Transfer,
        is FinancialCommand.UpdateTransaction,
        -> setOf(
            FinancialAccountType.CASH,
            FinancialAccountType.BANK_ACCOUNT,
            FinancialAccountType.SAVINGS,
        )
        is FinancialCommand.RecordCardPurchase,
        is FinancialCommand.RecordCardPayment,
        -> setOf(FinancialAccountType.CREDIT_CARD)
        is FinancialCommand.RecordSavingsMovement ->
            setOf(FinancialAccountType.SAVINGS)
        is FinancialCommand.RecordLoanPayment ->
            setOf(FinancialAccountType.LOAN)
        is FinancialCommand.CreateProduct,
        is FinancialCommand.UpdateProduct,
        is FinancialCommand.ArchiveProduct,
        is FinancialCommand.DeleteTransaction,
        -> emptySet()
    }
    val excludedDestinationId =
        (this as? FinancialCommand.Transfer)?.destinationProductId
    return products.filter { product ->
        product.type in compatibleTypes &&
            product.id != excludedDestinationId
    }
}

private fun FinancialCommand.toEditor(
    draft: AssistantUiDraft,
    products: List<FinancialAccount>,
    customCategories: List<CustomCategory>,
): AssistantDraftEditorState = AssistantDraftEditorState(
    draft = draft,
    command = this,
    amount = when (this) {
        is FinancialCommand.RecordIncome -> amount.minorUnits
        is FinancialCommand.RecordExpense -> amount.minorUnits
        is FinancialCommand.Transfer -> amount.minorUnits
        is FinancialCommand.CreateProduct -> account.openingBalance.minorUnits
        is FinancialCommand.UpdateProduct -> account.openingBalance.minorUnits
        is FinancialCommand.ArchiveProduct -> null
        is FinancialCommand.RecordCardPurchase -> principal.minorUnits
        is FinancialCommand.RecordCardPayment ->
            amount?.minorUnits ?: draft.preview.amountMinorUnits
        is FinancialCommand.RecordSavingsMovement ->
            amount.minorUnits.takeUnless {
                movementType == com.pocketmind.shared.domain.model.SavingsMovementType.RATE_CHANGE
            }
        is FinancialCommand.RecordLoanPayment ->
            amount?.minorUnits ?: draft.preview.amountMinorUnits
        is FinancialCommand.UpdateTransaction -> amount.minorUnits
        is FinancialCommand.DeleteTransaction -> null
    }?.toString().orEmpty(),
    merchant = when (this) {
        is FinancialCommand.RecordIncome -> merchant
        is FinancialCommand.RecordExpense -> merchant
        is FinancialCommand.Transfer -> merchant
        is FinancialCommand.RecordCardPurchase -> merchant
        is FinancialCommand.UpdateTransaction -> merchant
        else -> null
    }.orEmpty(),
    productName = when (this) {
        is FinancialCommand.CreateProduct -> account.name
        is FinancialCommand.UpdateProduct -> account.name
        else -> draft.preview.primaryProductName
    },
    productId = primaryProductId().orEmpty(),
    products = compatibleProducts(products),
    relatedProductId = relatedProductId().orEmpty(),
    relatedProducts = relatedCompatibleProducts(products),
    installmentCount = (this as? FinancialCommand.RecordCardPurchase)
        ?.installmentCount
        ?.toString()
        .orEmpty(),
    annualRatePercent = when (this) {
        is FinancialCommand.CreateProduct -> configuration.annualRateBasisPoints()
        is FinancialCommand.UpdateProduct -> configuration.annualRateBasisPoints()
        is FinancialCommand.RecordSavingsMovement -> annualYieldBasisPoints
        else -> null
    }?.let { (it / 100.0).toString() }.orEmpty(),
    categoryId = categoryId().orEmpty().ifBlank {
        TransactionCategoryId.OTHER.name
    },
    customCategories = customCategories,
    note = note().orEmpty(),
)

private fun AssistantDraftEditorState.selectedProductName(): String? =
    products.firstOrNull { it.id == productId }?.name

private fun AssistantDraftEditorState.selectedRelatedProductName(): String? =
    relatedProducts.firstOrNull { it.id == relatedProductId }?.name

private fun FinancialCommand.relatedProductId(): String? = when (this) {
    is FinancialCommand.Transfer -> destinationProductId
    is FinancialCommand.RecordCardPayment -> sourceProductId
    is FinancialCommand.RecordSavingsMovement -> when (movementType) {
        com.pocketmind.shared.domain.model.SavingsMovementType.DEPOSIT -> sourceProductId
        com.pocketmind.shared.domain.model.SavingsMovementType.WITHDRAWAL ->
            destinationProductId
        com.pocketmind.shared.domain.model.SavingsMovementType.RATE_CHANGE -> null
    }
    is FinancialCommand.RecordLoanPayment -> sourceProductId
    else -> null
}

private fun FinancialCommand.toHistoryPreview(
    draft: AssistantCommandDraft,
    transaction: FinancialTransaction,
    products: List<FinancialAccount>,
): AssistantDraftPreview {
    val primaryId = requireNotNull(primaryProductId())
    val relatedId = relatedProductId()
    val primary = products.firstOrNull { it.id == primaryId }
    val related = products.firstOrNull { it.id == relatedId }
    return AssistantDraftPreview(
        id = draft.id,
        commandId = commandId,
        version = draft.version,
        commandType = draft.commandType,
        amountMinorUnits = transaction.amount.minorUnits,
        currency = transaction.amount.currency.name,
        primaryProductId = primaryId,
        primaryProductName = primary?.name ?: "Producto",
        destinationProductId = relatedId,
        destinationProductName = related?.name,
        merchant = transaction.merchant,
        categoryId = transaction.categoryId,
        occurredAtEpochMillis = transaction.occurredAtEpochMillis,
        productType = primary?.type?.name,
        installmentCount = (this as? FinancialCommand.RecordCardPurchase)
            ?.installmentCount,
        annualRateBasisPoints =
            (this as? FinancialCommand.RecordSavingsMovement)
                ?.annualYieldBasisPoints,
        paymentType = when (this) {
            is FinancialCommand.RecordCardPayment -> paymentType.name
            is FinancialCommand.RecordLoanPayment -> paymentType.name
            else -> null
        },
        movementType = (this as? FinancialCommand.RecordSavingsMovement)
            ?.movementType
            ?.name,
        expiresAt = draft.expiresAt,
    )
}

private fun FinancialCommand.relatedCompatibleProducts(
    products: List<FinancialAccount>,
): List<FinancialAccount> {
    if (relatedProductId() == null && this !is FinancialCommand.Transfer) {
        return when (this) {
            is FinancialCommand.RecordCardPayment,
            is FinancialCommand.RecordSavingsMovement,
            is FinancialCommand.RecordLoanPayment,
            -> products.filter(FinancialAccount::isLiquidProduct)
            else -> emptyList()
        }
    }
    return products.filter(FinancialAccount::isLiquidProduct)
}

private fun FinancialAccount.isLiquidProduct(): Boolean =
    type == FinancialAccountType.CASH ||
        type == FinancialAccountType.BANK_ACCOUNT ||
        type == FinancialAccountType.SAVINGS

private fun FinancialCommand.categoryId(): String? = when (this) {
    is FinancialCommand.RecordIncome -> categoryId
    is FinancialCommand.RecordExpense -> categoryId
    is FinancialCommand.Transfer -> categoryId
    is FinancialCommand.RecordCardPurchase -> categoryId
    is FinancialCommand.UpdateTransaction -> categoryId
    else -> null
}

private fun FinancialCommand.note(): String? = when (this) {
    is FinancialCommand.RecordIncome -> note
    is FinancialCommand.RecordExpense -> note
    is FinancialCommand.Transfer -> note
    is FinancialCommand.RecordCardPurchase -> note
    is FinancialCommand.RecordCardPayment -> note
    is FinancialCommand.RecordSavingsMovement -> note
    is FinancialCommand.RecordLoanPayment -> note
    is FinancialCommand.UpdateTransaction -> note
    else -> null
}

private fun AssistantDraftEditorState.toCommand(): Result<FinancialCommand> = runCatching {
    val selectedProduct = products.firstOrNull { it.id == productId }
    val selectedRelatedProduct = relatedProducts
        .firstOrNull { it.id == relatedProductId }
    fun requiredProductId(): String {
        require(productId.isNotBlank() && selectedProduct != null) {
            "Selecciona un producto válido."
        }
        return productId
    }
    fun requiredAmount(currency: com.pocketmind.shared.domain.model.CurrencyCode): Money {
        val value = amount.toLongOrNull()
        require(value != null && value > 0) { "Ingresa un valor mayor que cero." }
        return Money(value, currency)
    }
    fun requiredRelatedProductId(): String {
        require(
            relatedProductId.isNotBlank() &&
                selectedRelatedProduct != null &&
                relatedProductId != productId,
        ) {
            "Selecciona un producto de destino válido."
        }
        return relatedProductId
    }
    fun optionalRelatedProductId(): String? =
        relatedProductId.takeIf { id ->
            id.isNotBlank() &&
                id != productId &&
                selectedRelatedProduct != null
        }
    fun optionalAmount(currency: com.pocketmind.shared.domain.model.CurrencyCode): Money? =
        amount.trim().takeIf(String::isNotEmpty)?.let {
            requiredAmount(currency)
        }
    val merchantValue = merchant.trim().ifBlank { null }
    val categoryValue = categoryId.trim().ifBlank { null }
    val noteValue = note.trim().ifBlank { null }
    when (val value = command) {
        is FinancialCommand.RecordIncome -> value.copy(
            productId = requiredProductId(),
            amount = requiredAmount(selectedProduct?.currency ?: value.amount.currency),
            merchant = merchantValue,
            categoryId = categoryValue,
            note = noteValue,
        )
        is FinancialCommand.RecordExpense -> value.copy(
            productId = requiredProductId(),
            amount = requiredAmount(selectedProduct?.currency ?: value.amount.currency),
            merchant = merchantValue,
            categoryId = categoryValue,
            note = noteValue,
        )
        is FinancialCommand.Transfer -> value.copy(
            sourceProductId = requiredProductId(),
            destinationProductId = requiredRelatedProductId(),
            amount = requiredAmount(selectedProduct?.currency ?: value.amount.currency),
            merchant = merchantValue,
            categoryId = categoryValue,
            note = noteValue,
        )
        is FinancialCommand.CreateProduct -> {
            val name = productName.trim()
            require(name.isNotEmpty()) { "Escribe el nombre del producto." }
            value.copy(
                account = value.account.copy(
                    name = name,
                    openingBalance = Money(
                        amount.toLongOrNull()?.takeIf { it >= 0 }
                            ?: error("Ingresa un saldo válido."),
                        value.account.currency,
                    ),
                ),
                configuration = value.configuration.withAnnualRate(
                    annualRatePercent.toBasisPointsOrNull(),
                ),
            )
        }
        is FinancialCommand.UpdateProduct -> {
            val name = productName.trim()
            require(name.isNotEmpty()) { "Escribe el nombre del producto." }
            value.copy(
                account = value.account.copy(
                    name = name,
                    openingBalance = Money(
                        amount.toLongOrNull()?.takeIf { it >= 0 }
                            ?: error("Ingresa un saldo válido."),
                        value.account.currency,
                    ),
                ),
                configuration = value.configuration.withAnnualRate(
                    annualRatePercent.toBasisPointsOrNull(),
                ),
            )
        }
        is FinancialCommand.ArchiveProduct -> value
        is FinancialCommand.RecordCardPurchase -> {
            val installments = installmentCount.toIntOrNull()
            require(installments != null && installments in 1..60) {
                "Las cuotas deben estar entre 1 y 60."
            }
            require(value.promotionalRatePeriods.all { it.lastInstallment <= installments }) {
                "Las cuotas no pueden ser menores al periodo promocional."
            }
            value.copy(
                cardId = requiredProductId(),
                principal = requiredAmount(
                    selectedProduct?.currency ?: value.principal.currency,
                ),
                merchant = merchantValue ?: error("Escribe el comercio o concepto."),
                installmentCount = installments,
                categoryId = categoryValue,
                note = noteValue,
            )
        }
        is FinancialCommand.RecordCardPayment -> value.copy(
            cardId = requiredProductId(),
            amount = optionalAmount(
                selectedProduct?.currency
                    ?: value.amount?.currency
                    ?: com.pocketmind.shared.domain.model.CurrencyCode.valueOf(
                        draft.preview.currency ?: "COP",
                    ),
            ),
            sourceProductId = optionalRelatedProductId(),
            note = noteValue,
        )
        is FinancialCommand.RecordSavingsMovement -> value.copy(
            savingsId = requiredProductId(),
            amount = if (
                value.movementType ==
                com.pocketmind.shared.domain.model.SavingsMovementType.RATE_CHANGE
            ) {
                value.amount
            } else {
                requiredAmount(selectedProduct?.currency ?: value.amount.currency)
            },
            annualYieldBasisPoints = if (
                value.movementType ==
                com.pocketmind.shared.domain.model.SavingsMovementType.RATE_CHANGE
            ) {
                annualRatePercent.toBasisPointsOrNull()
                    ?: error("Ingresa una tasa válida.")
            } else {
                value.annualYieldBasisPoints
            },
            sourceProductId = when (value.movementType) {
                com.pocketmind.shared.domain.model.SavingsMovementType.DEPOSIT ->
                    optionalRelatedProductId()
                else -> value.sourceProductId
            },
            destinationProductId = when (value.movementType) {
                com.pocketmind.shared.domain.model.SavingsMovementType.WITHDRAWAL ->
                    optionalRelatedProductId()
                else -> value.destinationProductId
            },
            note = noteValue,
        )
        is FinancialCommand.RecordLoanPayment -> value.copy(
            loanId = requiredProductId(),
            amount = optionalAmount(
                selectedProduct?.currency
                    ?: value.amount?.currency
                    ?: com.pocketmind.shared.domain.model.CurrencyCode.valueOf(
                        draft.preview.currency ?: "COP",
                    ),
            ),
            sourceProductId = optionalRelatedProductId(),
            note = noteValue,
        )
        is FinancialCommand.UpdateTransaction -> value.copy(
            productId = requiredProductId(),
            amount = requiredAmount(selectedProduct?.currency ?: value.amount.currency),
            merchant = merchantValue,
            categoryId = categoryValue,
            note = noteValue,
        )
        is FinancialCommand.DeleteTransaction -> value
    }
}

private fun FinancialProductConfiguration.withAnnualRate(
    annualRateBasisPoints: Int?,
): FinancialProductConfiguration = when (this) {
    FinancialProductConfiguration.Standard -> this
    is FinancialProductConfiguration.CreditCard -> copy(
        profile = profile.copy(
            annualInterestBasisPoints =
                annualRateBasisPoints ?: profile.annualInterestBasisPoints,
        ),
    )
    is FinancialProductConfiguration.Savings -> copy(
        profile = profile.copy(
            annualYieldBasisPoints =
                annualRateBasisPoints ?: profile.annualYieldBasisPoints,
        ),
    )
    is FinancialProductConfiguration.Loan -> copy(
        profile = profile.copy(
            annualInterestBasisPoints =
                annualRateBasisPoints ?: profile.annualInterestBasisPoints,
        ),
    )
}

private fun FinancialProductConfiguration.annualRateBasisPoints(): Int? = when (this) {
    FinancialProductConfiguration.Standard -> null
    is FinancialProductConfiguration.CreditCard -> profile.annualInterestBasisPoints
    is FinancialProductConfiguration.Savings ->
        profile.annualYieldBasisPoints.takeUnless {
            profile.type ==
                com.pocketmind.shared.domain.model.SavingsProductType.SIMPLE
        }
    is FinancialProductConfiguration.Loan -> profile.annualInterestBasisPoints
}

private fun String.toBasisPointsOrNull(): Int? =
    replace(',', '.').trim().toDoubleOrNull()
        ?.takeIf { it >= 0 }
        ?.times(100)
        ?.toInt()
