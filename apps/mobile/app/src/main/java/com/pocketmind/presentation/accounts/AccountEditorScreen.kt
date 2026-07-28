package com.pocketmind.presentation.accounts

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pocketmind.R
import com.pocketmind.shared.domain.model.FinancialAccountType
import com.pocketmind.shared.domain.model.SavingsProductType
import com.pocketmind.ui.components.PocketMessage
import com.pocketmind.ui.components.PocketContextTopBar
import com.pocketmind.ui.components.PocketPrimaryButton
import com.pocketmind.ui.components.pocketBringIntoViewOnFocus
import com.pocketmind.ui.theme.PocketSpacing

@Composable
fun AccountEditorRoute(onSaved: () -> Unit, onBack: () -> Unit, viewModel: AccountEditorViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.saved) { if (state.saved) onSaved() }
    AccountEditorScreen(state, viewModel::update, viewModel::save, onBack)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AccountEditorScreen(state: AccountEditorUiState, onUpdate: ((AccountEditorUiState) -> AccountEditorUiState) -> Unit, onSave: () -> Unit, onBack: () -> Unit) {
    if (state.isLoading) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }; return }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        PocketContextTopBar(
            title = stringResource(R.string.accounts_title),
            onBack = onBack,
            backContentDescription = stringResource(R.string.accounts_back),
        )
        Column(
            Modifier.weight(1f)
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(PocketSpacing.xl),
            verticalArrangement = Arrangement.spacedBy(PocketSpacing.lg),
        ) {
            OutlinedTextField(
                state.name,
                { value -> onUpdate { it.copy(name = value) } },
                label = { Text(stringResource(R.string.account_editor_name)) },
                placeholder = { Text(stringResource(R.string.account_editor_name_example)) },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().pocketBringIntoViewOnFocus(),
            )
            ProductTypeDropdown(
                selected = state.type,
                enabled = state.accountId == null,
                onSelect = { type -> onUpdate { it.copy(type = type) } },
            )
            OutlinedTextField(
                state.balance,
                { value -> onUpdate { it.copy(balance = value.filter(Char::isDigit)) } },
                label = {
                    Text(
                        stringResource(
                            when (state.type) {
                                FinancialAccountType.CREDIT_CARD, FinancialAccountType.LOAN -> R.string.account_editor_debt
                                FinancialAccountType.SAVINGS -> R.string.account_editor_saved
                                else -> R.string.account_editor_balance
                            },
                        ),
                    )
                },
                placeholder = { Text(stringResource(R.string.account_editor_balance_example)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().pocketBringIntoViewOnFocus(),
            )
            when (state.type) {
                FinancialAccountType.CREDIT_CARD -> CreditCardFields(state, onUpdate)
                FinancialAccountType.SAVINGS -> SavingsFields(state, onUpdate)
                FinancialAccountType.LOAN -> LoanFields(state, onUpdate)
                else -> Unit
            }
            state.error?.let { PocketMessage(it, isError = true) }
            PocketPrimaryButton(stringResource(R.string.account_editor_save), onSave, loading = state.isSaving)
            Spacer(Modifier.padding(PocketSpacing.sm))
        }
    }
}

@Composable
private fun CreditCardFields(
    state: AccountEditorUiState,
    onUpdate: ((AccountEditorUiState) -> AccountEditorUiState) -> Unit,
) {
    Text(stringResource(R.string.account_editor_card_section), style = MaterialTheme.typography.titleMedium)
    EditorField(
        value = state.creditLimit,
        onValueChange = { value -> onUpdate { it.copy(creditLimit = value.filter(Char::isDigit)) } },
        label = stringResource(R.string.account_editor_limit),
        placeholder = stringResource(R.string.account_editor_limit_example),
        keyboardType = KeyboardType.Number,
    )
    EditorField(
        value = state.annualRate,
        onValueChange = { value -> onUpdate { it.copy(annualRate = value.filter { char -> char.isDigit() || char == ',' || char == '.' }) } },
        label = stringResource(R.string.account_editor_rate),
        placeholder = stringResource(R.string.account_editor_rate_example),
        keyboardType = KeyboardType.Decimal,
    )
    EditorField(
        value = state.closingDay,
        onValueChange = { value -> onUpdate { it.copy(closingDay = value.filter(Char::isDigit).take(2)) } },
        label = stringResource(R.string.account_editor_closing_day),
        placeholder = stringResource(R.string.account_editor_day_example),
        keyboardType = KeyboardType.Number,
    )
    EditorField(
        value = state.paymentDay,
        onValueChange = { value -> onUpdate { it.copy(paymentDay = value.filter(Char::isDigit).take(2)) } },
        label = stringResource(R.string.account_editor_payment_day),
        placeholder = stringResource(R.string.account_editor_day_example),
        keyboardType = KeyboardType.Number,
    )
    if (state.balance.toLongOrNull()?.let { it > 0 } == true) {
        EditorField(
            value = state.debtInstallments,
            onValueChange = { value -> onUpdate { it.copy(debtInstallments = value.filter(Char::isDigit).take(2)) } },
            label = stringResource(R.string.account_editor_debt_installments),
            placeholder = stringResource(R.string.manual_action_installments_example),
            keyboardType = KeyboardType.Number,
        )
        EditorField(
            value = state.debtFirstPaymentDate,
            onValueChange = { value ->
                onUpdate { it.copy(debtFirstPaymentDate = value.filter { char -> char.isDigit() || char == '/' }.take(10)) }
            },
            label = stringResource(R.string.account_editor_debt_first_payment),
            placeholder = stringResource(R.string.account_editor_date_example),
            keyboardType = KeyboardType.Number,
        )
    }
}

