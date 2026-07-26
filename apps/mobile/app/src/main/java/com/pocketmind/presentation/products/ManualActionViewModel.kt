package com.pocketmind.presentation.products

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pocketmind.shared.domain.model.CreditCardPayment
import com.pocketmind.shared.domain.model.CurrencyCode
import com.pocketmind.shared.domain.model.FinancialAccount
import com.pocketmind.shared.domain.model.FinancialAccountType
import com.pocketmind.shared.domain.model.FinancialTransaction
import com.pocketmind.shared.domain.model.InstallmentPurchase
import com.pocketmind.shared.domain.model.Money
import com.pocketmind.shared.domain.model.SavingsMovement
import com.pocketmind.shared.domain.model.SavingsMovementType
import com.pocketmind.shared.domain.model.TransactionCategoryId
import com.pocketmind.shared.domain.model.TransactionSource
import com.pocketmind.shared.domain.model.TransactionType
import com.pocketmind.shared.domain.model.calculateCreditCardOverview
import com.pocketmind.shared.domain.model.calculateSavingsProjection
import com.pocketmind.shared.domain.usecase.ManualFinanceUseCases
import com.pocketmind.shared.domain.usecase.ObserveActiveFinancialAccountsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ManualActionType {
    CARD_PURCHASE,
    CARD_PAYMENT,
    SAVINGS_DEPOSIT,
    SAVINGS_WITHDRAWAL,
    SAVINGS_RATE,
}

