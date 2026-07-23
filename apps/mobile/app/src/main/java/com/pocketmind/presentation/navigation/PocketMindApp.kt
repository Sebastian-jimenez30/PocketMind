package com.pocketmind.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.pocketmind.presentation.auth.AuthRoute
import com.pocketmind.presentation.home.HomeRoute
import com.pocketmind.presentation.profile.ProfileRoute

private const val AUTH_ROUTE = "auth"
private const val HOME_ROUTE = "home"
private const val PROFILE_ROUTE = "profile"

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
                    navController.navigate(HOME_ROUTE) {
                        popUpTo(AUTH_ROUTE) { inclusive = true }
                    }
                },
            )
        }
        composable(HOME_ROUTE) {
            HomeRoute(onOpenProfile = { navController.navigate(PROFILE_ROUTE) })
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
