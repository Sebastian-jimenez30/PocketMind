package com.pocketmind.presentation.transactions

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Fastfood
import androidx.compose.material.icons.rounded.HealthAndSafety
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LocalMovies
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.ShoppingBag
import androidx.compose.material.icons.rounded.Work
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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
import com.pocketmind.ui.components.PocketChoiceChip
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

data class ManualRecordUiState(
    val operation: ManualRecordType = ManualRecordType.EXPENSE,
    val products: List<FinancialAccount> = emptyList(),
    val productId: String = "",
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
                        it.id == state.productId && it.type.isCompatibleWith(state.operation)
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

    fun selectGroup(group: ManualRecordGroup) {
        selectOperation(group.defaultOperation())
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
            state.operation.requiresRelatedProduct() && state.sourceProductId.isBlank() ->
                return showError("Elige el producto de destino.")
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
        merchant = null,
        note = note,
    )
    ManualRecordType.EXPENSE -> FinancialCommand.RecordExpense(
        commandId = commandId,
        productId = productId,
        amount = amount,
        occurredAtEpochMillis = occurredAtEpochMillis,
        source = TransactionSource.MANUAL,
        categoryId = category.name,
        merchant = null,
        note = note,
    )
    ManualRecordType.TRANSFER -> FinancialCommand.Transfer(
        commandId = commandId,
        sourceProductId = productId,
        destinationProductId = sourceProductId,
        amount = amount,
        occurredAtEpochMillis = occurredAtEpochMillis,
        source = TransactionSource.MANUAL,
        categoryId = TransactionCategoryId.TRANSFER.name,
        note = note,
    )
    ManualRecordType.CARD_PURCHASE -> FinancialCommand.RecordCardPurchase(
        commandId = commandId,
        cardId = productId,
        merchant = "Compra con tarjeta",
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
        onSelectGroup = viewModel::selectGroup,
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
    onSelectGroup: (ManualRecordGroup) -> Unit,
    onSelectOperation: (ManualRecordType) -> Unit,
    onSelectProduct: (String) -> Unit,
    onUpdate: ((ManualRecordUiState) -> ManualRecordUiState) -> Unit,
    onSave: () -> Unit,
    onBack: () -> Unit,
) {
    val compatibleProducts = state.products.filter { it.type.isCompatibleWith(state.operation) }
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
            MovementGroupSelector(
                selected = state.operation.group,
                onSelect = onSelectGroup,
            )
            SelectionDropdown(
                label = stringResource(R.string.manual_record_type),
                selectedId = state.operation.name,
                selected = stringResource(state.operation.labelRes()),
                options = state.operation.group.operations()
                    .map { it.name to stringResource(it.labelRes()) },
                onSelect = { onSelectOperation(ManualRecordType.valueOf(it)) },
            )
            CategoryCarousel(
                categories = state.operation.categories(),
                selected = state.category,
                onSelect = { category -> onUpdate { it.copy(category = category) } },
            )
            SelectionDropdown(
                label = stringResource(state.operation.primaryProductLabelRes()),
                selectedId = state.productId,
                selected = compatibleProducts.firstOrNull { it.id == state.productId }?.name
                    ?: stringResource(R.string.manual_record_choose_product),
                options = compatibleProducts.map { it.id to it.name },
                onSelect = onSelectProduct,
            )
            if (compatibleProducts.isEmpty()) {
                PocketMessage(stringResource(R.string.manual_record_no_products), isError = false)
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
                    label = stringResource(state.operation.relatedProductLabelRes()),
                    selectedId = state.sourceProductId,
                    selected = sourceProducts.firstOrNull { it.id == state.sourceProductId }?.name
                        ?: stringResource(
                            if (state.operation.requiresRelatedProduct()) {
                                R.string.manual_record_choose_destination
                            } else {
                                R.string.manual_action_no_source
                            },
                        ),
                    options = (
                        if (state.operation.requiresRelatedProduct()) {
                            emptyList()
                        } else {
                            listOf("" to stringResource(R.string.manual_action_no_source))
                        }
                    ) + sourceProducts.map { it.id to it.name },
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
    selectedId: String,
    selected: String,
    options: List<Pair<String, String>>,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(PocketSpacing.xs)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, true),
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .padding(horizontal = PocketSpacing.md, vertical = PocketSpacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = selected,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded)
                }
            }
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                shape = RoundedCornerShape(20.dp),
                containerColor = MaterialTheme.colorScheme.surface,
            ) {
                options.forEach { (id, text) ->
                    DropdownMenuItem(
                        text = { Text(text) },
                        trailingIcon = {
                            if (id == selectedId) {
                                Icon(
                                    imageVector = Icons.Rounded.CheckCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                        },
                        onClick = {
                            onSelect(id)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun MovementGroupSelector(
    selected: ManualRecordGroup,
    onSelect: (ManualRecordGroup) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(PocketSpacing.xs)) {
        Text(
            text = stringResource(R.string.manual_record_movement),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(PocketSpacing.xs),
        ) {
            ManualRecordGroup.entries.forEach { group ->
                PocketChoiceChip(
                    label = stringResource(group.labelRes()),
                    selected = selected == group,
                    onClick = { onSelect(group) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun CategoryCarousel(
    categories: List<TransactionCategoryId>,
    selected: TransactionCategoryId,
    onSelect: (TransactionCategoryId) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(PocketSpacing.xs)) {
        Text(
            text = stringResource(R.string.transaction_editor_category),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(PocketSpacing.xs),
        ) {
            items(categories, key = TransactionCategoryId::name) { category ->
                val isSelected = selected == category
                Surface(
                    modifier = Modifier
                        .widthIn(min = 104.dp)
                        .heightIn(min = 88.dp)
                        .selectable(
                            selected = isSelected,
                            onClick = { onSelect(category) },
                            role = Role.RadioButton,
                        ),
                    shape = RoundedCornerShape(18.dp),
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f)
                    } else {
                        MaterialTheme.colorScheme.surface
                    },
                    border = BorderStroke(
                        width = if (isSelected) 2.dp else 1.dp,
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.outline
                        },
                    ),
                ) {
                    Box(
                        modifier = Modifier.padding(
                            horizontal = PocketSpacing.md,
                            vertical = PocketSpacing.sm,
                        ),
                    ) {
                        Column(
                            modifier = Modifier.align(Alignment.Center),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(PocketSpacing.xs),
                        ) {
                            Icon(
                                imageVector = category.icon(),
                                contentDescription = null,
                                tint = if (isSelected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                            Text(
                                text = stringResource(category.labelRes()),
                                style = MaterialTheme.typography.labelMedium,
                                textAlign = TextAlign.Center,
                                maxLines = 2,
                            )
                        }
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Rounded.CheckCircle,
                                contentDescription = stringResource(R.string.manual_record_selected),
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .size(18.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
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
        minLines = if (singleLine) 1 else 2,
        maxLines = if (singleLine) 1 else 3,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().pocketBringIntoViewOnFocus(),
    )
}

private fun ManualRecordType.showsSourceProduct(): Boolean =
    this == ManualRecordType.TRANSFER ||
        this == ManualRecordType.CARD_PAYMENT ||
        this == ManualRecordType.SAVINGS_DEPOSIT ||
        this == ManualRecordType.SAVINGS_WITHDRAWAL ||
        this == ManualRecordType.LOAN_PAYMENT

private fun ManualRecordGroup.labelRes(): Int = when (this) {
    ManualRecordGroup.EXPENSE -> R.string.transactions_expense
    ManualRecordGroup.INCOME -> R.string.transactions_income
    ManualRecordGroup.TRANSFER -> R.string.manual_record_transfer_group
}

private fun ManualRecordType.labelRes(): Int = when (this) {
    ManualRecordType.INCOME -> R.string.transactions_income
    ManualRecordType.EXPENSE -> R.string.transactions_expense
    ManualRecordType.TRANSFER -> R.string.manual_record_own_transfer
    ManualRecordType.CARD_PURCHASE -> R.string.manual_record_card_purchase
    ManualRecordType.CARD_PAYMENT -> R.string.manual_record_card_payment
    ManualRecordType.SAVINGS_DEPOSIT -> R.string.manual_record_savings_deposit
    ManualRecordType.SAVINGS_WITHDRAWAL -> R.string.manual_record_savings_withdrawal
    ManualRecordType.LOAN_PAYMENT -> R.string.manual_record_loan_payment
}

private fun ManualRecordType.primaryProductLabelRes(): Int = when (this) {
    ManualRecordType.TRANSFER -> R.string.manual_record_origin
    ManualRecordType.CARD_PURCHASE,
    ManualRecordType.CARD_PAYMENT,
    -> R.string.manual_record_card
    ManualRecordType.SAVINGS_DEPOSIT,
    ManualRecordType.SAVINGS_WITHDRAWAL,
    -> R.string.manual_record_savings
    ManualRecordType.LOAN_PAYMENT -> R.string.manual_record_loan
    ManualRecordType.INCOME,
    ManualRecordType.EXPENSE,
    -> R.string.manual_record_product
}

private fun ManualRecordType.relatedProductLabelRes(): Int = when (this) {
    ManualRecordType.TRANSFER,
    ManualRecordType.SAVINGS_WITHDRAWAL,
    -> R.string.manual_record_destination
    ManualRecordType.SAVINGS_DEPOSIT -> R.string.manual_record_origin
    ManualRecordType.CARD_PAYMENT,
    ManualRecordType.LOAN_PAYMENT,
    -> R.string.manual_action_source
    else -> R.string.manual_record_product
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

private fun TransactionCategoryId.icon(): ImageVector = when (this) {
    TransactionCategoryId.SALARY -> Icons.Rounded.Payments
    TransactionCategoryId.FREELANCE -> Icons.Rounded.Work
    TransactionCategoryId.TRANSFER -> Icons.Rounded.AccountBalanceWallet
    TransactionCategoryId.FOOD -> Icons.Rounded.Fastfood
    TransactionCategoryId.TRANSPORT -> Icons.Rounded.DirectionsCar
    TransactionCategoryId.HOME -> Icons.Rounded.Home
    TransactionCategoryId.HEALTH -> Icons.Rounded.HealthAndSafety
    TransactionCategoryId.EDUCATION -> Icons.Rounded.School
    TransactionCategoryId.ENTERTAINMENT -> Icons.Rounded.LocalMovies
    TransactionCategoryId.SHOPPING -> Icons.Rounded.ShoppingBag
    TransactionCategoryId.SERVICES -> Icons.Rounded.ReceiptLong
    TransactionCategoryId.DEBT_PAYMENT -> Icons.Rounded.ReceiptLong
    TransactionCategoryId.SAVINGS -> Icons.Rounded.Savings
    TransactionCategoryId.OTHER -> Icons.Rounded.MoreHoriz
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
