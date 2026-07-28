package com.pocketmind.presentation.auth

import com.pocketmind.data.auth.AuthOperationResult
import com.pocketmind.data.auth.AuthRepository
import com.pocketmind.data.auth.AuthSessionState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `finishes successful sign in without exposing navigation state`() {
        val repository = FakeAuthRepository()
        val viewModel = AuthViewModel(
            authRepository = repository,
        )

        viewModel.updateEmail("ana@example.com")
        viewModel.updatePassword("segura123")
        viewModel.submit()

        assertFalse(viewModel.uiState.value.isLoading)
    }

    private class FakeAuthRepository : AuthRepository {
        override fun observeSessionState(): Flow<AuthSessionState> =
            flowOf(AuthSessionState.UNAUTHENTICATED)

        override suspend fun signIn(email: String, password: String): AuthOperationResult =
            AuthOperationResult.Success

        override suspend fun signUp(email: String, password: String): AuthOperationResult =
            AuthOperationResult.Success

        override suspend fun sendPasswordRecovery(email: String): AuthOperationResult =
            AuthOperationResult.Success

        override suspend fun signInWithGoogle(): AuthOperationResult =
            AuthOperationResult.ExternalFlowStarted
    }
}
