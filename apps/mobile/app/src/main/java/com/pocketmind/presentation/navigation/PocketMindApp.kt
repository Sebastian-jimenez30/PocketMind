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

private const val AUTH_ROUTE = "auth"
private const val HOME_ROUTE = "home"
private const val FINANCIAL_ONBOARDING_ROUTE = "financial-onboarding"
private const val PROFILE_ROUTE = "profile"
private const val TRANSACTIONS_ROUTE = "transactions"
private const val TRANSACTION_NEW_ROUTE = "transaction-new"
private const val TRANSACTION_EDIT_ROUTE = "transaction-edit/{transactionId}"
private const val ACCOUNTS_ROUTE = "accounts"
private const val ACCOUNT_NEW_ROUTE = "account-new"
private const val ACCOUNT_EDIT_ROUTE = "account-edit/{accountId}"

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
                onCreateTransaction = { navController.navigate(TRANSACTION_NEW_ROUTE) },
                onManageAccounts = { navController.navigate(ACCOUNTS_ROUTE) },
            )
        }
        composable(TRANSACTIONS_ROUTE) {
            TransactionsRoute(
                onCreate = { navController.navigate(TRANSACTION_NEW_ROUTE) },
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
                onEdit = { id -> navController.navigate("account-edit/$id") },
                onBack = { navController.popBackStack() },
            )
        }
        composable(ACCOUNT_NEW_ROUTE) {
            AccountEditorRoute(onSaved = { navController.popBackStack() }, onBack = { navController.popBackStack() })
        }
        composable(ACCOUNT_EDIT_ROUTE) {
            AccountEditorRoute(onSaved = { navController.popBackStack() }, onBack = { navController.popBackStack() })
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
