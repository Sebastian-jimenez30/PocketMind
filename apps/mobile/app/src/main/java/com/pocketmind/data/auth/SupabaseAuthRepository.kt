package com.pocketmind.data.auth

import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.SupabaseClient
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class SupabaseAuthRepository @Inject constructor(
    private val supabase: SupabaseClient,
) : AuthRepository {
    override fun observeAuthentication(): Flow<Boolean> =
        supabase.auth.sessionStatus.map { it is SessionStatus.Authenticated }

    override suspend fun signIn(email: String, password: String): AuthOperationResult = execute {
        supabase.auth.signInWith(Email) {
            this.email = email
            this.password = password
        }
    }

    override suspend fun signUp(email: String, password: String): AuthOperationResult = execute {
        supabase.auth.signUpWith(Email, redirectUrl = AUTH_REDIRECT_URL) {
            this.email = email
            this.password = password
        }
    }

    override suspend fun sendPasswordRecovery(email: String): AuthOperationResult = execute {
        supabase.auth.resetPasswordForEmail(email, redirectUrl = AUTH_REDIRECT_URL)
    }

    override suspend fun signInWithGoogle(): AuthOperationResult = try {
        supabase.auth.signInWith(Google, redirectUrl = AUTH_REDIRECT_URL)
        AuthOperationResult.ExternalFlowStarted
    } catch (exception: Exception) {
        AuthOperationResult.Failure(exception.toSafeMessage())
    }

    private suspend fun execute(block: suspend () -> Unit): AuthOperationResult = try {
        block()
        AuthOperationResult.Success
    } catch (exception: Exception) {
        AuthOperationResult.Failure(exception.toSafeMessage())
    }

    private fun Exception.toSafeMessage(): String =
        message?.takeIf(String::isNotBlank) ?: "No fue posible completar la operación. Inténtalo de nuevo."

    private companion object {
        const val AUTH_REDIRECT_URL = "pocketmind://auth"
    }
}
