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
import com.ssafy.s309.ui.screen.main.MyAccountScreen
import com.ssafy.s309.ui.screen.main.SettingsScreen
import com.ssafy.s309.ui.screen.onboarding.BasicHealthInfoScreen
import com.ssafy.s309.ui.screen.onboarding.BloodSugarRangeScreen
import com.ssafy.s309.ui.screen.onboarding.DiabetesTypeSelectionScreen
import com.ssafy.s309.ui.screen.onboarding.SignupDoneScreen
import com.ssafy.s309.ui.screen.projector.ProjectorScreen
import com.ssafy.s309.ui.viewmodel.AuthUiState
import com.ssafy.s309.ui.viewmodel.AuthViewModel

sealed class Screen(val route: String) {
    object Landing : Screen("landing")

    object Login : Screen("login")

    object SignIn : Screen("signin")

    object SignUp : Screen("signup")

    object BasicHealthInfo : Screen("basic_health_info")

    object DiabetesTypeSelection : Screen("diabetes_type_selection")

    object BloodSugarRange : Screen("blood_sugar_range")

    object SignupDone : Screen("signup_done")

    object Main : Screen("main")

    object Graph : Screen("graph")

    object HealthSource : Screen("health_source")

    object Ble : Screen("ble")

    object Settings : Screen("settings")

    object Guardian : Screen("guardian")

    object Projector : Screen("projector")

    object MyAccount : Screen("my_account")
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
            val autoLoginResult by authViewModel.autoLoginResult.collectAsState()

            LaunchedEffect(Unit) {
                authViewModel.tryAutoLogin()
            }

            LaunchedEffect(autoLoginResult) {
                when (autoLoginResult) {
                    true -> {
                        navController.navigate(Screen.Main.route) {
                            popUpTo(Screen.Landing.route) { inclusive = true }
                        }
                    }
                    false -> {
                        navController.navigate(Screen.Login.route) {
                            popUpTo(Screen.Landing.route) { inclusive = true }
                        }
                    }
                    null -> {}
                }
            }

            LandingScreen()
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
                onSignUpClick = { navController.navigate(Screen.SignUp.route) },
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
            SignUpScreen(
                onSignUpClick = { email, password, _, name, phone ->
                    authViewModel.saveSignupData(email, password, name, phone)
                    navController.navigate(Screen.BasicHealthInfo.route)
                },
                onAlreadyMemberClick = { navController.navigate(Screen.SignIn.route) },
                onBackClick = { navController.popBackStack() },
                initialEmail = authViewModel.pendingEmail,
                initialPassword = authViewModel.pendingPassword,
                initialName = authViewModel.pendingName,
                initialPhone = authViewModel.pendingPhone,
            )
        }
        composable(Screen.BasicHealthInfo.route) {
            BasicHealthInfoScreen(
                onNextClick = { birthDate, height, weight ->
                    authViewModel.saveHealthData(birthDate, height, weight)
                    navController.navigate(Screen.DiabetesTypeSelection.route)
                },
                onSkipClick = {
                    navController.navigate(Screen.SignupDone.route)
                },
                onBackClick = { navController.popBackStack() },
                initialAge = authViewModel.pendingAge,
                initialHeight = authViewModel.pendingHeight,
                initialWeight = authViewModel.pendingWeight,
            )
        }
        composable(Screen.DiabetesTypeSelection.route) {
            DiabetesTypeSelectionScreen(
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
            LaunchedEffect(Unit) {
                authViewModel.performPendingSignup()
            }

            LaunchedEffect(authState) {
                if (authState is AuthUiState.LoginSuccess) {
                    kotlinx.coroutines.delay(2000L)
                    authViewModel.resetState()
                    navController.navigate(Screen.Main.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                }
            }

            SignupDoneScreen(
                isLoading = authState is AuthUiState.Loading,
                errorMessage = (authState as? AuthUiState.Error)?.message,
                onRetryClick = { authViewModel.performPendingSignup() },
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
                onWithdrawClick = { password -> authViewModel.withdraw(password) },
                onSettingsClick = { navController.navigate(Screen.Settings.route) },
                onGuardianClick = { navController.navigate(Screen.Guardian.route) },
                onAccountClick = { navController.navigate(Screen.MyAccount.route) },
                userEmail = authViewModel.userEmail,
            )
        }
        composable(Screen.MyAccount.route) {
            LaunchedEffect(authState) {
                android.util.Log.d("AppNav", "MyAccount authState: $authState")
                if (authState is AuthUiState.WithdrawSuccess) {
                    authViewModel.resetState()
                    navController.navigate(Screen.Login.route) {
                        popUpTo(Screen.Main.route) { inclusive = true }
                    }
                }
            }

            MyAccountScreen(
                onBack = { navController.popBackStack() },
                onWithdrawClick = { password -> authViewModel.withdraw(password) },
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
                onProjectorClick = { navController.navigate(Screen.Projector.route) },
            )
        }
        composable(Screen.Projector.route) {
            ProjectorScreen(
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
