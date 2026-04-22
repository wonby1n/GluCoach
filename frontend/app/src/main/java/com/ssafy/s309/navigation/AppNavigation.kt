package com.ssafy.s309.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.ssafy.s309.ui.screen.GraphScreen
import com.ssafy.s309.ui.screen.auth.LandingScreen
import com.ssafy.s309.ui.screen.auth.LoginScreen
import com.ssafy.s309.ui.screen.auth.SignInScreen
import com.ssafy.s309.ui.screen.auth.SignUpScreen

sealed class Screen(val route: String) {
    object Landing : Screen("landing")

    object Login : Screen("login")

    object SignIn : Screen("signin")

    object SignUp : Screen("signup")

    object Graph : Screen("graph")
}

@Composable
fun AppNavigation(navController: NavHostController = rememberNavController()) {
    NavHost(
        navController = navController,
        startDestination = Screen.Landing.route,
    ) {
        composable(Screen.Landing.route) {
            LandingScreen(
                onNavigateToLogin = {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(Screen.Landing.route) { inclusive = true }
                    }
                },
            )
        }
        composable(Screen.Login.route) {
            LoginScreen(
                onSignInClick = { navController.navigate(Screen.SignIn.route) },
                onSignUpClick = { navController.navigate(Screen.SignUp.route) },
            )
        }
        composable(Screen.SignIn.route) {
            SignInScreen(
                onSignInClick = { _, _ ->
                    // TODO: 백엔드 연동 후 실제 인증 로직으로 교체
                    navController.navigate(Screen.Graph.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                },
                onForgotPasswordClick = {},
                onBackClick = { navController.popBackStack() },
            )
        }
        composable(Screen.SignUp.route) {
            SignUpScreen(
                onSignUpClick = { _, _, _ ->
                    // TODO: 백엔드 연동 후 실제 회원가입 로직으로 교체
                    navController.navigate(Screen.Graph.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                },
                onAlreadyMemberClick = { navController.navigate(Screen.SignIn.route) },
                onBackClick = { navController.popBackStack() },
            )
        }
        composable(Screen.Graph.route) {
            GraphScreen()
        }
    }
}
