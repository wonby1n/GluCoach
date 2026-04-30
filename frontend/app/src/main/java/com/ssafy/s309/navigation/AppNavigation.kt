package com.ssafy.s309.navigation

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.hilt.navigation.compose.hiltViewModel
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
import com.ssafy.s309.ui.screen.ble.BleScreen
import com.ssafy.s309.ui.screen.health.HealthSourceScreen
import com.ssafy.s309.ui.screen.main.GuardianScreen
import com.ssafy.s309.ui.screen.main.MainScreen
import com.ssafy.s309.ui.screen.main.SettingsScreen
import com.ssafy.s309.ui.screen.onboarding.BasicHealthInfoScreen
import com.ssafy.s309.ui.screen.onboarding.BloodSugarRangeScreen
import com.ssafy.s309.ui.screen.onboarding.DiabetesTypeSelectionScreen
import com.ssafy.s309.ui.screen.onboarding.SignupDoneScreen
import com.ssafy.s309.ui.screen.onboarding.TreatmentPillsScreen
import com.ssafy.s309.ui.screen.onboarding.TreatmentSelectionScreen
import com.ssafy.s309.ui.screen.onboarding.TreatmentTimeScreen
import com.ssafy.s309.ui.viewmodel.AuthUiState
import com.ssafy.s309.ui.viewmodel.AuthViewModel

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

    object HealthSource : Screen("health_source")

    object Ble : Screen("ble")

    object Settings : Screen("settings")

    object Guardian : Screen("guardian")
}

@Composable
fun AppNavigation(navController: NavHostController = rememberNavController()) {
    val authViewModel: AuthViewModel = hiltViewModel()
    val authState by authViewModel.uiState.collectAsState()

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
            LaunchedEffect(authState) {
                if (authState is AuthUiState.LoginSuccess) {
                    authViewModel.resetState()
                    navController.navigate(Screen.Main.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                }
            }

            SignInScreen(
                onSignInClick = { email, password ->
                    authViewModel.login(email, password)
                },
                onForgotPasswordClick = {},
                onBackClick = { navController.popBackStack() },
                isLoading = authState is AuthUiState.Loading,
                errorMessage = (authState as? AuthUiState.Error)?.message,
            )
        }
        composable(
            route = Screen.SignUp.route,
            enterTransition = { fadeIn() },
            exitTransition = { fadeOut() },
        ) {
            LaunchedEffect(authState) {
                if (authState is AuthUiState.LoginSuccess &&
                    (authState as AuthUiState.LoginSuccess).isNewUser
                ) {
                    authViewModel.resetState()
                    navController.navigate(Screen.BasicHealthInfo.route)
                }
            }

            SignUpScreen(
                onSignUpClick = { email, password, _ ->
                    authViewModel.signup(email, password)
                },
                onAlreadyMemberClick = { navController.navigate(Screen.SignIn.route) },
                onBackClick = { navController.popBackStack() },
                isLoading = authState is AuthUiState.Loading,
                errorMessage = (authState as? AuthUiState.Error)?.message,
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
                onNextClick = { selectedIndex ->
                    if (selectedIndex == 0) {
                        // 당뇨 전 혈당 관리 → 치료 관련 화면 스킵
                        navController.navigate(Screen.BloodSugarRange.route)
                    } else {
                        navController.navigate(Screen.TreatmentSelection.route)
                    }
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
            LaunchedEffect(authState) {
                if (authState is AuthUiState.LogoutSuccess ||
                    authState is AuthUiState.WithdrawSuccess
                ) {
                    authViewModel.resetState()
                    navController.navigate(Screen.Login.route) {
                        popUpTo(Screen.Main.route) { inclusive = true }
                    }
                }
            }

            MainScreen(
                mascotSlot = {
                    Image(
                        painter = painterResource(id = R.drawable.kiki_main),
                        contentDescription = "키키 캐릭터",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit,
                    )
                },
                onGraphClick = { navController.navigate(Screen.Graph.route) },
                onConnectedDeviceClick = { navController.navigate(Screen.HealthSource.route) },
                onLogoutClick = { authViewModel.logout() },
                onSettingsClick = { navController.navigate(Screen.Settings.route) },
                onGuardianClick = { navController.navigate(Screen.Guardian.route) },
                userEmail = authViewModel.userEmail,
            )
        }
        composable(Screen.Graph.route) {
            GraphScreen(
                onBack = { navController.popBackStack() },
                onNavigateToBle = { navController.navigate(Screen.Ble.route) },
            )
        }
        composable(Screen.Ble.route) {
            BleScreen(
                onBack = { navController.popBackStack() },
            )
        }
        composable(Screen.Settings.route) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
            )
        }
        composable(Screen.Guardian.route) {
            GuardianScreen(
                onBack = { navController.popBackStack() },
            )
        }
        composable(Screen.HealthSource.route) {
            HealthSourceScreen(
                onBack = { navController.popBackStack() },
            )
        }
    }
}
