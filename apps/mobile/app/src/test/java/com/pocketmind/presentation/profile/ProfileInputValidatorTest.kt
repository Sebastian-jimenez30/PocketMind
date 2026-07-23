package com.pocketmind.presentation.profile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProfileInputValidatorTest {
    @Test
    fun `rejects a display name over 80 characters`() {
        val result = ProfileInputValidator.validateDisplayName("a".repeat(81))

        assertEquals("El nombre puede tener máximo 80 caracteres.", result)
    }

    @Test
    fun `rejects invalid email`() {
        val result = ProfileInputValidator.validateEmail("correo-invalido")

        assertEquals("Ingresa un correo electrónico válido.", result)
    }

    @Test
    fun `rejects short password`() {
        val result = ProfileInputValidator.validatePassword("1234567", "1234567")

        assertEquals("La contraseña debe tener al menos 8 caracteres.", result)
    }

    @Test
    fun `accepts valid profile inputs`() {
        assertNull(ProfileInputValidator.validateDisplayName("Ana Gómez"))
        assertNull(ProfileInputValidator.validateEmail("ana@example.com"))
        assertNull(ProfileInputValidator.validatePassword("segura123", "segura123"))
    }
}
