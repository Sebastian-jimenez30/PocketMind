package com.pocketmind.presentation.auth

/** Input rules shared by the authentication screens before any network request. */
object AuthInputValidator {
    private const val MINIMUM_PASSWORD_LENGTH = 8
    private val emailRegex = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")

    fun validate(state: AuthUiState): String? = when {
        !emailRegex.matches(state.email.trim()) -> "Ingresa un correo electrónico válido."
        state.mode != AuthMode.RECOVERY && state.password.length < MINIMUM_PASSWORD_LENGTH ->
            "Tu contraseña debe tener al menos $MINIMUM_PASSWORD_LENGTH caracteres."
        state.mode == AuthMode.SIGN_UP && state.password != state.passwordConfirmation ->
            "Las contraseñas no coinciden."
        else -> null
    }
}
