package com.pocketmind.presentation

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.pocketmind.presentation.auth.AuthScreen
import com.pocketmind.presentation.auth.AuthUiState
import com.pocketmind.presentation.home.HomeScreen
import com.pocketmind.presentation.home.HomeUiState
import com.pocketmind.presentation.profile.ProfileScreen
import com.pocketmind.presentation.profile.ProfileUiState
import com.pocketmind.shared.domain.model.CurrencyCode
import com.pocketmind.shared.domain.model.DashboardSummary
import com.pocketmind.shared.domain.model.Money
import com.pocketmind.ui.theme.PocketMindTheme
import org.junit.Rule
import org.junit.Test

class PocketMindVisualIdentityTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun signIn_exposesTheMainAuthenticationActions() {
        composeRule.setContent {
            PocketMindTheme {
                AuthScreen(
                    state = AuthUiState(),
                    onEmailChanged = {},
                    onPasswordChanged = {},
                    onPasswordConfirmationChanged = {},
                    onSubmit = {},
                    onGoogleSignIn = {},
                    onModeChanged = {},
                )
            }
        }

        composeRule.onNodeWithText("Qué bueno verte").assertIsDisplayed()
        composeRule.onNodeWithText("Iniciar sesión").assertIsDisplayed()
        composeRule.onNodeWithText("Continuar con Google").assertIsDisplayed()
    }

    @Test
    fun home_exposesFinancialSummaryAndNavigation() {
        composeRule.setContent {
            PocketMindTheme {
                HomeScreen(
                    uiState = HomeUiState.Content(sampleSummary()),
                    onOpenProfile = {},
                )
            }
        }

        composeRule.onNodeWithText("Balance de este mes").assertIsDisplayed()
        composeRule.onNodeWithText("Accesos rápidos").assertIsDisplayed()
        composeRule.onNodeWithText("Inicio").assertIsDisplayed()
        composeRule.onNodeWithText("Perfil").assertIsDisplayed()
    }

    @Test
    fun profile_opensPersonalDataEditorAndKeepsLogoutVisible() {
        composeRule.setContent {
            PocketMindTheme {
                ProfileScreen(
                    state = ProfileUiState(
                        isLoading = false,
                        displayName = "Sofía Martínez",
                        email = "sofia@pocketmind.app",
                    ),
                    onBack = {},
                    onDisplayNameChanged = {},
                    onEmailChanged = {},
                    onCurrencySelected = {},
                    onWeekStartsOnSelected = {},
                    onMonthlySummaryNotificationsChanged = {},
                    onSave = {},
                    onChangeEmail = {},
                    onChangePassword = { _, _ -> },
                    onSignOut = {},
                )
            }
        }

        composeRule.onNodeWithText("Cerrar sesión").assertIsDisplayed()
        composeRule.onNodeWithText("Datos personales").performClick()
        composeRule.onNodeWithText("Guardar nombre").assertIsDisplayed()
    }

    private fun sampleSummary() = DashboardSummary(
        availableBalance = Money(1_850_000, CurrencyCode.COP),
        monthlyIncome = Money(3_200_000, CurrencyCode.COP),
        monthlyExpense = Money(1_350_000, CurrencyCode.COP),
    )
}