@Composable
private fun SavingsFields(
    state: AccountEditorUiState,
    onUpdate: ((AccountEditorUiState) -> AccountEditorUiState) -> Unit,
) {
    Text(stringResource(R.string.account_editor_savings_section), style = MaterialTheme.typography.titleMedium)
    SavingsTypeDropdown(state.savingsType) { type -> onUpdate { it.copy(savingsType = type) } }
    if (state.savingsType != SavingsProductType.SIMPLE) {
        EditorField(
            value = state.annualRate,
            onValueChange = { value -> onUpdate { it.copy(annualRate = value.filter { char -> char.isDigit() || char == ',' || char == '.' }) } },
            label = stringResource(R.string.account_editor_yield),
            placeholder = stringResource(R.string.account_editor_rate_example),
            keyboardType = KeyboardType.Decimal,
        )
    }
    if (state.savingsType == SavingsProductType.TERM_DEPOSIT) {
        EditorField(
            value = state.maturityDate,
            onValueChange = { value -> onUpdate { it.copy(maturityDate = value.filter { char -> char.isDigit() || char == '/' }.take(10)) } },
            label = stringResource(R.string.account_editor_maturity),
            placeholder = stringResource(R.string.account_editor_date_example),
            keyboardType = KeyboardType.Number,
        )
    }
}

@Composable
private fun LoanFields(
    state: AccountEditorUiState,
    onUpdate: ((AccountEditorUiState) -> AccountEditorUiState) -> Unit,
) {
    Text(stringResource(R.string.account_editor_loan_section), style = MaterialTheme.typography.titleMedium)
    EditorField(
        value = state.annualRate,
        onValueChange = { value -> onUpdate { it.copy(annualRate = value.filter { char -> char.isDigit() || char == ',' || char == '.' }) } },
        label = stringResource(R.string.account_editor_rate),
        placeholder = stringResource(R.string.account_editor_rate_example),
        keyboardType = KeyboardType.Decimal,
    )
    EditorField(
        value = state.monthlyPayment,
        onValueChange = { value -> onUpdate { it.copy(monthlyPayment = value.filter(Char::isDigit)) } },
        label = stringResource(R.string.account_editor_monthly_payment),
        placeholder = stringResource(R.string.account_editor_monthly_payment_example),
        keyboardType = KeyboardType.Number,
    )
    EditorField(
        value = state.paymentDay,
        onValueChange = { value -> onUpdate { it.copy(paymentDay = value.filter(Char::isDigit).take(2)) } },
        label = stringResource(R.string.account_editor_payment_day),
        placeholder = stringResource(R.string.account_editor_day_example),
        keyboardType = KeyboardType.Number,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProductTypeDropdown(
    selected: FinancialAccountType,
    enabled: Boolean,
    onSelect: (FinancialAccountType) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { if (enabled) expanded = it },
    ) {
        OutlinedTextField(
            value = stringResource(selected.labelRes()),
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            label = { Text(stringResource(R.string.account_editor_type)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled),
            shape = RoundedCornerShape(16.dp),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            shape = RoundedCornerShape(16.dp),
        ) {
            FinancialAccountType.entries.forEach { type ->
                DropdownMenuItem(
                    text = { Text(stringResource(type.labelRes())) },
                    onClick = {
                        onSelect(type)
                        expanded = false
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SavingsTypeDropdown(
    selected: SavingsProductType,
    onSelect: (SavingsProductType) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    fun SavingsProductType.labelResId() = when (this) {
        SavingsProductType.SIMPLE -> R.string.savings_type_simple
        SavingsProductType.POCKET -> R.string.savings_type_pocket
        SavingsProductType.TERM_DEPOSIT -> R.string.savings_type_cdt
    }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = stringResource(selected.labelResId()),
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.account_editor_savings_type)) },
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
            SavingsProductType.entries.forEach { type ->
                DropdownMenuItem(
                    text = { Text(stringResource(type.labelResId())) },
                    onClick = {
                        onSelect(type)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun EditorField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    keyboardType: KeyboardType,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = { Text(placeholder) },
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        singleLine = true,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().pocketBringIntoViewOnFocus(),
    )
}
