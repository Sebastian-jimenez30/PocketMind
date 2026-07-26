package com.pocketmind.presentation.accounts

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(PocketSpacing.xl), verticalArrangement = Arrangement.spacedBy(PocketSpacing.lg)) {
            OutlinedTextField(state.name, { value -> onUpdate { it.copy(name = value) } }, label = { Text(stringResource(R.string.account_editor_name)) }, placeholder = { Text(stringResource(R.string.account_editor_name_example)) }, singleLine = true, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth())
            OutlinedTextField(state.balance, { value -> onUpdate { it.copy(balance = value) } }, label = { Text(stringResource(R.string.account_editor_balance)) }, placeholder = { Text(stringResource(R.string.account_editor_balance_example)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth())
            Text(stringResource(R.string.account_editor_type), style = MaterialTheme.typography.labelLarge)
            androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(PocketSpacing.xs), verticalArrangement = Arrangement.spacedBy(PocketSpacing.xs)) {
                FinancialAccountType.entries.forEach { type ->
                    PocketChoiceChip(stringResource(type.labelRes()), state.type == type, onClick = { onUpdate { it.copy(type = type) } })
                }
            }
            state.error?.let { PocketMessage(it, isError = true) }
            PocketPrimaryButton(stringResource(R.string.account_editor_save), onSave, loading = state.isSaving)
        }
    }
}
