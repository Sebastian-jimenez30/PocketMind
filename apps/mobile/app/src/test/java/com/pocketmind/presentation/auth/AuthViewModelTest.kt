package com.pocketmind.presentation.auth

import com.pocketmind.data.auth.AuthOperationResult
import com.pocketmind.data.auth.AuthRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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
    fun `authenticates when Google callback creates a session`() {
        val repository = FakeAuthRepository()
        val viewModel = AuthViewModel(repository)

        assertFalse(viewModel.uiState.value.isAuthenticated)

        repository.authentication.value = true

        assertTrue(viewModel.uiState.value.isAuthenticated)
    }

    private class FakeAuthRepository : AuthRepository {
        val authentication = MutableStateFlow(false)

        override fun observeAuthentication(): Flow<Boolean> = authentication

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
