package com.pocketmind.presentation.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel

@Composable
fun AuthRoute(
    onAuthenticated: () -> Unit,
    viewModel: AuthViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
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
    val palette = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BrandMark()
        Spacer(Modifier.height(20.dp))
        Text("PocketMind", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(
            text = "Tus finanzas, más claras cada día.",
            style = MaterialTheme.typography.bodyLarge,
            color = palette.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(36.dp))

        Column(modifier = Modifier.widthIn(max = 440.dp).fillMaxWidth()) {
            Text(titleFor(state.mode), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            Text(descriptionFor(state.mode), color = palette.onSurfaceVariant)
            Spacer(Modifier.height(24.dp))

            OutlinedTextField(
                value = state.email,
                onValueChange = onEmailChanged,
                label = { Text("Correo electrónico") },
                singleLine = true,
                enabled = !state.isLoading,
                modifier = Modifier.fillMaxWidth(),
            )
            if (state.mode != AuthMode.RECOVERY) {
                Spacer(Modifier.height(12.dp))
                PasswordField(
                    value = state.password,
                    onValueChange = onPasswordChanged,
                    label = "Contraseña",
                    enabled = !state.isLoading,
                )
            }
            if (state.mode == AuthMode.SIGN_UP) {
                Spacer(Modifier.height(12.dp))
                PasswordField(
                    value = state.passwordConfirmation,
                    onValueChange = onPasswordConfirmationChanged,
                    label = "Confirmar contraseña",
                    enabled = !state.isLoading,
                )
            }
            if (state.message != null) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = state.message,
                    color = if (state.isError) palette.error else palette.primary,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = onSubmit,
                enabled = !state.isLoading,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = palette.primary),
            ) {
                if (state.isLoading) CircularProgressIndicator(Modifier.size(22.dp), color = palette.onPrimary, strokeWidth = 2.dp)
                else Text(primaryActionFor(state.mode))
            }

            if (state.mode != AuthMode.RECOVERY) {
                Spacer(Modifier.height(16.dp))
                Text("o continúa con", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center, color = palette.onSurfaceVariant)
                Spacer(Modifier.height(16.dp))
                OutlinedButton(
                    onClick = onGoogleSignIn,
                    enabled = !state.isLoading,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) { Text("Continuar con Google") }
            }
            Spacer(Modifier.height(12.dp))
            AuthFooter(mode = state.mode, onModeChanged = onModeChanged)
        }
    }
}

@Composable
private fun BrandMark() = Box(
    modifier = Modifier.size(64.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
    contentAlignment = Alignment.Center,
) {
    Text("P", color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
}

@Composable
private fun PasswordField(value: String, onValueChange: (String) -> Unit, label: String, enabled: Boolean) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        enabled = enabled,
        visualTransformation = PasswordVisualTransformation(),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun AuthFooter(mode: AuthMode, onModeChanged: (AuthMode) -> Unit) = when (mode) {
    AuthMode.SIGN_IN -> Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        TextButton(onClick = { onModeChanged(AuthMode.RECOVERY) }) { Text("¿Olvidaste tu contraseña?") }
        TextButton(onClick = { onModeChanged(AuthMode.SIGN_UP) }) { Text("Crear una cuenta") }
    }
    AuthMode.SIGN_UP -> TextButton(onClick = { onModeChanged(AuthMode.SIGN_IN) }, modifier = Modifier.fillMaxWidth()) { Text("Ya tengo una cuenta") }
    AuthMode.RECOVERY -> TextButton(onClick = { onModeChanged(AuthMode.SIGN_IN) }, modifier = Modifier.fillMaxWidth()) { Text("Volver a iniciar sesión") }
}

private fun titleFor(mode: AuthMode) = when (mode) {
    AuthMode.SIGN_IN -> "Bienvenido de nuevo"
    AuthMode.SIGN_UP -> "Crea tu espacio financiero"
    AuthMode.RECOVERY -> "Recupera tu acceso"
}

private fun descriptionFor(mode: AuthMode) = when (mode) {
    AuthMode.SIGN_IN -> "Ingresa para ver tu panorama financiero."
    AuthMode.SIGN_UP -> "Empieza a entender mejor tu dinero en pocos pasos."
    AuthMode.RECOVERY -> "Te enviaremos un enlace seguro para cambiar tu contraseña."
}

private fun primaryActionFor(mode: AuthMode) = when (mode) {
    AuthMode.SIGN_IN -> "Iniciar sesión"
    AuthMode.SIGN_UP -> "Crear cuenta"
    AuthMode.RECOVERY -> "Enviar instrucciones"
}
