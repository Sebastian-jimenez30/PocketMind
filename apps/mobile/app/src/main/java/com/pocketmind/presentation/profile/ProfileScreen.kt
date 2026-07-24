package com.pocketmind.presentation.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pocketmind.R
import com.pocketmind.ui.components.PocketContentSheet
import com.pocketmind.ui.components.PocketMessage
import com.pocketmind.ui.components.PocketPrimaryButton
import com.pocketmind.ui.components.PocketRowDivider
import com.pocketmind.ui.components.PocketSectionCard
import com.pocketmind.ui.components.PocketSettingsRow
import com.pocketmind.ui.theme.PocketSpacing
import com.pocketmind.ui.theme.PocketMindTheme
import kotlinx.coroutines.launch

private enum class ProfileSheet { PERSONAL, PREFERENCES }

@Composable
fun ProfileRoute(
    onBack: () -> Unit,
    onSignedOut: () -> Unit,
    viewModel: ProfileViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
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
    var activeSheet by rememberSaveable { mutableStateOf<ProfileSheet?>(null) }
    var showPasswordDialog by rememberSaveable { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val comingSoon = stringResource(R.string.profile_coming_soon)
    val onComingSoon: () -> Unit = {
        scope.launch {
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(comingSoon)
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.primary,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
        ) {
            ProfileHeader(state = state, onBack = onBack)
            PocketContentSheet(modifier = Modifier.weight(1f)) {
                if (state.isLoading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else {
                    ProfileContent(
                        state = state,
                        onOpenPersonal = { activeSheet = ProfileSheet.PERSONAL },
                        onOpenPreferences = { activeSheet = ProfileSheet.PREFERENCES },
                        onOpenSecurity = { showPasswordDialog = true },
                        onComingSoon = onComingSoon,
                        onSignOut = onSignOut,
                    )
                }
            }
        }
    }

    when (activeSheet) {
        ProfileSheet.PERSONAL -> ModalBottomSheet(onDismissRequest = { activeSheet = null }) {
            PersonalDataSheet(
                state = state,
                onDisplayNameChanged = onDisplayNameChanged,
                onEmailChanged = onEmailChanged,
                onSave = onSave,
                onChangeEmail = onChangeEmail,
            )
        }
        ProfileSheet.PREFERENCES -> ModalBottomSheet(onDismissRequest = { activeSheet = null }) {
            FinancialPreferencesSheet(
                state = state,
                onCurrencySelected = onCurrencySelected,
                onWeekStartsOnSelected = onWeekStartsOnSelected,
                onMonthlySummaryNotificationsChanged = onMonthlySummaryNotificationsChanged,
                onSave = onSave,
            )
        }
        null -> Unit
    }

    if (showPasswordDialog) {
        ChangePasswordDialog(
            state = state,
            onDismiss = { showPasswordDialog = false },
            onConfirm = onChangePassword,
        )
    }
}

@Composable
private fun ProfileHeader(
    state: ProfileUiState,
    onBack: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = PocketSpacing.xl, vertical = PocketSpacing.md),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = stringResource(R.string.profile_back),
                    tint = MaterialTheme.colorScheme.onPrimary,
                )
            }
            Text(
                text = stringResource(R.string.profile_title),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.size(PocketSpacing.touchTarget))
        }
        if (!state.isLoading) {
            Spacer(Modifier.height(PocketSpacing.sm))
            ProfileAvatar(state.displayName)
            Spacer(Modifier.height(PocketSpacing.sm))
            Text(
                text = state.displayName.ifBlank { stringResource(R.string.profile_default_name) },
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onPrimary,
            )
            Text(
                text = state.email,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.82f),
            )
            Spacer(Modifier.height(PocketSpacing.xxs))
            Text(
                text = stringResource(R.string.profile_member_label),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.72f),
            )
        }
        Spacer(Modifier.height(PocketSpacing.md))
    }
}

