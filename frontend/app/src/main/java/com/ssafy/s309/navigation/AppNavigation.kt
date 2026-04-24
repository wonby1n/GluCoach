package com.ssafy.s309.navigation

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.ssafy.s309.R
import com.ssafy.s309.ui.screen.GraphScreen
import com.ssafy.s309.ui.screen.auth.LandingScreen
import com.ssafy.s309.ui.screen.auth.LoginScreen
import com.ssafy.s309.ui.screen.auth.SignInScreen
import com.ssafy.s309.ui.screen.auth.SignUpScreen
import com.ssafy.s309.ui.screen.main.MainScreen
import com.ssafy.s309.ui.screen.onboarding.BasicHealthInfoScreen
import com.ssafy.s309.ui.screen.onboarding.BloodSugarRangeScreen
import com.ssafy.s309.ui.screen.onboarding.DiabetesTypeSelectionScreen
import com.ssafy.s309.ui.screen.onboarding.SignupDoneScreen
import com.ssafy.s309.ui.screen.onboarding.TreatmentPillsScreen
import com.ssafy.s309.ui.screen.onboarding.TreatmentSelectionScreen
import com.ssafy.s309.ui.screen.onboarding.TreatmentTimeScreen

sealed class Screen(val route: String) {
    object Landing : Screen("landing")

    object Login : Screen("login")

    object SignIn : Screen("signin")

    object SignUp : Screen("signup")

    object BasicHealthInfo : Screen("basic_health_info")

    object DiabetesTypeSelection : Screen("diabetes_type_selection")

    object TreatmentSelection : Screen("treatment_selection")

    object TreatmentTime : Screen("treatment_time")

    object TreatmentPills : Screen("treatment_pills")

    object BloodSugarRange : Screen("blood_sugar_range")

    object SignupDone : Screen("signup_done")

    object Main : Screen("main")

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
                    navController.navigate(Screen.Main.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                },
                onForgotPasswordClick = {},
                onBackClick = { navController.popBackStack() },
            )
        }
        composable(
            route = Screen.SignUp.route,
            enterTransition = { fadeIn() },
            exitTransition = { fadeOut() },
        ) {
            SignUpScreen(
                onSignUpClick = { _, _, _ ->
                    // TODO: 백엔드 연동 후 실제 회원가입 로직으로 교체
                    navController.navigate(Screen.BasicHealthInfo.route)
                },
                onAlreadyMemberClick = { navController.navigate(Screen.SignIn.route) },
                onBackClick = { navController.popBackStack() },
            )
        }
        composable(Screen.BasicHealthInfo.route) {
            BasicHealthInfoScreen(
                onNextClick = { _, _, _ ->
                    navController.navigate(Screen.DiabetesTypeSelection.route)
                },
                onSkipClick = {
                    navController.navigate(Screen.Main.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                },
                onBackClick = { navController.popBackStack() },
            )
        }
        composable(Screen.DiabetesTypeSelection.route) {
            DiabetesTypeSelectionScreen(
                onNextClick = { _ ->
                    navController.navigate(Screen.TreatmentSelection.route)
                },
                onBackClick = { navController.popBackStack() },
            )
        }
        composable(Screen.TreatmentSelection.route) {
            TreatmentSelectionScreen(
                onNextClick = { _ ->
                    navController.navigate(Screen.TreatmentTime.route)
                },
                onBackClick = { navController.popBackStack() },
            )
        }
        composable(Screen.TreatmentTime.route) {
            TreatmentTimeScreen(
                onNextClick = { _ ->
                    navController.navigate(Screen.TreatmentPills.route)
                },
                onBackClick = { navController.popBackStack() },
            )
        }
        composable(Screen.TreatmentPills.route) {
            TreatmentPillsScreen(
                onNextClick = { _ ->
                    navController.navigate(Screen.BloodSugarRange.route)
                },
                onBackClick = { navController.popBackStack() },
            )
        }
        composable(Screen.BloodSugarRange.route) {
            BloodSugarRangeScreen(
                onNextClick = { _, _ ->
                    navController.navigate(Screen.SignupDone.route)
                },
                onSkipClick = {
                    navController.navigate(Screen.SignupDone.route)
                },
                onBackClick = { navController.popBackStack() },
            )
        }
        composable(Screen.SignupDone.route) {
            SignupDoneScreen(
                onNextClick = {
                    navController.navigate(Screen.Main.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                },
            )
        }
        composable(Screen.Main.route) {
            MainScreen(
                mascotSlot = {
                    Image(
                        painter = painterResource(id = R.drawable.kiki_main),
                        contentDescription = "키키 캐릭터",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit,
                    )
                },
            )
        }
        composable(Screen.Graph.route) {
            GraphScreen()
        }
    }
}
