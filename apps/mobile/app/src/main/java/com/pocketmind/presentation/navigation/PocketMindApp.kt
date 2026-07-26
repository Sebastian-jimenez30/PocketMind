package com.pocketmind.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.pocketmind.presentation.auth.AuthRoute
import com.pocketmind.presentation.home.HomeRoute
import com.pocketmind.presentation.transactions.TransactionEditorRoute
import com.pocketmind.presentation.transactions.TransactionsRoute
import com.pocketmind.presentation.accounts.AccountEditorRoute
import com.pocketmind.presentation.accounts.AccountsRoute
import com.pocketmind.presentation.onboarding.FinancialOnboardingRoute
import com.pocketmind.presentation.profile.ProfileRoute
import com.pocketmind.presentation.products.ManualActionRoute
import com.pocketmind.presentation.products.ManualActionType
import com.pocketmind.presentation.products.ProductDetailRoute
import com.pocketmind.presentation.analysis.AnalysisRoute
import com.pocketmind.shared.domain.model.TransactionType

private const val AUTH_ROUTE = "auth"
private const val HOME_ROUTE = "home"
private const val FINANCIAL_ONBOARDING_ROUTE = "financial-onboarding"
private const val PROFILE_ROUTE = "profile"
private const val TRANSACTIONS_ROUTE = "transactions"
private const val TRANSACTION_NEW_ROUTE = "transaction-new/{type}"
private const val TRANSACTION_EDIT_ROUTE = "transaction-edit/{transactionId}"
private const val ACCOUNTS_ROUTE = "accounts"
private const val ACCOUNT_NEW_ROUTE = "account-new"
private const val ACCOUNT_EDIT_ROUTE = "account-edit/{accountId}"
private const val PRODUCT_DETAIL_ROUTE = "product/{accountId}"
private const val MANUAL_ACTION_ROUTE = "manual-action/{accountId}/{action}"
private const val ANALYSIS_ROUTE = "analysis"

/** Root navigation graph. New product flows will be registered here by feature. */
@Composable
fun PocketMindApp() {
    val navController = rememberNavController()
    NavHost(
        navController = navController,
        startDestination = AUTH_ROUTE,
        modifier = Modifier,
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
                onOpenProfile = { navController.navigate(PROFILE_ROUTE) },
                onOpenTransactions = { navController.navigate(TRANSACTIONS_ROUTE) },
                onCreateTransaction = { type -> navController.navigate("transaction-new/${type.name}") },
                onManageAccounts = { navController.navigate(ACCOUNTS_ROUTE) },
                onOpenAnalysis = { navController.navigate(ANALYSIS_ROUTE) },
                onOpenProduct = { id -> navController.navigate("product/$id") },
            )
        }
        composable(TRANSACTIONS_ROUTE) {
            TransactionsRoute(
                onCreate = { navController.navigate("transaction-new/${TransactionType.EXPENSE.name}") },
                onEdit = { id -> navController.navigate("transaction-edit/$id") },
                onManageAccounts = { navController.navigate(ACCOUNTS_ROUTE) },
                onBack = { navController.popBackStack() },
            )
        }
        composable(TRANSACTION_NEW_ROUTE) {
            TransactionEditorRoute(onSaved = { navController.popBackStack() }, onBack = { navController.popBackStack() })
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
                onAction = { id, action -> navController.navigate("manual-action/$id/${action.name}") },
            )
        }
        composable(MANUAL_ACTION_ROUTE) {
            ManualActionRoute(
                onSaved = { navController.popBackStack() },
                onBack = { navController.popBackStack() },
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