data class ManualActionUiState(
    val accountId: String,
    val action: ManualActionType,
    val accounts: List<FinancialAccount> = emptyList(),
    val merchant: String = "",
    val category: TransactionCategoryId = TransactionCategoryId.SHOPPING,
    val amount: String = "",
    val installments: String = "1",
    val annualRate: String = "",
    val date: String = todayText(),
    val firstPaymentDate: String = nextMonthText(),
    val sourceAccountId: String = "",
    val note: String = "",
    val isSaving: Boolean = false,
    val saved: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class ManualActionViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val manualFinance: ManualFinanceUseCases,
    private val observeAccounts: ObserveActiveFinancialAccountsUseCase,
) : ViewModel() {
    private val accountId: String = checkNotNull(savedStateHandle["accountId"])
    private val action = ManualActionType.valueOf(checkNotNull(savedStateHandle["action"]))
    private val _uiState = MutableStateFlow(ManualActionUiState(accountId, action))
    val uiState: StateFlow<ManualActionUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val accounts = observeAccounts().first()
            val cardProfile = if (action == ManualActionType.CARD_PURCHASE) {
                manualFinance.getCreditCardProfile(accountId)
            } else {
                null
            }
            val profileRate = when (action) {
                ManualActionType.CARD_PURCHASE -> cardProfile?.annualInterestBasisPoints
                ManualActionType.SAVINGS_RATE -> manualFinance
                    .getSavingsProfile(accountId)?.annualYieldBasisPoints
                else -> null
            }
            _uiState.update { current ->
                current.copy(
                    accounts = accounts.filter { account ->
                        account.id != accountId &&
                            account.type != FinancialAccountType.CREDIT_CARD &&
                            account.type != FinancialAccountType.LOAN
                    },
                    annualRate = profileRate?.div(100.0)?.toString().orEmpty(),
                    firstPaymentDate = cardProfile?.let {
                        estimatedCardDueDate(it.statementClosingDay, it.paymentDueDay)
                    } ?: current.firstPaymentDate,
                )
            }
        }
    }

    fun update(transform: (ManualActionUiState) -> ManualActionUiState) = _uiState.update(transform)

    fun save() {
        val state = _uiState.value
        val amount = state.amount.toLongOrNull()
        val occurredAt = state.date.parseDateMillis()
        if (state.action != ManualActionType.SAVINGS_RATE && (amount == null || amount <= 0)) {
            _uiState.update { it.copy(error = "Agrega un valor mayor que cero.") }
            return
        }
        if (occurredAt == null) {
            _uiState.update { it.copy(error = "Usa una fecha válida en formato dd/mm/aaaa.") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, error = null) }
            runCatching {
                when (state.action) {
                    ManualActionType.CARD_PURCHASE -> savePurchase(state, amount!!, occurredAt)
                    ManualActionType.CARD_PAYMENT -> saveCardPayment(state, amount!!, occurredAt)
                    ManualActionType.SAVINGS_DEPOSIT -> saveSavingsMovement(state, amount!!, occurredAt, SavingsMovementType.DEPOSIT)
                    ManualActionType.SAVINGS_WITHDRAWAL -> saveSavingsMovement(state, amount!!, occurredAt, SavingsMovementType.WITHDRAWAL)
                    ManualActionType.SAVINGS_RATE -> saveRateChange(state, occurredAt)
                }
            }.onSuccess {
                _uiState.update { it.copy(isSaving = false, saved = true) }
            }.onFailure { error ->
                val message = when (error.message) {
                    "Purchase exceeds available credit" -> "La compra supera el cupo disponible de la tarjeta."
                    "Payment exceeds debt" -> "El pago no puede superar la deuda actual."
                    "Withdrawal exceeds savings" -> "El retiro no puede superar el ahorro disponible."
                    "Missing card profile", "Missing savings profile" ->
                        "Edita primero este producto para completar sus datos."
                    else -> "No pudimos guardar. Revisa los datos e inténtalo de nuevo."
                }
                _uiState.update { it.copy(isSaving = false, error = message) }
            }
        }
    }

    private suspend fun savePurchase(state: ManualActionUiState, amount: Long, occurredAt: Long) {
        val installments = state.installments.toIntOrNull()?.takeIf { it in 1..60 }
            ?: error("Invalid installments")
        val firstPayment = state.firstPaymentDate.parseDateMillis() ?: error("Invalid first payment")
        val rate = state.annualRate.asBasisPoints()
        val merchant = state.merchant.trim().takeIf(String::isNotBlank) ?: error("Merchant required")
        val id = UUID.randomUUID().toString()
        val purchase = InstallmentPurchase(
                id = id,
                accountId = state.accountId,
                merchant = merchant,
                principal = Money(amount, CurrencyCode.COP),
                installmentCount = installments,
                annualInterestBasisPoints = rate,
                purchasedAtEpochMillis = occurredAt,
                firstPaymentAtEpochMillis = firstPayment,
                categoryId = state.category.name,
                note = state.note.trim().ifBlank { null },
            )
        val account = observeAccounts().first().first { it.id == state.accountId }
        val profile = manualFinance.getCreditCardProfile(state.accountId)
            ?: error("Missing card profile")
        val overview = calculateCreditCardOverview(
            profile,
            account.openingBalance,
            manualFinance.observeInstallmentPurchases().first(),
            manualFinance.observeCreditCardPayments().first(),
        )
        require(purchase.financedTotal.minorUnits <= overview.availableCredit.minorUnits) {
            "Purchase exceeds available credit"
        }
        manualFinance.recordPurchase(
            purchase,
            FinancialTransaction(
                id = "purchase-$id",
                accountId = state.accountId,
                type = TransactionType.EXPENSE,
                amount = Money(amount, CurrencyCode.COP),
                occurredAtEpochMillis = occurredAt,
                categoryId = state.category.name,
                merchant = merchant,
                note = "${installments} cuotas. ${state.note}".trim(),
                source = TransactionSource.MANUAL,
            ),
        )
    }

    private suspend fun saveCardPayment(state: ManualActionUiState, amount: Long, occurredAt: Long) {
        val account = observeAccounts().first().first { it.id == state.accountId }
        val profile = manualFinance.getCreditCardProfile(state.accountId)
            ?: error("Missing card profile")
        val overview = calculateCreditCardOverview(
            profile,
            account.openingBalance,
            manualFinance.observeInstallmentPurchases().first(),
            manualFinance.observeCreditCardPayments().first(),
        )
        require(amount <= overview.currentDebt.minorUnits) { "Payment exceeds debt" }
        val id = UUID.randomUUID().toString()
        val payment = CreditCardPayment(
                id = id,
                accountId = state.accountId,
                amount = Money(amount, CurrencyCode.COP),
                paidAtEpochMillis = occurredAt,
                sourceAccountId = state.sourceAccountId.ifBlank { null },
                note = state.note.trim().ifBlank { null },
            )
        val ledgerTransaction = state.sourceAccountId.takeIf(String::isNotBlank)?.let { sourceAccountId ->
                FinancialTransaction(
                    id = "card-payment-$id",
                    accountId = sourceAccountId,
                    type = TransactionType.TRANSFER,
                    amount = Money(amount, CurrencyCode.COP),
                    occurredAtEpochMillis = occurredAt,
                    categoryId = TransactionCategoryId.DEBT_PAYMENT.name,
                    merchant = "Pago de tarjeta",
                    note = state.note.trim().ifBlank { null },
                    source = TransactionSource.MANUAL,
                    relatedAccountId = state.accountId,
                )
        } ?:
            FinancialTransaction(
                id = "card-payment-$id",
                accountId = state.accountId,
                type = TransactionType.INCOME,
                amount = Money(amount, CurrencyCode.COP),
                occurredAtEpochMillis = occurredAt,
                categoryId = TransactionCategoryId.DEBT_PAYMENT.name,
                merchant = "Pago de tarjeta",
                note = state.note.trim().ifBlank { null },
                source = TransactionSource.MANUAL,
            )
        manualFinance.recordCardPayment(
            payment,
            ledgerTransaction,
        )
    }

    private suspend fun saveSavingsMovement(
        state: ManualActionUiState,
        amount: Long,
        occurredAt: Long,
        type: SavingsMovementType,
    ) {
        if (type == SavingsMovementType.WITHDRAWAL) {
            val account = observeAccounts().first().first { it.id == state.accountId }
            val profile = manualFinance.getSavingsProfile(state.accountId)
                ?: error("Missing savings profile")
            val projection = calculateSavingsProjection(
                profile,
                account.openingBalance,
                manualFinance.observeSavingsMovements().first(),
                occurredAt,
            )
            require(amount <= projection.currentBalance.minorUnits) { "Withdrawal exceeds savings" }
        }
        val id = UUID.randomUUID().toString()
        val movement = SavingsMovement(
                id = id,
                accountId = state.accountId,
                type = type,
                amount = Money(amount, CurrencyCode.COP),
                annualYieldBasisPoints = null,
                occurredAtEpochMillis = occurredAt,
                note = state.note.trim().ifBlank { null },
            )
        val relatedAccount = state.sourceAccountId.takeIf(String::isNotBlank)
        val ledgerTransaction = if (relatedAccount == null) {
                FinancialTransaction(
                    id = "savings-$id",
                    accountId = state.accountId,
                    type = if (type == SavingsMovementType.DEPOSIT) TransactionType.INCOME else TransactionType.EXPENSE,
                    amount = Money(amount, CurrencyCode.COP),
                    occurredAtEpochMillis = occurredAt,
                    categoryId = TransactionCategoryId.SAVINGS.name,
                    merchant = if (type == SavingsMovementType.DEPOSIT) "Aporte al ahorro" else "Retiro del ahorro",
                    note = state.note.trim().ifBlank { null },
                    source = TransactionSource.MANUAL,
                )
            } else {
                FinancialTransaction(
                    id = "savings-$id",
                    accountId = if (type == SavingsMovementType.DEPOSIT) relatedAccount else state.accountId,
                    type = TransactionType.TRANSFER,
                    amount = Money(amount, CurrencyCode.COP),
                    occurredAtEpochMillis = occurredAt,
                    categoryId = TransactionCategoryId.SAVINGS.name,
                    merchant = if (type == SavingsMovementType.DEPOSIT) "Aporte al ahorro" else "Retiro del ahorro",
                    note = state.note.trim().ifBlank { null },
                    source = TransactionSource.MANUAL,
                    relatedAccountId = if (type == SavingsMovementType.DEPOSIT) state.accountId else relatedAccount,
                )
            }
        manualFinance.recordSavingsMovement(
            movement,
            ledgerTransaction,
        )
    }

    private suspend fun saveRateChange(state: ManualActionUiState, occurredAt: Long) {
        val rate = state.annualRate.asBasisPoints()
        manualFinance.recordSavingsMovement(
            SavingsMovement(
                id = UUID.randomUUID().toString(),
                accountId = state.accountId,
                type = SavingsMovementType.RATE_CHANGE,
                amount = Money(0, CurrencyCode.COP),
                annualYieldBasisPoints = rate,
                occurredAtEpochMillis = occurredAt,
                note = state.note.trim().ifBlank { null },
            ),
            ledgerTransaction = null,
        )
    }
}

private fun String.asBasisPoints(): Int = replace(",", ".").toDoubleOrNull()
    ?.takeIf { it >= 0 }
    ?.times(100)
    ?.toInt()
    ?: error("Invalid rate")

private fun String.parseDateMillis(): Long? = runCatching {
    val parts = split("/")
    LocalDate.of(parts[2].toInt(), parts[1].toInt(), parts[0].toInt())
        .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
}.getOrNull()

private fun LocalDate.formatText(): String = "%02d/%02d/%04d".format(dayOfMonth, monthValue, year)
private fun todayText(): String = LocalDate.now().formatText()
private fun nextMonthText(): String = LocalDate.now().plusMonths(1).formatText()

private fun estimatedCardDueDate(closingDay: Int, paymentDay: Int): String {
    val today = LocalDate.now()
    val monthsToAdd = if (today.dayOfMonth <= closingDay) 1L else 2L
    val dueMonth = today.plusMonths(monthsToAdd).withDayOfMonth(1)
    return dueMonth.withDayOfMonth(paymentDay.coerceAtMost(dueMonth.lengthOfMonth())).formatText()
}
