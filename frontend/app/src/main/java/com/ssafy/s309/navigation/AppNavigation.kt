package com.ssafy.s309.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.ssafy.s309.ui.component.BottomNavBar
import com.ssafy.s309.ui.screen.GraphScreen
import com.ssafy.s309.ui.screen.auth.LandingScreen
import com.ssafy.s309.ui.screen.auth.LoginScreen
import com.ssafy.s309.ui.screen.auth.SignInScreen
import com.ssafy.s309.ui.screen.auth.SignUpScreen
import com.ssafy.s309.ui.screen.ble.BleScreen
import com.ssafy.s309.ui.screen.health.HealthSourceScreen
import com.ssafy.s309.ui.screen.main.GuardianScreen
import com.ssafy.s309.ui.screen.main.KikiAlarmDetailScreen
import com.ssafy.s309.ui.screen.main.KikiChatScreen
import com.ssafy.s309.ui.screen.main.KikiChatViewModel
import com.ssafy.s309.ui.screen.main.MainScreen
import com.ssafy.s309.ui.screen.main.MainViewModel
import com.ssafy.s309.ui.screen.main.MyAccountScreen
import com.ssafy.s309.ui.screen.main.ReportMenuSheet
import com.ssafy.s309.ui.screen.main.SettingsScreen
import com.ssafy.s309.ui.screen.main.defaultBottomNavItems
import com.ssafy.s309.ui.screen.onboarding.BasicHealthInfoScreen
import com.ssafy.s309.ui.screen.onboarding.BloodSugarRangeScreen
import com.ssafy.s309.ui.screen.onboarding.DiabetesTypeSelectionScreen
import com.ssafy.s309.ui.screen.onboarding.SignupDoneScreen
import com.ssafy.s309.ui.screen.projector.ProjectorScreen
import com.ssafy.s309.ui.theme.GlucoachColors
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

    object KikiChat : Screen("kiki_chat")

    object KikiAlarmDetail : Screen("kiki_alarm_detail")
}

@Composable
private fun SubScreenWithBottomNav(
    navController: NavHostController,
    selectedId: String = "profile",
    content: @Composable () -> Unit,
) {
    var showReportSheet by remember { mutableStateOf(false) }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(GlucoachColors.Background),
    ) {
        Box(modifier = Modifier.weight(1f)) {
            content()

            androidx.compose.animation.AnimatedVisibility(
                visible = showReportSheet,
                enter = fadeIn(animationSpec = tween(durationMillis = 280)),
                exit = fadeOut(animationSpec = tween(durationMillis = 240)),
            ) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.3f))
                            .clickable { showReportSheet = false },
                )
            }

            androidx.compose.animation.AnimatedVisibility(
                visible = showReportSheet,
                enter =
                    expandVertically(
                        expandFrom = Alignment.Bottom,
                        animationSpec = tween(durationMillis = 280),
                    ),
                exit =
                    shrinkVertically(
                        shrinkTowards = Alignment.Bottom,
                        animationSpec = tween(durationMillis = 240),
                    ),
                modifier = Modifier.align(Alignment.BottomCenter),
            ) {
                ReportMenuSheet(
                    onAIReport = {
                        showReportSheet = false
                        val mainEntry = navController.getBackStackEntry(Screen.Main.route)
                        mainEntry.savedStateHandle["requestedTab"] = "report"
                        navController.popBackStack(Screen.Main.route, inclusive = false)
                    },
                    onFoodReport = {
                        showReportSheet = false
                        val mainEntry = navController.getBackStackEntry(Screen.Main.route)
                        mainEntry.savedStateHandle["requestedTab"] = "food-report"
                        navController.popBackStack(Screen.Main.route, inclusive = false)
                    },
                    onClose = { showReportSheet = false },
                )
            }
        }

        BottomNavBar(
            items = defaultBottomNavItems(),
            selectedId = selectedId,
            onItemClick = { item ->
                when (item.id) {
                    "kiki" -> {
                        navController.navigate(Screen.KikiChat.route) {
                            launchSingleTop = true
                        }
                    }
                    "report" -> {
                        showReportSheet = !showReportSheet
                    }
                    else -> {
                        showReportSheet = false
                        val mainEntry = navController.getBackStackEntry(Screen.Main.route)
                        mainEntry.savedStateHandle["requestedTab"] = item.id
                        navController.popBackStack(Screen.Main.route, inclusive = false)
                    }
                }
            },
        )
    }
}

