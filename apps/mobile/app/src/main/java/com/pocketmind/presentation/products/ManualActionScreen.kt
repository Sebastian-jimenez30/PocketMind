package com.pocketmind.presentation.products

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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pocketmind.R
import com.pocketmind.shared.domain.model.TransactionCategoryId
import com.pocketmind.ui.components.PocketMessage
import com.pocketmind.ui.components.PocketPrimaryButton
import com.pocketmind.ui.theme.PocketSpacing

@Composable
fun ManualActionRoute(
    onSaved: () -> Unit,
    onBack: () -> Unit,
    viewModel: ManualActionViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.saved) { if (state.saved) onSaved() }
    ManualActionScreen(state, viewModel::update, viewModel::save, onBack)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ManualActionScreen(
    state: ManualActionUiState,
    onUpdate: ((ManualActionUiState) -> ManualActionUiState) -> Unit,
    onSave: () -> Unit,
    onBack: () -> Unit,
) {
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(
            Modifier.fillMaxWidth()
                .background(MaterialTheme.colorScheme.primary)
                .statusBarsPadding()
                .padding(PocketSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.accounts_back), tint = MaterialTheme.colorScheme.onPrimary)
            }
            Text(
                text = stringResource(state.action.titleRes()),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onPrimary,
            )
        }
        Column(
            Modifier.weight(1f)
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(PocketSpacing.xl),
            verticalArrangement = Arrangement.spacedBy(PocketSpacing.md),
        ) {
            if (state.action == ManualActionType.CARD_PURCHASE) {
                FormField(
                    state.merchant,
                    { value -> onUpdate { it.copy(merchant = value) } },
                    stringResource(R.string.manual_action_merchant),
                    stringResource(R.string.manual_action_merchant_example),
                )
                var categoryExpanded by androidx.compose.runtime.remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = categoryExpanded,
                    onExpandedChange = { categoryExpanded = it },
                ) {
                    OutlinedTextField(
                        value = stringResource(state.category.labelRes()),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.transaction_editor_category)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(categoryExpanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                        shape = RoundedCornerShape(16.dp),
                    )
                    ExposedDropdownMenu(
                        expanded = categoryExpanded,
                        onDismissRequest = { categoryExpanded = false },
                    ) {
                        TransactionCategoryId.entries.forEach { category ->
                            DropdownMenuItem(
                                text = { Text(stringResource(category.labelRes())) },
                                onClick = {
                                    onUpdate { it.copy(category = category) }
                                    categoryExpanded = false
                                },
                            )
                        }
                    }
                }
            }
            if (state.action != ManualActionType.SAVINGS_RATE) {
                FormField(
                    state.amount,
                    { value -> onUpdate { it.copy(amount = value.filter(Char::isDigit)) } },
                    stringResource(R.string.manual_action_amount),
                    stringResource(R.string.manual_action_amount_example),
                    KeyboardType.Number,
                )
            }
            if (state.action == ManualActionType.CARD_PURCHASE) {
                FormField(
                    state.installments,
                    { value -> onUpdate { it.copy(installments = value.filter(Char::isDigit).take(2)) } },
                    stringResource(R.string.manual_action_installments),
                    stringResource(R.string.manual_action_installments_example),
                    KeyboardType.Number,
                )
                FormField(
                    state.annualRate,
                    { value -> onUpdate { it.copy(annualRate = value.decimalOnly()) } },
                    stringResource(R.string.manual_action_rate_label),
                    stringResource(R.string.account_editor_rate_example),
                    KeyboardType.Decimal,
                )
            }
            if (state.action == ManualActionType.SAVINGS_RATE) {
                FormField(
                    state.annualRate,
                    { value -> onUpdate { it.copy(annualRate = value.decimalOnly()) } },
                    stringResource(R.string.manual_action_rate_label),
                    stringResource(R.string.account_editor_rate_example),
                    KeyboardType.Decimal,
                )
                Text(
                    stringResource(R.string.manual_action_rate_help),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            FormField(
                state.date,
                { value -> onUpdate { it.copy(date = value.dateOnly()) } },
                stringResource(R.string.manual_action_date),
                stringResource(R.string.account_editor_date_example),
                KeyboardType.Number,
            )
            if (state.action == ManualActionType.CARD_PURCHASE) {
                FormField(
                    state.firstPaymentDate,
                    { value -> onUpdate { it.copy(firstPaymentDate = value.dateOnly()) } },
                    stringResource(R.string.manual_action_first_payment),
                    stringResource(R.string.account_editor_date_example),
                    KeyboardType.Number,
                )
            }
            if (
                state.action == ManualActionType.CARD_PAYMENT ||
                state.action == ManualActionType.SAVINGS_DEPOSIT ||
                state.action == ManualActionType.SAVINGS_WITHDRAWAL
            ) {
                var expanded by androidx.compose.runtime.remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                    val selected = state.accounts.firstOrNull { it.id == state.sourceAccountId }?.name
                        ?: stringResource(R.string.manual_action_no_source)
                    OutlinedTextField(
                        value = selected,
                        onValueChange = {},
                        readOnly = true,
                        label = {
                            Text(
                                stringResource(
                                    if (state.action == ManualActionType.CARD_PAYMENT) {
                                        R.string.manual_action_source
                                    } else {
                                        R.string.manual_action_savings_source
                                    },
                                ),
                            )
                        },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                        shape = RoundedCornerShape(16.dp),
                    )
                    ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.manual_action_no_source)) },
                            onClick = {
                                onUpdate { it.copy(sourceAccountId = "") }
                                expanded = false
                            },
                        )
                        state.accounts.forEach { account ->
                            DropdownMenuItem(
                                text = { Text(account.name) },
                                onClick = {
                                    onUpdate { it.copy(sourceAccountId = account.id) }
                                    expanded = false
                                },
                            )
                        }
                    }
                }
            }
            FormField(
                state.note,
                { value -> onUpdate { it.copy(note = value) } },
                stringResource(R.string.manual_action_note),
                stringResource(R.string.manual_action_note_example),
                singleLine = false,
            )
            state.error?.let { PocketMessage(it, isError = true) }
            PocketPrimaryButton(stringResource(R.string.manual_action_save), onSave, loading = state.isSaving)
            Spacer(Modifier.padding(PocketSpacing.sm))
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
        modifier = Modifier.fillMaxWidth(),
    )
}

private fun ManualActionType.titleRes(): Int = when (this) {
    ManualActionType.CARD_PURCHASE -> R.string.manual_action_purchase
    ManualActionType.CARD_PAYMENT -> R.string.manual_action_payment
    ManualActionType.SAVINGS_DEPOSIT -> R.string.manual_action_deposit
    ManualActionType.SAVINGS_WITHDRAWAL -> R.string.manual_action_withdrawal
    ManualActionType.SAVINGS_RATE -> R.string.manual_action_rate
}

private fun String.decimalOnly(): String = filter { it.isDigit() || it == ',' || it == '.' }
private fun String.dateOnly(): String = filter { it.isDigit() || it == '/' }.take(10)

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
