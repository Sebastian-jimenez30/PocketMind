package com.pocketmind.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.Analytics
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.Scaffold
import androidx.compose.ui.res.stringResource
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.NavType
import androidx.navigation.navArgument
import com.pocketmind.presentation.auth.AuthRoute
import com.pocketmind.presentation.home.HomeRoute
import com.pocketmind.presentation.transactions.TransactionEditorRoute
import com.pocketmind.presentation.transactions.TransactionsRoute
import com.pocketmind.presentation.transactions.ManualRecordRoute
import com.pocketmind.presentation.accounts.AccountEditorRoute
import com.pocketmind.presentation.accounts.AccountsRoute
import com.pocketmind.presentation.onboarding.FinancialOnboardingRoute
import com.pocketmind.presentation.profile.ProfileRoute
import com.pocketmind.presentation.products.ManualActionType
import com.pocketmind.presentation.products.ProductDetailRoute
import com.pocketmind.presentation.analysis.AnalysisRoute
import com.pocketmind.presentation.assistant.AssistantRoute
import com.pocketmind.ui.components.PocketBottomNavigation
import com.pocketmind.ui.components.PocketNavigationItem
import com.pocketmind.R

private const val AUTH_ROUTE = "auth"
private const val HOME_ROUTE = "home"
private const val FINANCIAL_ONBOARDING_ROUTE = "financial-onboarding"
private const val PROFILE_ROUTE = "profile"
private const val TRANSACTIONS_ROUTE = "transactions"
private const val MANUAL_RECORD_ROUTE = "manual-record"
private const val MANUAL_RECORD_PATTERN = "manual-record?operation={operation}&productId={productId}"
private const val TRANSACTION_EDIT_ROUTE = "transaction-edit/{transactionId}"
private const val ACCOUNTS_ROUTE = "accounts"
private const val ACCOUNT_NEW_ROUTE = "account-new"
private const val ACCOUNT_EDIT_ROUTE = "account-edit/{accountId}"
private const val PRODUCT_DETAIL_ROUTE = "product/{accountId}"
private const val ANALYSIS_ROUTE = "analysis"
private const val ASSISTANT_ROUTE = "assistant"

