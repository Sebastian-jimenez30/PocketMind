package com.pocketmind.data.auth

/** Platform data contract for authentication actions. */
interface AuthRepository {
    suspend fun hasActiveSession(): Boolean
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
