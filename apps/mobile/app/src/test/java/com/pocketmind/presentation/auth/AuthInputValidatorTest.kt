package com.pocketmind.presentation.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AuthInputValidatorTest {
    @Test
    fun `rejects invalid email`() {
        val result = AuthInputValidator.validate(AuthUiState(email = "correo-invalido", password = "segura123"))

        assertEquals("Ingresa un correo electrónico válido.", result)
    }

    @Test
    fun `rejects a short password`() {
        val result = AuthInputValidator.validate(AuthUiState(email = "ana@example.com", password = "1234567"))

        assertEquals("Tu contraseña debe tener al menos 8 caracteres.", result)
    }

    @Test
    fun `rejects registration with different passwords`() {
        val result = AuthInputValidator.validate(
            AuthUiState(
                mode = AuthMode.SIGN_UP,
                email = "ana@example.com",
                password = "segura123",
                passwordConfirmation = "otra1234",
            ),
        )

        assertEquals("Las contraseñas no coinciden.", result)
    }

    @Test
    fun `accepts a valid registration`() {
        val result = AuthInputValidator.validate(
            AuthUiState(
                mode = AuthMode.SIGN_UP,
                email = "ana@example.com",
                password = "segura123",
                passwordConfirmation = "segura123",
            ),
        )

        assertNull(result)
    }
}
