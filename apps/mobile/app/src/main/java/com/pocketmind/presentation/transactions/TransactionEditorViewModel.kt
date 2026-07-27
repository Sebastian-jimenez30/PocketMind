package com.pocketmind.presentation.transactions

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pocketmind.presentation.common.toUserMessage
import com.pocketmind.shared.domain.command.FinancialCommand
import com.pocketmind.shared.domain.command.FinancialCommandResult
import com.pocketmind.shared.domain.model.FinancialAccount
import com.pocketmind.shared.domain.model.FinancialAccountType
import com.pocketmind.shared.domain.model.Money
import com.pocketmind.shared.domain.model.TransactionCategoryId
import com.pocketmind.shared.domain.model.TransactionSource
import com.pocketmind.shared.domain.model.TransactionType
import com.pocketmind.shared.domain.usecase.ExecuteFinancialCommandUseCase
import com.pocketmind.shared.domain.usecase.GetTransactionUseCase
import com.pocketmind.shared.domain.usecase.ObserveActiveFinancialAccountsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TransactionEditorUiState(
    val transactionId: String? = null,
    val accounts: List<FinancialAccount> = emptyList(),
    val accountId: String = "",
    val type: TransactionType = TransactionType.EXPENSE,
    val amount: String = "",
    val category: TransactionCategoryId = TransactionCategoryId.OTHER,
    val merchant: String = "",
    val note: String = "",
    val date: String = LocalDate.now().toDisplayDate(),
    val canDelete: Boolean = false,
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val error: String? = null,
    val saved: Boolean = false,
)

@HiltViewModel
class TransactionEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    observeAccounts: ObserveActiveFinancialAccountsUseCase,
    private val getTransaction: GetTransactionUseCase,
    private val executeFinancialCommand: ExecuteFinancialCommandUseCase,
) : ViewModel() {
    private val initialId: String? = savedStateHandle["transactionId"]
    private val initialType = savedStateHandle.get<String>("type")
        ?.let { runCatching { TransactionType.valueOf(it) }.getOrNull() }
        ?: TransactionType.EXPENSE
    private val _uiState = MutableStateFlow(TransactionEditorUiState(transactionId = initialId, type = initialType))
    val uiState: StateFlow<TransactionEditorUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            observeAccounts().collect { accounts ->
                val manualAccounts = accounts.filter {
                    it.type != FinancialAccountType.CREDIT_CARD &&
                        it.type != FinancialAccountType.SAVINGS &&
                        it.type != FinancialAccountType.LOAN
                }
                _uiState.update { state ->
                    state.copy(
                        accounts = manualAccounts,
                        accountId = state.accountId.ifBlank { manualAccounts.firstOrNull()?.id.orEmpty() },
                    )
                }
            }
        }
        initialId?.let { id ->
            viewModelScope.launch {
                getTransaction(id)?.let { transaction ->
                    _uiState.update {
                        it.copy(
                            accountId = transaction.accountId,
                            type = transaction.type,
                            amount = transaction.amount.minorUnits.toString(),
                            category = transaction.categoryId?.let(TransactionCategoryId::valueOf) ?: TransactionCategoryId.OTHER,
                            merchant = transaction.merchant.orEmpty(),
                            note = transaction.note.orEmpty(),
                            date = transaction.occurredAtEpochMillis.toDisplayDate(),
                            canDelete = !transaction.id.hasLinkedProduct(),
                        )
                    }
                }
                _uiState.update { it.copy(isLoading = false) }
            }
        } ?: _uiState.update { it.copy(isLoading = false) }
    }

    fun update(transform: (TransactionEditorUiState) -> TransactionEditorUiState) = _uiState.update(transform)

    fun save() {
        val state = _uiState.value
        val amount = state.amount.toLongOrNull()?.takeIf { it > 0 }
        val occurredAt = state.date.parseDisplayDate()
        val product = state.accounts.firstOrNull { it.id == state.accountId }
        if (amount == null || product == null || occurredAt == null) {
            _uiState.update { it.copy(error = "Completa un producto y un valor mayor que cero.") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, error = null) }
            val transactionId = state.transactionId ?: UUID.randomUUID().toString()
            val commandId = UUID.randomUUID().toString()
            val command = if (state.transactionId != null) {
                FinancialCommand.UpdateTransaction(
                    commandId = commandId,
                    transactionId = transactionId,
                    productId = state.accountId,
                    type = state.type,
                    amount = Money(amount, product.currency),
                    occurredAtEpochMillis = occurredAt,
                    source = TransactionSource.MANUAL,
                    categoryId = state.category.name,
                    merchant = state.merchant,
                    note = state.note,
                )
            } else {
                when (state.type) {
                    TransactionType.INCOME -> FinancialCommand.RecordIncome(
                        commandId = transactionId,
                        productId = state.accountId,
                        amount = Money(amount, product.currency),
                        occurredAtEpochMillis = occurredAt,
                        source = TransactionSource.MANUAL,
                        categoryId = state.category.name,
                        merchant = state.merchant,
                        note = state.note,
                    )
                    TransactionType.EXPENSE -> FinancialCommand.RecordExpense(
                        commandId = transactionId,
                        productId = state.accountId,
                        amount = Money(amount, product.currency),
                        occurredAtEpochMillis = occurredAt,
                        source = TransactionSource.MANUAL,
                        categoryId = state.category.name,
                        merchant = state.merchant,
                        note = state.note,
                    )
                    TransactionType.TRANSFER -> {
                        _uiState.update {
                            it.copy(isSaving = false, error = "Registra transferencias desde el formulario de operaciones.")
                        }
                        return@launch
                    }
                }
            }
            runCatching { executeFinancialCommand(command) }
                .onSuccess { result ->
                    when (result) {
                        is FinancialCommandResult.Success ->
                            _uiState.update { it.copy(isSaving = false, saved = true) }
                        is FinancialCommandResult.Rejected ->
                            _uiState.update { it.copy(isSaving = false, error = result.toUserMessage()) }
                    }
                }
                .onFailure {
                    _uiState.update {
                        it.copy(isSaving = false, error = "No pudimos guardar el movimiento.")
                    }
                }
        }
    }

    fun delete() {
        val state = _uiState.value
        val id = state.transactionId?.takeIf { state.canDelete } ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, error = null) }
            runCatching {
                executeFinancialCommand(
                    FinancialCommand.DeleteTransaction(
                        commandId = UUID.randomUUID().toString(),
                        transactionId = id,
                    ),
                )
            }.onSuccess { result ->
                when (result) {
                    is FinancialCommandResult.Success ->
                        _uiState.update { it.copy(isSaving = false, saved = true) }
                    is FinancialCommandResult.Rejected ->
                        _uiState.update { it.copy(isSaving = false, error = result.toUserMessage()) }
                }
            }.onFailure {
                _uiState.update { it.copy(isSaving = false, error = "No pudimos eliminar el movimiento.") }
            }
        }
    }
}

private fun String.hasLinkedProduct(): Boolean =
    startsWith("purchase-") || startsWith("card-payment-") ||
        startsWith("savings-") || startsWith("loan-payment-")

private fun Long.toDisplayDate(): String {
    val date = Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).toLocalDate()
    return date.toDisplayDate()
}

private fun LocalDate.toDisplayDate(): String = "%02d/%02d/%04d".format(dayOfMonth, monthValue, year)

private fun String.parseDisplayDate(): Long? = runCatching {
    val parts = split("/")
    LocalDate.of(parts[2].toInt(), parts[1].toInt(), parts[0].toInt())
        .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
}.getOrNull()
