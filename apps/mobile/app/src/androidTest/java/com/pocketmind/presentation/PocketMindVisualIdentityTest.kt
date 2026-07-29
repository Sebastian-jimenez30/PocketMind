package com.pocketmind.presentation

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
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
import com.pocketmind.ui.testing.PocketMindTestTags
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
        composeRule.onNode(hasText("Iniciar sesión") and hasClickAction()).assertIsDisplayed()
        composeRule.onNodeWithText("Continuar con Google").assertIsDisplayed()
    }

    @Test
    fun home_exposesFinancialSummaryAndActions() {
        composeRule.setContent {
            PocketMindTheme {
                HomeScreen(
                    uiState = HomeUiState.Content(sampleSummary()),
                    onOpenTransactions = {},
                    onCreateTransaction = {},
                    onManageAccounts = {},
                    onStartRecord = {},
                    onOpenProduct = {},
                    onOpenAssistant = {},
                    onOpenBudgets = {},
                )
            }
        }

        composeRule.onNodeWithText("Panorama").assertIsDisplayed()
        composeRule.onNodeWithText("Acciones").assertIsDisplayed()
        composeRule.onNodeWithTag(PocketMindTestTags.HOME_CONTENT)
            .performScrollToNode(hasText("Productos"))
        composeRule.onNodeWithText("Productos").assertIsDisplayed()
        composeRule.onNodeWithTag(PocketMindTestTags.HOME_CONTENT)
            .performScrollToNode(hasText("Presupuestos"))
        composeRule.onNodeWithText("Presupuestos").assertIsDisplayed()
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
                    onSyncNow = {},
                    onSignOut = {},
                )
            }
        }

        val profileContent = composeRule.onNodeWithTag(PocketMindTestTags.PROFILE_CONTENT)
        profileContent.performScrollToNode(hasText("Cerrar sesión"))
        composeRule.onNodeWithText("Cerrar sesión").assertIsDisplayed()
        profileContent.performScrollToNode(hasText("Datos"))
        composeRule.onNodeWithText("Datos").performClick()
        composeRule.onNodeWithText("Guardar nombre").assertIsDisplayed()
    }

    private fun sampleSummary() = DashboardSummary(
        availableBalance = Money(1_850_000, CurrencyCode.COP),
        monthlyIncome = Money(3_200_000, CurrencyCode.COP),
        monthlyExpense = Money(1_350_000, CurrencyCode.COP),
    )
}
