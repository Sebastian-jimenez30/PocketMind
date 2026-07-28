package com.pocketmind.data.auth

import kotlinx.coroutines.flow.Flow

enum class AuthSessionState {
    RESOLVING,
    AUTHENTICATED,
    UNAUTHENTICATED,
}

/** Platform data contract for authentication actions. */
interface AuthRepository {
    /**
     * Emits the complete session lifecycle so startup never treats Supabase's
     * restoration phase as a signed-out user.
     */
    fun observeSessionState(): Flow<AuthSessionState>
    suspend fun signIn(email: String, password: String): AuthOperationResult
    suspend fun signUp(email: String, password: String): AuthOperationResult
    suspend fun sendPasswordRecovery(email: String): AuthOperationResult
    suspend fun signInWithGoogle(): AuthOperationResult
}

sealed interface AuthOperationResult {
    data object Success : AuthOperationResult
    data object ExternalFlowStarted : AuthOperationResult
    data class Failure(val message: String) : AuthOperationResult
}
