package com.pocketmind.presentation.transactions

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.pocketmind.R
import com.pocketmind.shared.domain.model.CreditCardPayment
import com.pocketmind.shared.domain.model.CurrencyCode
import com.pocketmind.shared.domain.model.FinancialAccount
import com.pocketmind.shared.domain.model.FinancialAccountType
import com.pocketmind.shared.domain.model.FinancialTransaction
import com.pocketmind.shared.domain.model.InstallmentPurchase
import com.pocketmind.shared.domain.model.LoanPayment
import com.pocketmind.shared.domain.model.Money
import com.pocketmind.shared.domain.model.SavingsMovement
import com.pocketmind.shared.domain.model.SavingsMovementType
import com.pocketmind.shared.domain.model.TransactionCategoryId
import com.pocketmind.shared.domain.model.TransactionSource
import com.pocketmind.shared.domain.model.TransactionType
import com.pocketmind.shared.domain.model.calculateCreditCardOverview
import com.pocketmind.shared.domain.model.calculateLoanOverview
import com.pocketmind.shared.domain.model.calculateSavingsProjection
import com.pocketmind.shared.domain.usecase.CreateTransactionResult
import com.pocketmind.shared.domain.usecase.CreateTransactionUseCase
import com.pocketmind.shared.domain.usecase.ManualFinanceUseCases
import com.pocketmind.shared.domain.usecase.NewTransaction
import com.pocketmind.shared.domain.usecase.ObserveActiveFinancialAccountsUseCase
import com.pocketmind.ui.components.PocketMessage
import com.pocketmind.ui.components.PocketPrimaryButton
import com.pocketmind.ui.components.pocketBringIntoViewOnFocus
import com.pocketmind.ui.theme.PocketSpacing
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

enum class ManualRecordType {
    INCOME,
    EXPENSE,
    CARD_PURCHASE,
    CARD_PAYMENT,
    SAVINGS_DEPOSIT,
    SAVINGS_WITHDRAWAL,
    LOAN_PAYMENT,
}