@Composable
fun AppNavigation(
    navController: NavHostController = rememberNavController(),
    pendingNavTarget: String? = null,
    onNavTargetConsumed: () -> Unit = {},
    pendingFoodName: String? = null,
    onFoodNameConsumed: () -> Unit = {},
) {
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
        composable(Screen.Main.route) { backStackEntry ->
            val requestedTab = backStackEntry.savedStateHandle.get<String>("requestedTab")
            val targetFoodName = backStackEntry.savedStateHandle.get<String>("targetFoodName")

            // FCM 알림 탭 → KikiAlarmDetail 딥링크 처리
            LaunchedEffect(pendingNavTarget, pendingFoodName) {
                if (pendingNavTarget == com.ssafy.s309.MainActivity.NAV_KIKI_ALARM_DETAIL) {
                    navController.navigate(Screen.KikiAlarmDetail.route) { launchSingleTop = true }
                    onNavTargetConsumed()
                }
                if (pendingNavTarget == com.ssafy.s309.MainActivity.NAV_FOOD_REPORT) {
                    backStackEntry.savedStateHandle["requestedTab"] = "food-report"
                    onNavTargetConsumed()
                }
                if (pendingFoodName != null) {
                    backStackEntry.savedStateHandle["targetFoodName"] = pendingFoodName
                    onFoodNameConsumed()
                }
            }

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
                onGraphClick = { navController.navigate(Screen.Graph.route) },
                onConnectedDeviceClick = { navController.navigate(Screen.HealthSource.route) },
                onLogoutClick = { authViewModel.logout() },
                onWithdrawClick = { password -> authViewModel.withdraw(password) },
                onSettingsClick = { navController.navigate(Screen.Settings.route) },
                onGuardianClick = { navController.navigate(Screen.Guardian.route) },
                onAccountClick = { navController.navigate(Screen.MyAccount.route) },
                onKikiChatClick = { navController.navigate(Screen.KikiChat.route) },
                onKikiAlarmClick = { navController.navigate(Screen.KikiAlarmDetail.route) },
                userEmail = authViewModel.userEmail,
                requestedTab = requestedTab,
                onTabHandled = { backStackEntry.savedStateHandle.remove<String>("requestedTab") },
                targetFoodName = targetFoodName,
                onTargetFoodHandled = { backStackEntry.savedStateHandle.remove<String>("targetFoodName") },
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

            SubScreenWithBottomNav(navController = navController) {
                MyAccountScreen(
                    onBack = { navController.popBackStack() },
                    onWithdrawClick = { password -> authViewModel.withdraw(password) },
                )
            }
        }
        composable(Screen.Graph.route) {
            SubScreenWithBottomNav(navController = navController, selectedId = "home") {
                GraphScreen(
                    onBack = { navController.popBackStack() },
                    onNavigateToBle = { navController.navigate(Screen.Ble.route) },
                )
            }
        }
        composable(Screen.Ble.route) {
            SubScreenWithBottomNav(navController = navController, selectedId = "home") {
                BleScreen(
                    onBack = { navController.popBackStack() },
                )
            }
        }
        composable(Screen.Settings.route) {
            SubScreenWithBottomNav(navController = navController) {
                SettingsScreen(
                    onBack = { navController.popBackStack() },
                    onProjectorClick = { navController.navigate(Screen.Projector.route) },
                )
            }
        }
        composable(Screen.Projector.route) {
            SubScreenWithBottomNav(navController = navController) {
                ProjectorScreen(
                    onBack = { navController.popBackStack() },
                )
            }
        }
        composable(Screen.Guardian.route) {
            SubScreenWithBottomNav(navController = navController) {
                GuardianScreen(
                    onBack = { navController.popBackStack() },
                )
            }
        }
        composable(Screen.HealthSource.route) {
            SubScreenWithBottomNav(navController = navController) {
                HealthSourceScreen(
                    onBack = { navController.popBackStack() },
                )
            }
        }
        composable(Screen.KikiChat.route) {
            val mainEntry =
                remember(navController) {
                    navController.getBackStackEntry(Screen.Main.route)
                }
            val mainViewModel: MainViewModel = hiltViewModel(mainEntry)
            val chatViewModel: KikiChatViewModel = hiltViewModel(mainEntry)
            LaunchedEffect(Unit) { mainViewModel.markAllNotificationsRead() }
            SubScreenWithBottomNav(navController = navController, selectedId = "home") {
                KikiChatScreen(
                    onBack = {
                        mainViewModel.markAllNotificationsRead()
                        navController.popBackStack()
                    },
                    onItemClick = { item ->
                        // 채팅 목록에서 열리는 알림은 이미 읽은 것으로 처리
                        mainViewModel.selectNotification(item.copy(isUnread = false))
                        navController.navigate(Screen.KikiAlarmDetail.route)
                    },
                    onReplySent = mainViewModel::markAllNotificationsRead,
                    viewModel = chatViewModel,
                )
            }
        }
        composable(Screen.KikiAlarmDetail.route) {
            val mainEntry =
                remember(navController) {
                    navController.getBackStackEntry(Screen.Main.route)
                }
            val mainViewModel: MainViewModel = hiltViewModel(mainEntry)
            val chatViewModel: KikiChatViewModel = hiltViewModel(mainEntry)
            val mainUiState by mainViewModel.uiState.collectAsState()
            val notification =
                mainUiState.selectedNotification
                    ?: mainUiState.notifications.firstOrNull { it.isUnread }
            SubScreenWithBottomNav(navController = navController, selectedId = "home") {
                KikiAlarmDetailScreen(
                    notification = notification,
                    onBack = { navController.popBackStack() },
                    onChatClick = { navController.navigate(Screen.KikiChat.route) },
                    onMealReply = { replyText, displayLabel ->
                        mainViewModel.sendMealReply(replyText, displayLabel)
                        chatViewModel.sendUserReply(null, displayLabel)
                    },
                    onViewed = mainViewModel::markAllNotificationsRead,
                )
            }
        }
    }
}
