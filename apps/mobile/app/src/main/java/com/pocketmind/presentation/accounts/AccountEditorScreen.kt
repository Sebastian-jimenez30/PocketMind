package com.pocketmind.presentation.accounts

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import com.pocketmind.ui.components.PocketChoiceChip
import com.pocketmind.ui.components.PocketPrimaryButton
import com.pocketmind.ui.theme.PocketSpacing

@Composable
fun AccountEditorRoute(onSaved: () -> Unit, onBack: () -> Unit, viewModel: AccountEditorViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.saved) { if (state.saved) onSaved() }
    AccountEditorScreen(state, viewModel::update, viewModel::save, onBack)
}

@Composable
private fun AccountEditorScreen(state: AccountEditorUiState, onUpdate: ((AccountEditorUiState) -> AccountEditorUiState) -> Unit, onSave: () -> Unit, onBack: () -> Unit) {
    if (state.isLoading) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }; return }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.primary).statusBarsPadding().padding(PocketSpacing.md), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.accounts_back), tint = MaterialTheme.colorScheme.onPrimary) }
            Spacer(Modifier.padding(PocketSpacing.xs))
            Text(stringResource(if (state.accountId == null) R.string.account_editor_create else R.string.account_editor_edit), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onPrimary)
        }
        Column(
            Modifier.weight(1f)
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(PocketSpacing.xl),
            verticalArrangement = Arrangement.spacedBy(PocketSpacing.lg),
        ) {
            OutlinedTextField(state.name, { value -> onUpdate { it.copy(name = value) } }, label = { Text(stringResource(R.string.account_editor_name)) }, placeholder = { Text(stringResource(R.string.account_editor_name_example)) }, singleLine = true, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth())
            Text(stringResource(R.string.account_editor_type), style = MaterialTheme.typography.labelLarge)
            androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(PocketSpacing.xs), verticalArrangement = Arrangement.spacedBy(PocketSpacing.xs)) {
                val availableTypes = if (state.accountId == null) FinancialAccountType.entries else listOf(state.type)
                availableTypes.forEach { type ->
                    PocketChoiceChip(
                        stringResource(type.labelRes()),
                        state.type == type,
                        onClick = { if (state.accountId == null) onUpdate { it.copy(type = type) } },
                    )
                }
            }
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
                modifier = Modifier.fillMaxWidth(),
            )
            when (state.type) {
                FinancialAccountType.CREDIT_CARD -> CreditCardFields(state, onUpdate)
                FinancialAccountType.SAVINGS -> SavingsFields(state, onUpdate)
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
    Text(stringResource(R.string.account_editor_savings_type), style = MaterialTheme.typography.labelLarge)
    Row(horizontalArrangement = Arrangement.spacedBy(PocketSpacing.xs)) {
        SavingsProductType.entries.forEach { type ->
            PocketChoiceChip(
                label = stringResource(
                    when (type) {
                        SavingsProductType.SIMPLE -> R.string.savings_type_simple
                        SavingsProductType.POCKET -> R.string.savings_type_pocket
                        SavingsProductType.TERM_DEPOSIT -> R.string.savings_type_cdt
                    },
                ),
                selected = state.savingsType == type,
                onClick = { onUpdate { it.copy(savingsType = type) } },
            )
        }
    }
    EditorField(
        value = state.annualRate,
        onValueChange = { value -> onUpdate { it.copy(annualRate = value.filter { char -> char.isDigit() || char == ',' || char == '.' }) } },
        label = stringResource(R.string.account_editor_yield),
        placeholder = stringResource(R.string.account_editor_rate_example),
        keyboardType = KeyboardType.Decimal,
    )
    if (state.savingsType == SavingsProductType.TERM_DEPOSIT) {
        EditorField(
            value = state.maturityDate,
            onValueChange = { value -> onUpdate { it.copy(maturityDate = value.filter { char -> char.isDigit() || char == '/' }.take(10)) } },
            label = stringResource(R.string.account_editor_maturity),
            placeholder = stringResource(R.string.account_editor_date_example),
            keyboardType = KeyboardType.Number,
        )
    } else {
        Text(
            stringResource(R.string.account_editor_optional),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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
        modifier = Modifier.fillMaxWidth(),
    )
}
