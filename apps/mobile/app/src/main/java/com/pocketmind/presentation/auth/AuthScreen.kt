package com.pocketmind.presentation.auth

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
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
import com.pocketmind.ui.components.PocketBrandMark
import com.pocketmind.ui.components.PocketContentSheet
import com.pocketmind.ui.components.PocketMessage
import com.pocketmind.ui.components.PocketPrimaryButton
import com.pocketmind.ui.theme.PocketSpacing
import com.pocketmind.ui.theme.PocketMindTheme
import androidx.compose.ui.res.stringResource

@Composable
fun AuthRoute(
    onAuthenticated: () -> Unit,
    viewModel: AuthViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.isAuthenticated) {
        if (state.isAuthenticated) onAuthenticated()
    }
    AuthScreen(
        state = state,
        onEmailChanged = viewModel::updateEmail,
        onPasswordChanged = viewModel::updatePassword,
        onPasswordConfirmationChanged = viewModel::updatePasswordConfirmation,
        onSubmit = viewModel::submit,
        onGoogleSignIn = viewModel::signInWithGoogle,
        onModeChanged = viewModel::changeMode,
    )
}

@Composable
fun AuthScreen(
    state: AuthUiState,
    onEmailChanged: (String) -> Unit,
    onPasswordChanged: (String) -> Unit,
    onPasswordConfirmationChanged: (String) -> Unit,
    onSubmit: () -> Unit,
    onGoogleSignIn: () -> Unit,
    onModeChanged: (AuthMode) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.primary),
    ) {
        AuthBrandHeader(mode = state.mode)
        PocketContentSheet(modifier = Modifier.weight(1f)) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .imePadding()
                    .padding(horizontal = PocketSpacing.xl, vertical = PocketSpacing.xl)
                    .widthIn(max = 480.dp)
                    .align(Alignment.CenterHorizontally),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                AuthIllustration()
                Spacer(Modifier.height(PocketSpacing.lg))
                Text(
                    text = titleFor(state.mode),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(PocketSpacing.xs))
                Text(
                    text = descriptionFor(state.mode),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(PocketSpacing.xl))

                OutlinedTextField(
                    value = state.email,
                    onValueChange = onEmailChanged,
                    label = { Text(stringResource(R.string.auth_email_label)) },
                    singleLine = true,
                    enabled = !state.isLoading,
                    shape = RoundedCornerShape(16.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (state.mode != AuthMode.RECOVERY) {
                    Spacer(Modifier.height(PocketSpacing.sm))
                    PasswordField(
                        value = state.password,
                        onValueChange = onPasswordChanged,
                        label = stringResource(R.string.auth_password_label),
                        enabled = !state.isLoading,
                    )
                }
                if (state.mode == AuthMode.SIGN_UP) {
                    Spacer(Modifier.height(PocketSpacing.sm))
                    PasswordField(
                        value = state.passwordConfirmation,
                        onValueChange = onPasswordConfirmationChanged,
                        label = stringResource(R.string.auth_password_confirmation_label),
                        enabled = !state.isLoading,
                    )
                }
                if (state.message != null) {
                    Spacer(Modifier.height(PocketSpacing.md))
                    PocketMessage(message = state.message, isError = state.isError)
                }
                Spacer(Modifier.height(PocketSpacing.lg))
                PocketPrimaryButton(
                    text = primaryActionFor(state.mode),
                    onClick = onSubmit,
                    loading = state.isLoading,
                )

                if (state.mode != AuthMode.RECOVERY) {
                    AuthDivider()
                    OutlinedButton(
                        onClick = onGoogleSignIn,
                        enabled = !state.isLoading,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(PocketSpacing.primaryButtonHeight),
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = stringResource(R.string.auth_google_mark),
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                        Spacer(Modifier.size(PocketSpacing.sm))
                        Text(stringResource(R.string.auth_google_action))
                    }
                }
                AuthFooter(mode = state.mode, onModeChanged = onModeChanged)
                Spacer(Modifier.height(PocketSpacing.md))
                Text(
                    text = stringResource(R.string.auth_security_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(PocketSpacing.md))
            }
        }
    }
}

@Composable
private fun AuthBrandHeader(mode: AuthMode) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = PocketSpacing.xl, vertical = PocketSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(PocketSpacing.sm),
    ) {
        PocketBrandMark(
            modifier = Modifier.size(44.dp),
            contentDescription = stringResource(R.string.brand_mark_description),
        )
        Column {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onPrimary,
            )
            Text(
                text = navTitleFor(mode),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.82f),
            )
        }
    }
}

