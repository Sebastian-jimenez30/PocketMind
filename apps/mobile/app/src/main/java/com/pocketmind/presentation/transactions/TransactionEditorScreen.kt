package com.pocketmind.presentation.transactions

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pocketmind.R
import com.pocketmind.shared.domain.model.FinancialAccount
import com.pocketmind.shared.domain.model.TransactionCategoryId
import com.pocketmind.shared.domain.model.TransactionType
import com.pocketmind.ui.components.PocketMessage
import com.pocketmind.ui.components.PocketContextTopBar
import com.pocketmind.ui.components.PocketPrimaryButton
import com.pocketmind.ui.components.pocketBringIntoViewOnFocus
import com.pocketmind.ui.theme.PocketSpacing

@Composable
fun TransactionEditorRoute(
    onSaved: () -> Unit,
    onBack: () -> Unit,
    viewModel: TransactionEditorViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.saved) { if (state.saved) onSaved() }
    TransactionEditorScreen(state, viewModel::update, viewModel::save, viewModel::delete, onBack)
}

@Composable
fun TransactionEditorScreen(
    state: TransactionEditorUiState,
    onUpdate: ((TransactionEditorUiState) -> TransactionEditorUiState) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    onBack: () -> Unit,
) {
    if (state.isLoading) {
        androidx.compose.foundation.layout.Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        EditorHeader(state.transactionId != null, onBack)
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).imePadding().padding(PocketSpacing.xl),
            verticalArrangement = Arrangement.spacedBy(PocketSpacing.lg),
        ) {
            TransactionTypeSelector(state.type) { type -> onUpdate { it.copy(type = type) } }
            AccountSelector(state.accounts, state.accountId) { id -> onUpdate { it.copy(accountId = id) } }
            OutlinedTextField(
                value = state.amount,
                onValueChange = { value -> onUpdate { it.copy(amount = value.filter(Char::isDigit)) } },
                label = { Text(stringResource(R.string.transaction_editor_amount)) },
                placeholder = { Text(stringResource(R.string.transaction_editor_amount_example)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().pocketBringIntoViewOnFocus(),
            )
            OutlinedTextField(
                value = state.date,
                onValueChange = { value -> onUpdate { it.copy(date = value.filter { char -> char.isDigit() || char == '/' }.take(10)) } },
                label = { Text(stringResource(R.string.transaction_editor_date)) },
                placeholder = { Text(stringResource(R.string.account_editor_date_example)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().pocketBringIntoViewOnFocus(),
            )
            CategorySelector(state.category) { category -> onUpdate { it.copy(category = category) } }
            OutlinedTextField(
                value = state.merchant,
                onValueChange = { value -> onUpdate { it.copy(merchant = value) } },
                label = { Text(stringResource(R.string.transaction_editor_merchant)) },
                placeholder = { Text(stringResource(R.string.transaction_editor_merchant_example)) },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().pocketBringIntoViewOnFocus(),
            )
            OutlinedTextField(
                value = state.note,
                onValueChange = { value -> onUpdate { it.copy(note = value) } },
                label = { Text(stringResource(R.string.transaction_editor_note)) },
                placeholder = { Text(stringResource(R.string.transaction_editor_note_example)) },
                minLines = 3,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().pocketBringIntoViewOnFocus(),
            )
            state.error?.let { PocketMessage(it, isError = true) }
            PocketPrimaryButton(
                text = stringResource(if (state.transactionId == null) R.string.transaction_editor_save else R.string.transaction_editor_update),
                onClick = onSave,
                loading = state.isSaving,
            )
            if (state.canDelete) {
                var confirmDelete by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
                TextButton(onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.transaction_editor_delete), color = MaterialTheme.colorScheme.error)
                }
                if (confirmDelete) {
                    AlertDialog(
                        onDismissRequest = { confirmDelete = false },
                        title = { Text(stringResource(R.string.transaction_editor_delete_title)) },
                        text = { Text(stringResource(R.string.transaction_editor_delete_message)) },
                        confirmButton = {
                            TextButton(onClick = {
                                confirmDelete = false
                                onDelete()
                            }) { Text(stringResource(R.string.transaction_editor_confirm_delete), color = MaterialTheme.colorScheme.error) }
                        },
                        dismissButton = {
                            TextButton(onClick = { confirmDelete = false }) {
                                Text(stringResource(R.string.transaction_editor_cancel))
                            }
                        },
                    )
                }
            }
            Spacer(Modifier.height(PocketSpacing.sm))
        }
    }
}

@Composable
private fun EditorHeader(editing: Boolean, onBack: () -> Unit) {
    PocketContextTopBar(
        title = stringResource(
            if (editing) R.string.transaction_editor_edit_title
            else R.string.transaction_editor_create_title,
        ),
        onBack = onBack,
        backContentDescription = stringResource(R.string.transactions_back),
    )
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun TransactionTypeSelector(selected: TransactionType, onSelect: (TransactionType) -> Unit) {
    var expanded by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = stringResource(selected.labelRes()),
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.transaction_editor_type)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled = true),
            shape = RoundedCornerShape(16.dp),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            shape = RoundedCornerShape(16.dp),
        ) {
            TransactionType.entries.filter { it != TransactionType.TRANSFER }.forEach { type ->
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

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun AccountSelector(accounts: List<FinancialAccount>, selectedId: String, onSelect: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(PocketSpacing.sm)) {
        if (accounts.isEmpty()) {
            PocketMessage(stringResource(R.string.transaction_editor_no_accounts), isError = true)
        } else {
            var expanded by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
            val selectedName = accounts.firstOrNull { it.id == selectedId }?.name.orEmpty()
            ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                OutlinedTextField(
                    value = selectedName,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(stringResource(R.string.transaction_editor_account)) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                    modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled = true),
                    shape = RoundedCornerShape(16.dp),
                )
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                    shape = RoundedCornerShape(16.dp),
                ) {
                    accounts.forEach { account ->
                        DropdownMenuItem(text = { Text(account.name) }, onClick = { onSelect(account.id); expanded = false })
                    }
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun CategorySelector(selected: TransactionCategoryId, onSelect: (TransactionCategoryId) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(PocketSpacing.sm)) {
        var expanded by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
            OutlinedTextField(
                value = stringResource(selected.labelRes()),
                onValueChange = {},
                readOnly = true,
                label = { Text(stringResource(R.string.transaction_editor_category)) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled = true),
                shape = RoundedCornerShape(16.dp),
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                shape = RoundedCornerShape(16.dp),
            ) {
                TransactionCategoryId.entries.forEach { category ->
                    DropdownMenuItem(text = { Text(stringResource(category.labelRes())) }, onClick = { onSelect(category); expanded = false })
                }
            }
        }
    }
}

private fun TransactionType.labelRes(): Int = when (this) {
    TransactionType.INCOME -> R.string.transactions_income
    TransactionType.EXPENSE -> R.string.transactions_expense
    TransactionType.TRANSFER -> R.string.transactions_transfer
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
