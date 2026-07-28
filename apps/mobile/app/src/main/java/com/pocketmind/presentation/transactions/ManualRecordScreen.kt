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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
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
import com.pocketmind.presentation.common.toUserMessage
import com.pocketmind.shared.domain.command.FinancialCommand
import com.pocketmind.shared.domain.command.FinancialCommandResult
import com.pocketmind.shared.domain.model.FinancialAccount
import com.pocketmind.shared.domain.model.FinancialAccountType
import com.pocketmind.shared.domain.model.Money
import com.pocketmind.shared.domain.model.SavingsMovementType
import com.pocketmind.shared.domain.model.TransactionCategoryId
import com.pocketmind.shared.domain.model.TransactionSource
import com.pocketmind.shared.domain.model.calculateCreditCardOverview
import com.pocketmind.shared.domain.usecase.ExecuteFinancialCommandUseCase
import com.pocketmind.shared.domain.usecase.ManualFinanceUseCases
import com.pocketmind.shared.domain.usecase.ObserveActiveFinancialAccountsUseCase
import com.pocketmind.ui.components.PocketMessage
import com.pocketmind.ui.components.PocketContextTopBar
import com.pocketmind.ui.components.PocketPrimaryButton
import com.pocketmind.ui.components.pocketBringIntoViewOnFocus
import com.pocketmind.ui.theme.PocketSpacing
import dagger.hilt.android.lifecycle.HiltViewModel
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
    private val executeFinancialCommand: ExecuteFinancialCommandUseCase,
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
        val product = state.products.firstOrNull { it.id == state.productId }
        when {
            state.productId.isBlank() -> return showError("Elige el producto del movimiento.")
            product == null -> return showError("No encontramos el producto seleccionado.")
            amount == null -> return showError("Agrega un valor mayor que cero.")
            occurredAt == null -> return showError("Usa una fecha válida en formato dd/mm/aaaa.")
            state.operation == ManualRecordType.CARD_PURCHASE && state.merchant.isBlank() ->
                return showError("Escribe el comercio de la compra.")
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, error = null) }
            runCatching {
                executeFinancialCommand(
                    state.toCommand(
                        commandId = UUID.randomUUID().toString(),
                        amount = Money(amount, product.currency),
                        occurredAtEpochMillis = occurredAt,
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
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        error = "No pudimos guardar. Revisa los datos e inténtalo de nuevo.",
                    )
                }
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

    private fun showError(message: String) {
        _uiState.update { it.copy(error = message) }
    }
}

private fun ManualRecordUiState.toCommand(
    commandId: String,
    amount: Money,
    occurredAtEpochMillis: Long,
): FinancialCommand = when (operation) {
    ManualRecordType.INCOME -> FinancialCommand.RecordIncome(
        commandId = commandId,
        productId = productId,
        amount = amount,
        occurredAtEpochMillis = occurredAtEpochMillis,
        source = TransactionSource.MANUAL,
        categoryId = category.name,
        merchant = merchant,
        note = note,
    )
    ManualRecordType.EXPENSE -> FinancialCommand.RecordExpense(
        commandId = commandId,
        productId = productId,
        amount = amount,
        occurredAtEpochMillis = occurredAtEpochMillis,
        source = TransactionSource.MANUAL,
        categoryId = category.name,
        merchant = merchant,
        note = note,
    )
    ManualRecordType.CARD_PURCHASE -> FinancialCommand.RecordCardPurchase(
        commandId = commandId,
        cardId = productId,
        merchant = merchant,
        principal = amount,
        installmentCount = installments.toIntOrNull() ?: 0,
        purchasedAtEpochMillis = occurredAtEpochMillis,
        source = TransactionSource.MANUAL,
        categoryId = category.name,
        note = note,
    )
    ManualRecordType.CARD_PAYMENT -> FinancialCommand.RecordCardPayment(
        commandId = commandId,
        cardId = productId,
        amount = amount,
        paidAtEpochMillis = occurredAtEpochMillis,
        source = TransactionSource.MANUAL,
        sourceProductId = sourceProductId,
        note = note,
    )
    ManualRecordType.SAVINGS_DEPOSIT,
    ManualRecordType.SAVINGS_WITHDRAWAL -> FinancialCommand.RecordSavingsMovement(
        commandId = commandId,
        savingsId = productId,
        movementType = if (operation == ManualRecordType.SAVINGS_DEPOSIT) {
            SavingsMovementType.DEPOSIT
        } else {
            SavingsMovementType.WITHDRAWAL
        },
        amount = amount,
        occurredAtEpochMillis = occurredAtEpochMillis,
        source = TransactionSource.MANUAL,
        sourceProductId = sourceProductId.takeIf {
            operation == ManualRecordType.SAVINGS_DEPOSIT
        },
        destinationProductId = sourceProductId.takeIf {
            operation == ManualRecordType.SAVINGS_WITHDRAWAL
        },
        note = note,
    )
    ManualRecordType.LOAN_PAYMENT -> FinancialCommand.RecordLoanPayment(
        commandId = commandId,
        loanId = productId,
        amount = amount,
        paidAtEpochMillis = occurredAtEpochMillis,
        source = TransactionSource.MANUAL,
        sourceProductId = sourceProductId,
        note = note,
    )
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
        PocketContextTopBar(
            title = stringResource(R.string.transaction_editor_create_title),
            onBack = onBack,
            backContentDescription = stringResource(R.string.accounts_back),
        )
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