@Composable
private fun ProfileAvatar(displayName: String) {
    val initial = displayName.trim().firstOrNull()?.uppercase() ?: "P"
    Box(
        modifier = Modifier
            .size(76.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = initial,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun ProfileContent(
    state: ProfileUiState,
    onOpenPersonal: () -> Unit,
    onOpenPreferences: () -> Unit,
    onOpenSecurity: () -> Unit,
    onComingSoon: () -> Unit,
    onSignOut: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = PocketSpacing.xl,
            top = PocketSpacing.xl,
            end = PocketSpacing.xl,
            bottom = PocketSpacing.xxl,
        ),
        verticalArrangement = Arrangement.spacedBy(PocketSpacing.lg),
    ) {
        if (state.message != null) {
            item { PocketMessage(message = state.message, isError = state.isError) }
        }
        item {
            PocketSectionCard {
                PocketSettingsRow(
                    icon = Icons.Rounded.AccountCircle,
                    title = stringResource(R.string.profile_personal_title),
                    description = stringResource(R.string.profile_personal_description),
                    onClick = onOpenPersonal,
                )
                PocketRowDivider()
                PocketSettingsRow(
                    icon = Icons.Rounded.Tune,
                    title = stringResource(R.string.profile_preferences_title),
                    description = stringResource(R.string.profile_preferences_description),
                    onClick = onOpenPreferences,
                )
            }
        }
        item {
            PocketSectionCard {
                PocketSettingsRow(
                    icon = Icons.Rounded.Lock,
                    title = stringResource(R.string.profile_security_title),
                    description = stringResource(R.string.profile_security_description),
                    onClick = onOpenSecurity,
                )
                PocketRowDivider()
                PocketSettingsRow(
                    icon = Icons.Rounded.AutoAwesome,
                    title = stringResource(R.string.profile_automation_title),
                    description = stringResource(R.string.profile_automation_description),
                    onClick = onComingSoon,
                )
                PocketRowDivider()
                PocketSettingsRow(
                    icon = Icons.Rounded.Security,
                    title = stringResource(R.string.profile_privacy_title),
                    description = stringResource(R.string.profile_privacy_description),
                    onClick = onComingSoon,
                )
            }
        }
        item {
            PocketSectionCard {
                PocketSettingsRow(
                    icon = Icons.AutoMirrored.Rounded.Logout,
                    title = stringResource(R.string.profile_sign_out),
                    description = stringResource(R.string.profile_sign_out_description),
                    onClick = onSignOut,
                    iconTint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun SheetTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.headlineSmall,
        modifier = Modifier.fillMaxWidth(),
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun PersonalDataSheet(
    state: ProfileUiState,
    onDisplayNameChanged: (String) -> Unit,
    onEmailChanged: (String) -> Unit,
    onSave: () -> Unit,
    onChangeEmail: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(horizontal = PocketSpacing.xl)
            .padding(bottom = PocketSpacing.xxl),
        verticalArrangement = Arrangement.spacedBy(PocketSpacing.md),
    ) {
        SheetTitle(stringResource(R.string.profile_personal_title))
        OutlinedTextField(
            value = state.displayName,
            onValueChange = onDisplayNameChanged,
            label = { Text(stringResource(R.string.profile_display_name)) },
            supportingText = { Text(stringResource(R.string.profile_display_name_support)) },
            enabled = !state.isSaving,
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth(),
        )
        PocketPrimaryButton(
            text = stringResource(R.string.profile_save_name),
            onClick = onSave,
            loading = state.isSaving,
        )
        OutlinedTextField(
            value = state.email,
            onValueChange = onEmailChanged,
            label = { Text(stringResource(R.string.profile_email)) },
            supportingText = { Text(stringResource(R.string.profile_email_support)) },
            enabled = !state.isSaving,
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedButton(
            onClick = onChangeEmail,
            enabled = !state.isSaving,
            modifier = Modifier
                .fillMaxWidth()
                .height(PocketSpacing.primaryButtonHeight),
            shape = RoundedCornerShape(16.dp),
        ) {
            Text(stringResource(R.string.profile_update_email))
        }
        if (state.message != null) PocketMessage(state.message, state.isError)
    }
}

@Composable
private fun FinancialPreferencesSheet(
    state: ProfileUiState,
    onCurrencySelected: (String) -> Unit,
    onWeekStartsOnSelected: (Int) -> Unit,
    onMonthlySummaryNotificationsChanged: (Boolean) -> Unit,
    onSave: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = PocketSpacing.xl)
            .padding(bottom = PocketSpacing.xxl),
        verticalArrangement = Arrangement.spacedBy(PocketSpacing.lg),
    ) {
        SheetTitle(stringResource(R.string.profile_preferences_title))
        Text(stringResource(R.string.profile_currency), style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(PocketSpacing.xs)) {
            FilterChip(
                selected = state.currencyCode == "COP",
                onClick = { onCurrencySelected("COP") },
                label = { Text(stringResource(R.string.profile_currency_cop_code)) },
            )
            FilterChip(
                selected = state.currencyCode == "USD",
                onClick = { onCurrencySelected("USD") },
                label = { Text(stringResource(R.string.profile_currency_usd_code)) },
            )
        }
        Text(
            text = stringResource(
                if (state.currencyCode == "USD") {
                    R.string.profile_currency_usd
                } else {
                    R.string.profile_currency_cop
                },
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(stringResource(R.string.profile_week_start), style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(PocketSpacing.xs)) {
            FilterChip(
                selected = state.weekStartsOn == 1,
                onClick = { onWeekStartsOnSelected(1) },
                label = { Text(stringResource(R.string.profile_monday)) },
            )
            FilterChip(
                selected = state.weekStartsOn == 0,
                onClick = { onWeekStartsOnSelected(0) },
                label = { Text(stringResource(R.string.profile_sunday)) },
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.profile_monthly_summary), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.profile_monthly_summary_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = state.monthlySummaryNotificationsEnabled,
                onCheckedChange = onMonthlySummaryNotificationsChanged,
                enabled = !state.isSaving,
            )
        }
        PocketPrimaryButton(
            text = stringResource(R.string.profile_save_preferences),
            onClick = onSave,
            loading = state.isSaving,
        )
        if (state.message != null) PocketMessage(state.message, state.isError)
    }
}

@Composable
private fun ChangePasswordDialog(
    state: ProfileUiState,
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit,
) {
    var password by rememberSaveable { mutableStateOf("") }
    var confirmation by rememberSaveable { mutableStateOf("") }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    val visibilityDescription = stringResource(
        if (passwordVisible) R.string.auth_hide_password else R.string.auth_show_password,
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.profile_change_password)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(PocketSpacing.sm)) {
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(stringResource(R.string.profile_new_password)) },
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                if (passwordVisible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                contentDescription = visibilityDescription,
                            )
                        }
                    },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = confirmation,
                    onValueChange = { confirmation = it },
                    label = { Text(stringResource(R.string.profile_confirm_password)) },
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    singleLine = true,
                )
                if (state.message != null) PocketMessage(state.message, state.isError)
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(password, confirmation) },
                enabled = !state.isSaving,
            ) {
                Text(stringResource(R.string.profile_save_password))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.profile_cancel))
            }
        },
    )
}

@Preview(name = "Perfil", showBackground = true, widthDp = 360, heightDp = 800)
@Composable
private fun ProfilePreview() {
    PocketMindTheme {
        ProfileScreen(
            state = ProfileUiState(
                isLoading = false,
                displayName = "Sofía Martínez",
                email = "sofia@pocketmind.app",
                monthlySummaryNotificationsEnabled = true,
            ),
            onBack = {},
            onDisplayNameChanged = {},
            onEmailChanged = {},
            onCurrencySelected = {},
            onWeekStartsOnSelected = {},
            onMonthlySummaryNotificationsChanged = {},
            onSave = {},
            onChangeEmail = {},
            onChangePassword = { _, _ -> },
            onSignOut = {},
        )
    }
}
