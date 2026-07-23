package com.pocketmind.presentation.profile

/** Keeps profile form rules independent from Android framework classes. */
object ProfileInputValidator {
    private const val MAXIMUM_DISPLAY_NAME_LENGTH = 80
    private const val MINIMUM_PASSWORD_LENGTH = 8
    private val emailRegex = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")

    fun validateDisplayName(displayName: String): String? = when {
        displayName.length > MAXIMUM_DISPLAY_NAME_LENGTH -> "El nombre puede tener máximo 80 caracteres."
        else -> null
    }

    fun validateEmail(email: String): String? = when {
        !emailRegex.matches(email.trim()) -> "Ingresa un correo electrónico válido."
        else -> null
    }

    fun validatePassword(password: String, confirmation: String): String? = when {
        password.length < MINIMUM_PASSWORD_LENGTH -> "La contraseña debe tener al menos 8 caracteres."
        password != confirmation -> "Las contraseñas no coinciden."
        else -> null
    }
}