data class ManualRecordUiState(
    val operation: ManualRecordType = ManualRecordType.EXPENSE,
    val products: List<FinancialAccount> = emptyList(),
    val productId: String = "",
    val merchant: String = "",
    val category: TransactionCategoryId = TransactionCategoryId.SHOPPING,
    val amount: String = "",
    val installments: String = "1",
    val date: String = todayText(),
    val sourceProductId: String = "",
    val note: String = "",
    val isSaving: Boolean = false,
    val saved: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class ManualRecordViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val observeProducts: ObserveActiveFinancialAccountsUseCase,
    private val manualFinance: ManualFinanceUseCases,
    private val createTransaction: CreateTransactionUseCase,
) : ViewModel() {
    private val initialOperation = savedStateHandle.get<String>("operation")
        ?.let { runCatching { ManualRecordType.valueOf(it) }.getOrNull() }
        ?: ManualRecordType.EXPENSE
    private val initialProductId = savedStateHandle.get<String>("productId").orEmpty()
    private val _uiState = MutableStateFlow(
        ManualRecordUiState(
            operation = initialOperation,
            productId = initialProductId,
            category = initialOperation.defaultCategory(),
        ),
    )
    val uiState: StateFlow<ManualRecordUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            observeProducts().collect { products ->
                val firstLoad = _uiState.value.products.isEmpty()
                _uiState.update { state ->
                    val selectedIsCompatible = products.any {
                        it.id == state.productId && it.isCompatibleWith(state.operation)
                    }
                    state.copy(
                        products = products,
                        productId = state.productId.takeIf { selectedIsCompatible }.orEmpty(),
                    )
                }
                if (firstLoad && _uiState.value.productId.isNotBlank()) {
                    prefillSuggestedAmount()
                }
            }
        }
    }

    fun selectOperation(operation: ManualRecordType) {
        _uiState.update {
            ManualRecordUiState(
                operation = operation,
                products = it.products,
                category = operation.defaultCategory(),
            )
        }
    }

    fun selectProduct(productId: String) {
        _uiState.update { it.copy(productId = productId, amount = "", sourceProductId = "", error = null) }
        prefillSuggestedAmount()
    }

    fun update(transform: (ManualRecordUiState) -> ManualRecordUiState) {
        _uiState.update { transform(it).copy(error = null) }
    }

    fun save() {
        val state = _uiState.value
        val amount = state.amount.toLongOrNull()?.takeIf { it > 0 }
        val occurredAt = state.date.parseDateMillis()
        when {
            state.productId.isBlank() -> return showError("Elige el producto del movimiento.")
            amount == null -> return showError("Agrega un valor mayor que cero.")
            occurredAt == null -> return showError("Usa una fecha válida en formato dd/mm/aaaa.")
            state.operation == ManualRecordType.CARD_PURCHASE && state.merchant.isBlank() ->
                return showError("Escribe el comercio de la compra.")
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, error = null) }
            runCatching {
                when (state.operation) {
                    ManualRecordType.INCOME, ManualRecordType.EXPENSE ->
                        saveStandardMovement(state, amount, occurredAt)
                    ManualRecordType.CARD_PURCHASE -> savePurchase(state, amount, occurredAt)
                    ManualRecordType.CARD_PAYMENT -> saveCardPayment(state, amount, occurredAt)
                    ManualRecordType.SAVINGS_DEPOSIT -> saveSavingsMovement(
                        state,
                        amount,
                        occurredAt,
                        SavingsMovementType.DEPOSIT,
                    )
                    ManualRecordType.SAVINGS_WITHDRAWAL -> saveSavingsMovement(
                        state,
                        amount,
                        occurredAt,
                        SavingsMovementType.WITHDRAWAL,
                    )
                    ManualRecordType.LOAN_PAYMENT -> saveLoanPayment(state, amount, occurredAt)
                }
            }.onSuccess {
                _uiState.update { it.copy(isSaving = false, saved = true) }
            }.onFailure { error ->
                val message = when (error.message) {
                    "Purchase exceeds available credit" -> "La compra supera el cupo disponible."
                    "Payment exceeds debt" -> "El pago no puede superar la deuda actual."
                    "Withdrawal exceeds savings" -> "El retiro no puede superar el ahorro disponible."
                    "Loan payment exceeds debt" -> "El abono no puede superar la deuda actual."
                    "Missing card profile", "Missing savings profile", "Missing loan profile" ->
                        "Completa primero los datos de este producto desde Editar."
                    "Invalid installments" -> "Usa entre 1 y 60 cuotas."
                    else -> "No pudimos guardar. Revisa los datos e inténtalo de nuevo."
                }
                _uiState.update { it.copy(isSaving = false, error = message) }
            }
        }
    }

    private fun prefillSuggestedAmount() {
        val state = _uiState.value
        if (state.productId.isBlank()) return
        viewModelScope.launch {
            val amount = when (state.operation) {
                ManualRecordType.CARD_PAYMENT -> {
                    val profile = manualFinance.getCreditCardProfile(state.productId)
                    val account = observeProducts().first().firstOrNull { it.id == state.productId }
                    if (profile != null && account != null) {
                        calculateCreditCardOverview(
                            profile,
                            account.openingBalance,
                            manualFinance.observeInstallmentPurchases().first(),
                            manualFinance.observeCreditCardPayments().first(),
                        ).nextPayment.minorUnits
                    } else {
                        null
                    }
                }
                ManualRecordType.LOAN_PAYMENT ->
                    manualFinance.getLoanProfile(state.productId)?.monthlyPayment?.minorUnits
                else -> null
            }
            if (amount != null && amount > 0 && _uiState.value.productId == state.productId) {
                _uiState.update { it.copy(amount = amount.toString()) }
            }
        }
    }

    private suspend fun saveStandardMovement(
        state: ManualRecordUiState,
        amount: Long,
        occurredAt: Long,
    ) {
        val result = createTransaction(
            NewTransaction(
                id = UUID.randomUUID().toString(),
                accountId = state.productId,
                type = if (state.operation == ManualRecordType.INCOME) {
                    TransactionType.INCOME
                } else {
                    TransactionType.EXPENSE
                },
                amount = Money(amount, CurrencyCode.COP),
                occurredAtEpochMillis = occurredAt,
                source = TransactionSource.MANUAL,
                categoryId = state.category.name,
                merchant = state.merchant,
                note = state.note,
            ),
        )
        check(result is CreateTransactionResult.Success) { "Invalid movement" }
    }

    private suspend fun savePurchase(
        state: ManualRecordUiState,
        amount: Long,
        occurredAt: Long,
    ) {
        val installments = state.installments.toIntOrNull()?.takeIf { it in 1..60 }
            ?: error("Invalid installments")
        val merchant = state.merchant.trim()
        val profile = manualFinance.getCreditCardProfile(state.productId)
            ?: error("Missing card profile")
        val account = observeProducts().first().first { it.id == state.productId }
        val overview = calculateCreditCardOverview(
            profile,
            account.openingBalance,
            manualFinance.observeInstallmentPurchases().first(),
            manualFinance.observeCreditCardPayments().first(),
        )
        val id = UUID.randomUUID().toString()
        val purchase = InstallmentPurchase(
            id = id,
            accountId = state.productId,
            merchant = merchant,
            principal = Money(amount, CurrencyCode.COP),
            installmentCount = installments,
            annualInterestBasisPoints = profile.annualInterestBasisPoints,
            purchasedAtEpochMillis = occurredAt,
            firstPaymentAtEpochMillis = estimatedCardDueDate(
                occurredAt,
                profile.statementClosingDay,
                profile.paymentDueDay,
            ),
            categoryId = state.category.name,
            note = state.note.trim().ifBlank { null },
        )
        require(purchase.financedTotal.minorUnits <= overview.availableCredit.minorUnits) {
            "Purchase exceeds available credit"
        }
        manualFinance.recordPurchase(
            purchase,
            FinancialTransaction(
                id = "purchase-$id",
                accountId = state.productId,
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

    private suspend fun saveCardPayment(
        state: ManualRecordUiState,
        amount: Long,
        occurredAt: Long,
    ) {
        val account = observeProducts().first().first { it.id == state.productId }
        val profile = manualFinance.getCreditCardProfile(state.productId)
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
            accountId = state.productId,
            amount = Money(amount, CurrencyCode.COP),
            paidAtEpochMillis = occurredAt,
            sourceAccountId = state.sourceProductId.ifBlank { null },
            note = state.note.trim().ifBlank { null },
        )
        val ledger = state.sourceProductId.takeIf(String::isNotBlank)?.let { sourceId ->
            FinancialTransaction(
                id = "card-payment-$id",
                accountId = sourceId,
                type = TransactionType.TRANSFER,
                amount = Money(amount, CurrencyCode.COP),
                occurredAtEpochMillis = occurredAt,
                categoryId = TransactionCategoryId.DEBT_PAYMENT.name,
                merchant = "Pago de tarjeta",
                note = state.note.trim().ifBlank { null },
                source = TransactionSource.MANUAL,
                relatedAccountId = state.productId,
            )
        } ?: FinancialTransaction(
            id = "card-payment-$id",
            accountId = state.productId,
            type = TransactionType.INCOME,
            amount = Money(amount, CurrencyCode.COP),
            occurredAtEpochMillis = occurredAt,
            categoryId = TransactionCategoryId.DEBT_PAYMENT.name,
            merchant = "Pago de tarjeta",
            note = state.note.trim().ifBlank { null },
            source = TransactionSource.MANUAL,
        )
        manualFinance.recordCardPayment(payment, ledger)
    }

    private suspend fun saveSavingsMovement(
        state: ManualRecordUiState,
        amount: Long,
        occurredAt: Long,
        type: SavingsMovementType,
    ) {
        if (type == SavingsMovementType.WITHDRAWAL) {
            val account = observeProducts().first().first { it.id == state.productId }
            val profile = manualFinance.getSavingsProfile(state.productId)
                ?: error("Missing savings profile")
            val projection = calculateSavingsProjection(
                profile,
                account.openingBalance,
                manualFinance.observeSavingsMovements().first(),
                occurredAt,
            )
            require(amount <= projection.currentBalance.minorUnits) {
                "Withdrawal exceeds savings"
            }
        }
        val id = UUID.randomUUID().toString()
        val movement = SavingsMovement(
            id = id,
            accountId = state.productId,
            type = type,
            amount = Money(amount, CurrencyCode.COP),
            annualYieldBasisPoints = null,
            occurredAtEpochMillis = occurredAt,
            note = state.note.trim().ifBlank { null },
        )
        val relatedProduct = state.sourceProductId.takeIf(String::isNotBlank)
        val ledger = if (relatedProduct == null) {
            FinancialTransaction(
                id = "savings-$id",
                accountId = state.productId,
                type = if (type == SavingsMovementType.DEPOSIT) {
                    TransactionType.INCOME
                } else {
                    TransactionType.EXPENSE
                },
                amount = Money(amount, CurrencyCode.COP),
                occurredAtEpochMillis = occurredAt,
                categoryId = TransactionCategoryId.SAVINGS.name,
                merchant = if (type == SavingsMovementType.DEPOSIT) {
                    "Aporte al ahorro"
                } else {
                    "Retiro del ahorro"
                },
                note = state.note.trim().ifBlank { null },
                source = TransactionSource.MANUAL,
            )
        } else {
            FinancialTransaction(
                id = "savings-$id",
                accountId = if (type == SavingsMovementType.DEPOSIT) relatedProduct else state.productId,
                type = TransactionType.TRANSFER,
                amount = Money(amount, CurrencyCode.COP),
                occurredAtEpochMillis = occurredAt,
                categoryId = TransactionCategoryId.SAVINGS.name,
                merchant = if (type == SavingsMovementType.DEPOSIT) {
                    "Aporte al ahorro"
                } else {
                    "Retiro del ahorro"
                },
                note = state.note.trim().ifBlank { null },
                source = TransactionSource.MANUAL,
                relatedAccountId = if (type == SavingsMovementType.DEPOSIT) {
                    state.productId
                } else {
                    relatedProduct
                },
            )
        }
        manualFinance.recordSavingsMovement(movement, ledger)
    }

    private suspend fun saveLoanPayment(
        state: ManualRecordUiState,
        amount: Long,
        occurredAt: Long,
    ) {
        val account = observeProducts().first().first { it.id == state.productId }
        val profile = manualFinance.getLoanProfile(state.productId)
            ?: error("Missing loan profile")
        val overview = calculateLoanOverview(
            profile,
            account.openingBalance,
            manualFinance.observeLoanPayments().first(),
            occurredAt,
        )
        require(amount <= overview.currentDebt.minorUnits) { "Loan payment exceeds debt" }
        val id = UUID.randomUUID().toString()
        val payment = LoanPayment(
            id = id,
            accountId = state.productId,
            amount = Money(amount, CurrencyCode.COP),
            paidAtEpochMillis = occurredAt,
            sourceAccountId = state.sourceProductId.ifBlank { null },
            note = state.note.trim().ifBlank { null },
        )
        val ledger = state.sourceProductId.takeIf(String::isNotBlank)?.let { sourceId ->
            FinancialTransaction(
                id = "loan-payment-$id",
                accountId = sourceId,
                type = TransactionType.TRANSFER,
                amount = Money(amount, CurrencyCode.COP),
                occurredAtEpochMillis = occurredAt,
                categoryId = TransactionCategoryId.DEBT_PAYMENT.name,
                merchant = "Pago de préstamo",
                note = state.note.trim().ifBlank { null },
                source = TransactionSource.MANUAL,
                relatedAccountId = state.productId,
            )
        } ?: FinancialTransaction(
            id = "loan-payment-$id",
            accountId = state.productId,
            type = TransactionType.INCOME,
            amount = Money(amount, CurrencyCode.COP),
            occurredAtEpochMillis = occurredAt,
            categoryId = TransactionCategoryId.DEBT_PAYMENT.name,
            merchant = "Pago de préstamo",
            note = state.note.trim().ifBlank { null },
            source = TransactionSource.MANUAL,
        )
        manualFinance.recordLoanPayment(payment, ledger)
    }

    private fun showError(message: String) {
        _uiState.update { it.copy(error = message) }
    }
}

@Composable
fun ManualRecordRoute(
    onSaved: () -> Unit,
    onBack: () -> Unit,
    viewModel: ManualRecordViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.saved) {
        if (state.saved) onSaved()
    }
    ManualRecordScreen(
        state = state,
        onSelectOperation = viewModel::selectOperation,
        onSelectProduct = viewModel::selectProduct,
        onUpdate = viewModel::update,
        onSave = viewModel::save,
        onBack = onBack,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ManualRecordScreen(
    state: ManualRecordUiState,
    onSelectOperation: (ManualRecordType) -> Unit,
    onSelectProduct: (String) -> Unit,
    onUpdate: ((ManualRecordUiState) -> ManualRecordUiState) -> Unit,
    onSave: () -> Unit,
    onBack: () -> Unit,
) {
    val compatibleProducts = state.products.filter { it.isCompatibleWith(state.operation) }
    val sourceProducts = state.products.filter {
        it.id != state.productId &&
            it.type != FinancialAccountType.CREDIT_CARD &&
            it.type != FinancialAccountType.LOAN
    }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(
            Modifier.fillMaxWidth()
                .background(MaterialTheme.colorScheme.primary)
                .statusBarsPadding()
                .padding(horizontal = PocketSpacing.sm, vertical = PocketSpacing.xxs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Rounded.ArrowBack,
                    stringResource(R.string.accounts_back),
                    tint = MaterialTheme.colorScheme.onPrimary,
                )
            }
            Text(
                stringResource(R.string.transaction_editor_create_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimary,
            )
        }
        Column(
            Modifier.weight(1f)
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = PocketSpacing.lg, vertical = PocketSpacing.md),
            verticalArrangement = Arrangement.spacedBy(PocketSpacing.md),
        ) {
            SelectionDropdown(
                label = stringResource(R.string.manual_record_operation),
                selected = stringResource(state.operation.labelRes()),
                options = ManualRecordType.entries.map { it.name to stringResource(it.labelRes()) },
                onSelect = { onSelectOperation(ManualRecordType.valueOf(it)) },
            )
            SelectionDropdown(
                label = stringResource(R.string.manual_record_product),
                selected = compatibleProducts.firstOrNull { it.id == state.productId }?.name
                    ?: stringResource(R.string.manual_record_choose_product),
                options = compatibleProducts.map { it.id to it.name },
                onSelect = onSelectProduct,
            )
            if (compatibleProducts.isEmpty()) {
                PocketMessage(stringResource(R.string.manual_record_no_products), isError = false)
            }

            if (state.operation.showsMerchant()) {
                FormField(
                    value = state.merchant,
                    onValueChange = { value -> onUpdate { it.copy(merchant = value) } },
                    label = stringResource(
                        if (state.operation == ManualRecordType.CARD_PURCHASE) {
                            R.string.manual_action_merchant
                        } else {
                            R.string.transaction_editor_merchant
                        },
                    ),
                    placeholder = stringResource(
                        if (state.operation == ManualRecordType.CARD_PURCHASE) {
                            R.string.manual_action_merchant_example
                        } else {
                            R.string.transaction_editor_merchant_example
                        },
                    ),
                )
            }
            if (state.operation.showsCategory()) {
                CategoryDropdown(
                    selected = state.category,
                    onSelect = { category -> onUpdate { it.copy(category = category) } },
                )
            }
            FormField(
                value = state.amount,
                onValueChange = { value -> onUpdate { it.copy(amount = value.filter(Char::isDigit)) } },
                label = stringResource(R.string.manual_action_amount),
                placeholder = stringResource(R.string.manual_action_amount_example),
                keyboardType = KeyboardType.Number,
            )
            if (state.operation == ManualRecordType.CARD_PURCHASE) {
                FormField(
                    value = state.installments,
                    onValueChange = { value ->
                        onUpdate { it.copy(installments = value.filter(Char::isDigit).take(2)) }
                    },
                    label = stringResource(R.string.manual_action_installments),
                    placeholder = stringResource(R.string.manual_action_installments_example),
                    keyboardType = KeyboardType.Number,
                )
            }
            FormField(
                value = state.date,
                onValueChange = { value -> onUpdate { it.copy(date = value.dateOnly()) } },
                label = stringResource(R.string.manual_action_date),
                placeholder = stringResource(R.string.account_editor_date_example),
                keyboardType = KeyboardType.Number,
            )
            if (state.operation.showsSourceProduct()) {
                SelectionDropdown(
                    label = stringResource(
                        if (
                            state.operation == ManualRecordType.CARD_PAYMENT ||
                            state.operation == ManualRecordType.LOAN_PAYMENT
                        ) {
                            R.string.manual_action_source
                        } else {
                            R.string.manual_action_savings_source
                        },
                    ),
                    selected = sourceProducts.firstOrNull { it.id == state.sourceProductId }?.name
                        ?: stringResource(R.string.manual_action_no_source),
                    options = listOf("" to stringResource(R.string.manual_action_no_source)) +
                        sourceProducts.map { it.id to it.name },
                    onSelect = { id -> onUpdate { it.copy(sourceProductId = id) } },
                )
            }
            FormField(
                value = state.note,
                onValueChange = { value -> onUpdate { it.copy(note = value) } },
                label = stringResource(R.string.manual_action_note),
                placeholder = stringResource(R.string.manual_action_note_example),
                singleLine = false,
            )
            state.error?.let { PocketMessage(it, isError = true) }
            PocketPrimaryButton(
                text = stringResource(R.string.manual_record_continue),
                onClick = onSave,
                enabled = compatibleProducts.isNotEmpty(),
                loading = state.isSaving,
            )
            Spacer(Modifier.padding(PocketSpacing.xs))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectionDropdown(
    label: String,
    selected: String,
    options: List<Pair<String, String>>,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selected,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, true),
            shape = RoundedCornerShape(16.dp),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            shape = RoundedCornerShape(16.dp),
        ) {
            options.forEach { (id, text) ->
                DropdownMenuItem(
                    text = { Text(text) },
                    onClick = {
                        onSelect(id)
                        expanded = false
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryDropdown(
    selected: TransactionCategoryId,
    onSelect: (TransactionCategoryId) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = stringResource(selected.labelRes()),
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.transaction_editor_category)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, true),
            shape = RoundedCornerShape(16.dp),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            shape = RoundedCornerShape(16.dp),
        ) {
            TransactionCategoryId.entries.forEach { category ->
                DropdownMenuItem(
                    text = { Text(stringResource(category.labelRes())) },
                    onClick = {
                        onSelect(category)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun FormField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    keyboardType: KeyboardType = KeyboardType.Text,
    singleLine: Boolean = true,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = { Text(placeholder) },
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        singleLine = singleLine,
        minLines = if (singleLine) 1 else 3,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().pocketBringIntoViewOnFocus(),
    )
}

private fun FinancialAccount.isCompatibleWith(operation: ManualRecordType): Boolean = when (operation) {
    ManualRecordType.INCOME, ManualRecordType.EXPENSE ->
        type == FinancialAccountType.BANK_ACCOUNT || type == FinancialAccountType.CASH
    ManualRecordType.CARD_PURCHASE, ManualRecordType.CARD_PAYMENT ->
        type == FinancialAccountType.CREDIT_CARD
    ManualRecordType.SAVINGS_DEPOSIT, ManualRecordType.SAVINGS_WITHDRAWAL ->
        type == FinancialAccountType.SAVINGS
    ManualRecordType.LOAN_PAYMENT -> type == FinancialAccountType.LOAN
}

private fun ManualRecordType.defaultCategory(): TransactionCategoryId = when (this) {
    ManualRecordType.INCOME -> TransactionCategoryId.SALARY
    ManualRecordType.EXPENSE, ManualRecordType.CARD_PURCHASE -> TransactionCategoryId.SHOPPING
    ManualRecordType.CARD_PAYMENT, ManualRecordType.LOAN_PAYMENT -> TransactionCategoryId.DEBT_PAYMENT
    ManualRecordType.SAVINGS_DEPOSIT, ManualRecordType.SAVINGS_WITHDRAWAL ->
        TransactionCategoryId.SAVINGS
}

private fun ManualRecordType.showsMerchant(): Boolean =
    this == ManualRecordType.INCOME ||
        this == ManualRecordType.EXPENSE ||
        this == ManualRecordType.CARD_PURCHASE

private fun ManualRecordType.showsCategory(): Boolean =
    this == ManualRecordType.INCOME ||
        this == ManualRecordType.EXPENSE ||
        this == ManualRecordType.CARD_PURCHASE

private fun ManualRecordType.showsSourceProduct(): Boolean =
    this == ManualRecordType.CARD_PAYMENT ||
        this == ManualRecordType.SAVINGS_DEPOSIT ||
        this == ManualRecordType.SAVINGS_WITHDRAWAL ||
        this == ManualRecordType.LOAN_PAYMENT

private fun ManualRecordType.labelRes(): Int = when (this) {
    ManualRecordType.INCOME -> R.string.transactions_income
    ManualRecordType.EXPENSE -> R.string.transactions_expense
    ManualRecordType.CARD_PURCHASE -> R.string.manual_record_card_purchase
    ManualRecordType.CARD_PAYMENT -> R.string.manual_record_card_payment
    ManualRecordType.SAVINGS_DEPOSIT -> R.string.manual_record_savings_deposit
    ManualRecordType.SAVINGS_WITHDRAWAL -> R.string.manual_record_savings_withdrawal
    ManualRecordType.LOAN_PAYMENT -> R.string.manual_record_loan_payment
}

private fun TransactionCategoryId.labelRes(): Int = when (this) {
    TransactionCategoryId.SALARY -> R.string.category_salary
    TransactionCategoryId.FREELANCE -> R.string.category_freelance
    TransactionCategoryId.TRANSFER -> R.string.category_transfer
    TransactionCategoryId.FOOD -> R.string.category_food
    TransactionCategoryId.TRANSPORT -> R.string.category_transport
    TransactionCategoryId.HOME -> R.string.category_home
    TransactionCategoryId.HEALTH -> R.string.category_health
    TransactionCategoryId.EDUCATION -> R.string.category_education
    TransactionCategoryId.ENTERTAINMENT -> R.string.category_entertainment
    TransactionCategoryId.SHOPPING -> R.string.category_shopping
    TransactionCategoryId.SERVICES -> R.string.category_services
    TransactionCategoryId.DEBT_PAYMENT -> R.string.category_debt_payment
    TransactionCategoryId.SAVINGS -> R.string.category_savings
    TransactionCategoryId.OTHER -> R.string.category_other
}

private fun String.dateOnly(): String = filter { it.isDigit() || it == '/' }.take(10)

private fun String.parseDateMillis(): Long? = runCatching {
    val parts = split("/")
    LocalDate.of(parts[2].toInt(), parts[1].toInt(), parts[0].toInt())
        .atStartOfDay(ZoneId.systemDefault())
        .toInstant()
        .toEpochMilli()
}.getOrNull()

private fun LocalDate.formatText(): String =
    "%02d/%02d/%04d".format(dayOfMonth, monthValue, year)

private fun todayText(): String = LocalDate.now().formatText()

internal fun estimatedCardDueDate(
    purchasedAtEpochMillis: Long,
    closingDay: Int,
    paymentDay: Int,
): Long {
    val purchaseDate = Instant.ofEpochMilli(purchasedAtEpochMillis)
        .atZone(ZoneId.systemDefault())
        .toLocalDate()
    val monthsToAdd = if (purchaseDate.dayOfMonth <= closingDay) 1L else 2L
    val dueMonth = purchaseDate.plusMonths(monthsToAdd).withDayOfMonth(1)
    return dueMonth.withDayOfMonth(paymentDay.coerceAtMost(dueMonth.lengthOfMonth()))
        .atStartOfDay(ZoneId.systemDefault())
        .toInstant()
        .toEpochMilli()
}