/** Root navigation graph. New product flows will be registered here by feature. */
@Composable
fun PocketMindApp() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = currentRoute != null &&
        currentRoute != AUTH_ROUTE &&
        currentRoute != FINANCIAL_ONBOARDING_ROUTE
    fun navigateTopLevel(route: String) {
        if (route == HOME_ROUTE) {
            navController.popBackStack(HOME_ROUTE, inclusive = false)
            return
        }
        navController.navigate(route) {
            popUpTo(HOME_ROUTE)
            launchSingleTop = true
            restoreState = false
        }
    }
    val activeTopLevel = when {
        currentRoute == PROFILE_ROUTE -> PROFILE_ROUTE
        currentRoute == ANALYSIS_ROUTE -> ANALYSIS_ROUTE
        currentRoute == TRANSACTIONS_ROUTE ||
            currentRoute?.startsWith(MANUAL_RECORD_ROUTE) == true ||
            currentRoute?.startsWith("transaction-") == true -> TRANSACTIONS_ROUTE
        else -> HOME_ROUTE
    }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                PocketBottomNavigation(
                    items = listOf(
                        PocketNavigationItem(
                            stringResource(R.string.nav_home),
                            Icons.Rounded.Home,
                            activeTopLevel == HOME_ROUTE,
                        ) { navigateTopLevel(HOME_ROUTE) },
                        PocketNavigationItem(
                            stringResource(R.string.nav_transactions),
                            Icons.AutoMirrored.Rounded.ReceiptLong,
                            activeTopLevel == TRANSACTIONS_ROUTE,
                        ) { navigateTopLevel(TRANSACTIONS_ROUTE) },
                        PocketNavigationItem(
                            stringResource(R.string.nav_analysis),
                            Icons.Rounded.Analytics,
                            activeTopLevel == ANALYSIS_ROUTE,
                        ) { navigateTopLevel(ANALYSIS_ROUTE) },
                        PocketNavigationItem(
                            stringResource(R.string.nav_profile),
                            Icons.Rounded.Person,
                            activeTopLevel == PROFILE_ROUTE,
                        ) { navigateTopLevel(PROFILE_ROUTE) },
                    ),
                )
            }
        },
    ) { rootPadding ->
        NavHost(
            navController = navController,
            startDestination = AUTH_ROUTE,
            modifier = Modifier.padding(rootPadding),
        ) {
        composable(AUTH_ROUTE) {
            AuthRoute(
                onAuthenticated = {
                    navController.navigate(FINANCIAL_ONBOARDING_ROUTE) {
                        popUpTo(AUTH_ROUTE) { inclusive = true }
                    }
                },
            )
        }
        composable(FINANCIAL_ONBOARDING_ROUTE) {
            FinancialOnboardingRoute(
                onCompleted = {
                    navController.navigate(HOME_ROUTE) {
                        popUpTo(FINANCIAL_ONBOARDING_ROUTE) { inclusive = true }
                    }
                },
            )
        }
        composable(HOME_ROUTE) {
            HomeRoute(
                onOpenTransactions = { navController.navigate(TRANSACTIONS_ROUTE) },
                onCreateTransaction = { type ->
                    navController.navigate(manualRecordRoute(operation = type.name))
                },
                onManageAccounts = { navController.navigate(ACCOUNTS_ROUTE) },
                onStartRecord = { navController.navigate(MANUAL_RECORD_ROUTE) },
                onOpenProduct = { id -> navController.navigate("product/$id") },
                onOpenAssistant = { navController.navigate(ASSISTANT_ROUTE) },
            )
        }
        composable(ASSISTANT_ROUTE) {
            AssistantRoute(onBack = { navController.popBackStack() })
        }
        composable(TRANSACTIONS_ROUTE) {
            TransactionsRoute(
                onCreate = { navController.navigate(MANUAL_RECORD_ROUTE) },
                onEdit = { id -> navController.navigate("transaction-edit/$id") },
                onManageAccounts = { navController.navigate(ACCOUNTS_ROUTE) },
                onBack = { navController.popBackStack() },
            )
        }
        composable(TRANSACTION_EDIT_ROUTE) {
            TransactionEditorRoute(onSaved = { navController.popBackStack() }, onBack = { navController.popBackStack() })
        }
        composable(ACCOUNTS_ROUTE) {
            AccountsRoute(
                onCreate = { navController.navigate(ACCOUNT_NEW_ROUTE) },
                onOpen = { id -> navController.navigate("product/$id") },
                onBack = { navController.popBackStack() },
            )
        }
        composable(
            route = MANUAL_RECORD_PATTERN,
            arguments = listOf(
                navArgument("operation") {
                    type = NavType.StringType
                    defaultValue = ""
                },
                navArgument("productId") {
                    type = NavType.StringType
                    defaultValue = ""
                },
            ),
        ) {
            ManualRecordRoute(
                onSaved = { navController.popBackStack() },
                onBack = { navController.popBackStack() },
            )
        }
        composable(ACCOUNT_NEW_ROUTE) {
            AccountEditorRoute(onSaved = { navController.popBackStack() }, onBack = { navController.popBackStack() })
        }
        composable(ACCOUNT_EDIT_ROUTE) {
            AccountEditorRoute(onSaved = { navController.popBackStack() }, onBack = { navController.popBackStack() })
        }
        composable(PRODUCT_DETAIL_ROUTE) {
            ProductDetailRoute(
                onBack = { navController.popBackStack() },
                onEdit = { id -> navController.navigate("account-edit/$id") },
                onAction = { id, action ->
                    if (action == ManualActionType.SAVINGS_RATE) {
                        navController.navigate("account-edit/$id")
                    } else {
                        navController.navigate(
                            manualRecordRoute(operation = action.name, productId = id),
                        )
                    }
                },
            )
        }
        composable(ANALYSIS_ROUTE) {
            AnalysisRoute(onBack = { navController.popBackStack() })
        }
        composable(PROFILE_ROUTE) {
            ProfileRoute(
                onBack = { navController.popBackStack() },
                onSignedOut = {
                    navController.navigate(AUTH_ROUTE) {
                        popUpTo(AUTH_ROUTE) { inclusive = true }
                    }
                },
            )
        }
    }
    }
}

private fun manualRecordRoute(operation: String = "", productId: String = ""): String =
    buildList {
        if (operation.isNotBlank()) add("operation=$operation")
        if (productId.isNotBlank()) add("productId=$productId")
    }.joinToString(
        separator = "&",
        prefix = if (operation.isBlank() && productId.isBlank()) {
            MANUAL_RECORD_ROUTE
        } else {
            "$MANUAL_RECORD_ROUTE?"
        },
    )