@Composable
private fun AuthIllustration() {
    Box(modifier = Modifier.size(132.dp), contentAlignment = Alignment.Center) {
        val primary = MaterialTheme.colorScheme.primary
        val secondary = MaterialTheme.colorScheme.secondary
        val soft = MaterialTheme.colorScheme.primaryContainer
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(color = soft, radius = size.minDimension * 0.43f)
            decorativeDot(primary, 0.18f, 0.18f, 6.dp.toPx())
            decorativeDot(secondary, 0.84f, 0.30f, 9.dp.toPx())
            decorativeDot(primary.copy(alpha = 0.6f), 0.78f, 0.80f, 5.dp.toPx())
        }
        PocketBrandMark(contentDescription = null)
    }
}

private fun DrawScope.decorativeDot(color: Color, x: Float, y: Float, radius: Float) {
    drawCircle(color = color, radius = radius, center = center.copy(x = size.width * x, y = size.height * y))
}

@Composable
private fun AuthDivider() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = PocketSpacing.lg),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(PocketSpacing.sm),
    ) {
        HorizontalDivider(modifier = Modifier.weight(1f))
        Text(
            text = stringResource(R.string.auth_continue_with),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        HorizontalDivider(modifier = Modifier.weight(1f))
    }
}

@Composable
private fun PasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    enabled: Boolean,
) {
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    val visibilityDescription = stringResource(
        if (passwordVisible) R.string.auth_hide_password else R.string.auth_show_password,
    )
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        enabled = enabled,
        shape = RoundedCornerShape(16.dp),
        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        trailingIcon = {
            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                Icon(
                    imageVector = if (passwordVisible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                    contentDescription = visibilityDescription,
                )
            }
        },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun AuthFooter(mode: AuthMode, onModeChanged: (AuthMode) -> Unit) {
    when (mode) {
        AuthMode.SIGN_IN -> {
            TextButton(onClick = { onModeChanged(AuthMode.RECOVERY) }) {
                Text(stringResource(R.string.auth_forgot_password))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.auth_no_account),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = { onModeChanged(AuthMode.SIGN_UP) }) {
                    Text(stringResource(R.string.auth_create_account))
                }
            }
        }
        AuthMode.SIGN_UP -> Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.auth_has_account),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = { onModeChanged(AuthMode.SIGN_IN) }) {
                Text(stringResource(R.string.auth_go_to_sign_in))
            }
        }
        AuthMode.RECOVERY -> TextButton(onClick = { onModeChanged(AuthMode.SIGN_IN) }) {
            Text(stringResource(R.string.auth_back_to_sign_in))
        }
    }
}

@Composable
private fun titleFor(mode: AuthMode) = stringResource(
    when (mode) {
        AuthMode.SIGN_IN -> R.string.auth_sign_in_title
        AuthMode.SIGN_UP -> R.string.auth_sign_up_title
        AuthMode.RECOVERY -> R.string.auth_recovery_title
    },
)

@Composable
private fun descriptionFor(mode: AuthMode) = stringResource(
    when (mode) {
        AuthMode.SIGN_IN -> R.string.auth_sign_in_description
        AuthMode.SIGN_UP -> R.string.auth_sign_up_description
        AuthMode.RECOVERY -> R.string.auth_recovery_description
    },
)

@Composable
private fun primaryActionFor(mode: AuthMode) = stringResource(
    when (mode) {
        AuthMode.SIGN_IN -> R.string.auth_sign_in_action
        AuthMode.SIGN_UP -> R.string.auth_sign_up_action
        AuthMode.RECOVERY -> R.string.auth_recovery_action
    },
)

@Composable
private fun navTitleFor(mode: AuthMode) = stringResource(
    when (mode) {
        AuthMode.SIGN_IN -> R.string.auth_sign_in_nav_title
        AuthMode.SIGN_UP -> R.string.auth_sign_up_nav_title
        AuthMode.RECOVERY -> R.string.auth_recovery_nav_title
    },
)

@Preview(name = "Iniciar sesión", showBackground = true, widthDp = 360, heightDp = 800)
@Composable
private fun AuthSignInPreview() {
    PocketMindTheme {
        AuthScreen(
            state = AuthUiState(email = "hola@pocketmind.app"),
            onEmailChanged = {},
            onPasswordChanged = {},
            onPasswordConfirmationChanged = {},
            onSubmit = {},
            onGoogleSignIn = {},
            onModeChanged = {},
        )
    }
}

@Preview(name = "Registro oscuro", showBackground = true, widthDp = 360, heightDp = 800)
@Composable
private fun AuthSignUpDarkPreview() {
    PocketMindTheme(darkTheme = true) {
        AuthScreen(
            state = AuthUiState(mode = AuthMode.SIGN_UP),
            onEmailChanged = {},
            onPasswordChanged = {},
            onPasswordConfirmationChanged = {},
            onSubmit = {},
            onGoogleSignIn = {},
            onModeChanged = {},
        )
    }
}
