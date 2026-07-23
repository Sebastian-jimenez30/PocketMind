package com.pocketmind.presentation.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel

@Composable
fun ProfileRoute(
    onBack: () -> Unit,
    onSignedOut: () -> Unit,
    viewModel: ProfileViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    ProfileScreen(
        state = state,
        onBack = onBack,
        onDisplayNameChanged = viewModel::updateDisplayName,
        onEmailChanged = viewModel::updateEmail,
        onCurrencySelected = viewModel::selectCurrency,
        onWeekStartsOnSelected = viewModel::selectWeekStartsOn,
        onMonthlySummaryNotificationsChanged = viewModel::setMonthlySummaryNotifications,
        onSave = viewModel::saveProfileAndPreferences,
        onChangeEmail = viewModel::changeEmail,
        onChangePassword = viewModel::changePassword,
        onSignOut = { viewModel.signOut(onSignedOut) },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    state: ProfileUiState,
    onBack: () -> Unit,
    onDisplayNameChanged: (String) -> Unit,
    onEmailChanged: (String) -> Unit,
    onCurrencySelected: (String) -> Unit,
    onWeekStartsOnSelected: (Int) -> Unit,
    onMonthlySummaryNotificationsChanged: (Boolean) -> Unit,
    onSave: () -> Unit,
    onChangeEmail: () -> Unit,
    onChangePassword: (String, String) -> Unit,
    onSignOut: () -> Unit,
) {
    var showPasswordDialog by remember { mutableStateOf(false) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Perfil y preferencias") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Volver") } },
            )
        },
    ) { paddingValues ->
        if (state.isLoading) {
            Column(
                modifier = Modifier.fillMaxSize().padding(paddingValues),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) { CircularProgressIndicator() }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 16.dp)
                    .widthIn(max = 560.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                ProfileHeader(state.displayName, state.email)
                SettingsSection("Perfil") {
                    OutlinedTextField(
                        value = state.displayName,
                        onValueChange = onDisplayNameChanged,
                        label = { Text("Nombre visible") },
                        supportingText = { Text("Así te saludará PocketMind.") },
                        enabled = !state.isSaving,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                SettingsSection("Preferencias financieras") {
                    Text("Moneda principal", style = MaterialTheme.typography.titleSmall)
                    ChoiceRow(
                        choices = listOf("COP" to "Pesos colombianos", "USD" to "Dólares estadounidenses"),
                        selected = state.currencyCode,
                        onSelected = onCurrencySelected,
                    )
                    Text("Inicio de la semana", style = MaterialTheme.typography.titleSmall)
                    ChoiceRow(
                        choices = listOf("1" to "Lunes", "0" to "Domingo"),
                        selected = state.weekStartsOn.toString(),
                        onSelected = { onWeekStartsOnSelected(it.toInt()) },
                    )
                    PreferenceSwitch(
                        title = "Resumen mensual",
                        description = "Recibe un resumen cuando esté disponible.",
                        checked = state.monthlySummaryNotificationsEnabled,
                        onCheckedChange = onMonthlySummaryNotificationsChanged,
                        enabled = !state.isSaving,
                    )
                    Button(onClick = onSave, enabled = !state.isSaving, modifier = Modifier.fillMaxWidth()) {
                        if (state.isSaving) CircularProgressIndicator(color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                        else Text("Guardar preferencias")
                    }
                }
                SettingsSection("Cuenta y seguridad") {
                    OutlinedTextField(
                        value = state.email,
                        onValueChange = onEmailChanged,
                        label = { Text("Correo electrónico") },
                        supportingText = { Text("Requerirá confirmación en tu nueva dirección.") },
                        enabled = !state.isSaving,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedButton(onClick = onChangeEmail, enabled = !state.isSaving, modifier = Modifier.fillMaxWidth()) {
                        Text("Actualizar correo")
                    }
                    OutlinedButton(
                        onClick = { showPasswordDialog = true },
                        enabled = !state.isSaving,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Cambiar contraseña") }
                    HorizontalDivider()
                    OutlinedButton(
                        onClick = onSignOut,
                        enabled = !state.isSaving,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Cerrar sesión", color = MaterialTheme.colorScheme.error) }
                }
                if (state.message != null) {
                    Text(
                        state.message,
                        color = if (state.isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    )
                }
                Text(
                    "La eliminación de cuenta y la exportación de datos se incorporarán cuando exista el backend seguro para esas operaciones irreversibles.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
    if (showPasswordDialog) {
        ChangePasswordDialog(
            onDismiss = { showPasswordDialog = false },
            onConfirm = { password, confirmation ->
                onChangePassword(password, confirmation)
                showPasswordDialog = false
            },
        )
    }
}

@Composable
private fun ProfileHeader(displayName: String, email: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(displayName.ifBlank { "Tu perfil" }, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        Text(email, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), content = {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        content()
    })
}

@Composable
private fun ChoiceRow(choices: List<Pair<String, String>>, selected: String, onSelected: (String) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        choices.forEach { (value, label) ->
            FilterChip(selected = value == selected, onClick = { onSelected(value) }, label = { Text(label) })
        }
    }
}

@Composable
private fun PreferenceSwitch(title: String, description: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit, enabled: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

@Composable
private fun ChangePasswordDialog(onDismiss: () -> Unit, onConfirm: (String, String) -> Unit) {
    var password by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Cambiar contraseña") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(value = password, onValueChange = { password = it }, label = { Text("Nueva contraseña") }, visualTransformation = PasswordVisualTransformation(), singleLine = true)
                OutlinedTextField(value = confirmation, onValueChange = { confirmation = it }, label = { Text("Confirmar contraseña") }, visualTransformation = PasswordVisualTransformation(), singleLine = true)
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(password, confirmation) }) { Text("Guardar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
