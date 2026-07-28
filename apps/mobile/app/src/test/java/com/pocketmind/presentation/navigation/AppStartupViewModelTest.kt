package com.pocketmind.presentation.navigation

import com.pocketmind.data.auth.AuthOperationResult
import com.pocketmind.data.auth.AuthRepository
import com.pocketmind.data.auth.AuthSessionState
import com.pocketmind.data.sync.SessionBootstrapper
import com.pocketmind.shared.domain.model.FinancialSetup
import com.pocketmind.shared.domain.repository.FinancialSetupRepository
import com.pocketmind.shared.domain.usecase.ObserveFinancialSetupCompletedUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AppStartupViewModelTest {

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
    fun `shows auth only after session restoration resolves as unauthenticated`() {
        val authRepository = FakeAuthRepository(AuthSessionState.RESOLVING)
        val viewModel = createViewModel(authRepository)

        assertEquals(AppDestination.RESOLVING, viewModel.uiState.value.destination)

        authRepository.session.value = AuthSessionState.UNAUTHENTICATED

        assertEquals(AppDestination.AUTH, viewModel.uiState.value.destination)
    }

    @Test
    fun `opens home after authenticated session bootstrap finds completed setup`() {
        val authRepository = FakeAuthRepository(AuthSessionState.AUTHENTICATED)
        val setupRepository = FakeFinancialSetupRepository(completed = true)
        var bootstrapCalls = 0

        val viewModel = createViewModel(
            authRepository = authRepository,
            setupRepository = setupRepository,
            bootstrapper = SessionBootstrapper {
                bootstrapCalls += 1
                Result.success(Unit)
            },
        )

        assertEquals(1, bootstrapCalls)
        assertEquals(AppDestination.HOME, viewModel.uiState.value.destination)
    }

    @Test
    fun `keeps new authenticated user in onboarding until setup is completed`() {
        val setupRepository = FakeFinancialSetupRepository(completed = false)
        val viewModel = createViewModel(
            authRepository = FakeAuthRepository(AuthSessionState.AUTHENTICATED),
            setupRepository = setupRepository,
        )

        assertEquals(AppDestination.ONBOARDING, viewModel.uiState.value.destination)

        setupRepository.completed.value = true

        assertEquals(AppDestination.HOME, viewModel.uiState.value.destination)
    }

    private fun createViewModel(
        authRepository: FakeAuthRepository,
        setupRepository: FakeFinancialSetupRepository = FakeFinancialSetupRepository(false),
        bootstrapper: SessionBootstrapper = SessionBootstrapper { Result.success(Unit) },
    ): AppStartupViewModel = AppStartupViewModel(
        authRepository = authRepository,
        sessionBootstrapper = bootstrapper,
        observeFinancialSetupCompleted = ObserveFinancialSetupCompletedUseCase(setupRepository),
    )

    private class FakeAuthRepository(initialState: AuthSessionState) : AuthRepository {
        val session = MutableStateFlow(initialState)

        override fun observeSessionState(): Flow<AuthSessionState> = session

        override suspend fun signIn(email: String, password: String): AuthOperationResult =
            AuthOperationResult.Success

        override suspend fun signUp(email: String, password: String): AuthOperationResult =
            AuthOperationResult.Success

        override suspend fun sendPasswordRecovery(email: String): AuthOperationResult =
            AuthOperationResult.Success

        override suspend fun signInWithGoogle(): AuthOperationResult =
            AuthOperationResult.ExternalFlowStarted
    }

    private class FakeFinancialSetupRepository(completed: Boolean) : FinancialSetupRepository {
        val completed = MutableStateFlow(completed)

        override fun observeIsCompleted(): Flow<Boolean> = completed

        override suspend fun saveInitialSetup(setup: FinancialSetup) {
            completed.value = true
        }
    }
}
