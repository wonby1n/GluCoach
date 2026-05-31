package com.ssafy.s309.ui.screen.main

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.MailOutline
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ssafy.s309.R
import com.ssafy.s309.ui.component.BottomNavBar
import com.ssafy.s309.ui.component.BottomNavItem
import com.ssafy.s309.ui.component.CurrentGlucoseCard
import com.ssafy.s309.ui.component.GlucoseChartCard
import com.ssafy.s309.ui.component.KikiCharacterMapper
import com.ssafy.s309.ui.component.KikiImage
import com.ssafy.s309.ui.component.SummaryStatCard
import com.ssafy.s309.ui.theme.GlucoachColors
import com.ssafy.s309.ui.theme.GlucoachCorner
import com.ssafy.s309.ui.theme.GlucoachSpacing
import kotlinx.coroutines.delay
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

@Composable
fun MainScreen(
    viewModel: MainViewModel = hiltViewModel(),
    mascotSlot: (@Composable () -> Unit)? = null,
    bellIcon: (@Composable () -> Unit)? = null,
    mealPinIcon: (@Composable () -> Unit)? = null,
    onGraphClick: () -> Unit = {},
    onConnectedDeviceClick: () -> Unit = {},
    onBleClick: () -> Unit = {},
    onLogoutClick: () -> Unit = {},
    onWithdrawClick: (String) -> Unit = {},
    onSettingsClick: () -> Unit = {},
    onGuardianClick: () -> Unit = {},
    onAccountClick: () -> Unit = {},
    onKikiChatClick: () -> Unit = {},
    onKikiAlarmClick: () -> Unit = {},
    userEmail: String = "",
    requestedTab: String? = null,
    onTabHandled: () -> Unit = {},
    targetFoodName: String? = null,
    onTargetFoodHandled: () -> Unit = {},
    targetFoodA: String? = null,
    targetFoodB: String? = null,
    onTargetFoodABHandled: () -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    MainScreenContent(
        state = uiState,
        onBellClick = {
            // 패널 열 때마다 최신 알림 + timeAgo 재조회 (다른 단말 메시지/시간 sync용)
            viewModel.loadDashboard()
            viewModel.openNotificationPanel()
        },
        onNotificationBack = viewModel::closeNotificationPanel,
        onClearAllNotifications = viewModel::clearAllNotifications,
        onMarkAllNotificationsRead = viewModel::markAllNotificationsRead,
        onNotificationClick = viewModel::selectNotification,
        onDismissNotificationDetail = viewModel::dismissNotificationDetail,
        mascotSlot = mascotSlot,
        bellIcon = bellIcon,
        mealPinIcon = mealPinIcon,
        onGraphClick = onGraphClick,
        onConnectedDeviceClick = onConnectedDeviceClick,
        onBleClick = onBleClick,
        onLogoutClick = onLogoutClick,
        onWithdrawClick = onWithdrawClick,
        onSettingsClick = onSettingsClick,
        onGuardianClick = onGuardianClick,
        onAccountClick = onAccountClick,
        onKikiChatClick = onKikiChatClick,
        onKikiAlarmClick = onKikiAlarmClick,
        userEmail = userEmail,
        requestedTab = requestedTab,
        onTabHandled = onTabHandled,
        targetFoodName = targetFoodName,
        onTargetFoodHandled = onTargetFoodHandled,
        targetFoodA = targetFoodA,
        targetFoodB = targetFoodB,
        onTargetFoodABHandled = onTargetFoodABHandled,
        // [DEBUG_KIKI_TEST]
    )
}

@Composable
fun MainScreenContent(
    state: MainUiState,
    onBellClick: () -> Unit,
    onNotificationBack: () -> Unit,
    onClearAllNotifications: () -> Unit,
    onMarkAllNotificationsRead: () -> Unit = {},
    onNotificationClick: (com.ssafy.s309.data.model.NotificationItem) -> Unit = {},
    onDismissNotificationDetail: () -> Unit = {},
    mascotSlot: (@Composable () -> Unit)? = null,
    bellIcon: (@Composable () -> Unit)? = null,
    mealPinIcon: (@Composable () -> Unit)? = null,
    onGraphClick: () -> Unit = {},
    onConnectedDeviceClick: () -> Unit = {},
    onBleClick: () -> Unit = {},
    onLogoutClick: () -> Unit = {},
    onWithdrawClick: (String) -> Unit = {},
    onSettingsClick: () -> Unit = {},
    onGuardianClick: () -> Unit = {},
    onAccountClick: () -> Unit = {},
    onKikiChatClick: () -> Unit = {},
    onKikiAlarmClick: () -> Unit = {},
    userEmail: String = "",
    requestedTab: String? = null,
    onTabHandled: () -> Unit = {},
    targetFoodName: String? = null,
    onTargetFoodHandled: () -> Unit = {},
    targetFoodA: String? = null,
    targetFoodB: String? = null,
    onTargetFoodABHandled: () -> Unit = {},
) {
    var selectedTab by rememberSaveable { mutableStateOf("home") }
    var mealLogTargetDate by remember { mutableStateOf<String?>(null) }
    var showReportSheet by remember { mutableStateOf(false) }
    var showAddMenuSheet by remember { mutableStateOf(false) }
    var showCameraPanel by remember { mutableStateOf(false) }
    var isAbMode by remember { mutableStateOf(false) }
    var initialFoodA by remember { mutableStateOf<String?>(null) }
    var initialFoodB by remember { mutableStateOf<String?>(null) }
    var keyboardPredictFoodName by remember { mutableStateOf<String?>(null) }
    var cumulativeDrag by remember { mutableFloatStateOf(0f) }

    androidx.compose.runtime.LaunchedEffect(requestedTab) {
        if (requestedTab != null) {
            showReportSheet = false
            when (requestedTab) {
                "food-scan" -> {
                    showCameraPanel = true
                    isAbMode = false
                }
                "food-comparison" -> {
                    initialFoodA = targetFoodA
                    initialFoodB = targetFoodB
                    onTargetFoodABHandled()
                    showCameraPanel = true
                    isAbMode = true
                }
                "glucose-predict" -> {
                    keyboardPredictFoodName = targetFoodName
                    onTargetFoodHandled()
                }
                else -> selectedTab = requestedTab
            }
            onTabHandled()
        }
    }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(GlucoachColors.Background),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier =
                    Modifier
                        .weight(1f)
                        .pointerInput(showCameraPanel, selectedTab) {
                            if (!showCameraPanel) {
                                val swipeTabs = listOf("home", "meallog", "profile")
                                detectHorizontalDragGestures(
                                    onDragStart = { cumulativeDrag = 0f },
                                    onDragEnd = {
                                        val threshold = 80.dp.toPx()
                                        if (cumulativeDrag > threshold) {
                                            val idx = swipeTabs.indexOf(selectedTab)
                                            if (selectedTab == "home") {
                                                showCameraPanel = true
                                                isAbMode = false
                                            } else if (idx > 0) {
                                                selectedTab = swipeTabs[idx - 1]
                                            }
                                        } else if (cumulativeDrag < -threshold) {
                                            val idx = swipeTabs.indexOf(selectedTab)
                                            if (idx in 0 until swipeTabs.lastIndex) {
                                                selectedTab = swipeTabs[idx + 1]
                                            }
                                        }
                                        cumulativeDrag = 0f
                                    },
                                    onDragCancel = { cumulativeDrag = 0f },
                                    onHorizontalDrag = { _, dragAmount ->
                                        cumulativeDrag += dragAmount
                                    },
                                )
                            }
                        },
            ) {
                Crossfade(
                    targetState = selectedTab,
                    animationSpec = tween(300),
                    modifier = Modifier.fillMaxSize(),
                    label = "tab-crossfade",
                ) { tab ->
                    when (tab) {
                        "profile" ->
                            MyPageContent(
                                onAccountClick = onAccountClick,
                                onLogoutClick = onLogoutClick,
                                onWithdrawClick = onWithdrawClick,
                                onDeviceClick = onConnectedDeviceClick,
                                onBleClick = onBleClick,
                                onHealthDetailClick = onSettingsClick,
                                onGuardianClick = onGuardianClick,
                                isDeviceConnected = state.isDeviceConnected,
                                userEmail = userEmail,
                            )
                        "meallog" ->
                            MealLogContent(
                                onBackToHome = { selectedTab = "home" },
                                onNavigateToFoodReport = { selectedTab = "food-report" },
                                initialDate = mealLogTargetDate.also { mealLogTargetDate = null },
                            )
                        "report" -> AIReportContent()
                        "food-report" ->
                            FoodReportContent(
                                onBack = { selectedTab = "meallog" },
                                onNavigateToMealLog = { date, _ ->
                                    mealLogTargetDate = date
                                    selectedTab = "meallog"
                                },
                                targetFoodName = targetFoodName,
                                onTargetFoodHandled = onTargetFoodHandled,
                            )

                        else ->
                            Column(
                                modifier =
                                    Modifier
                                        .fillMaxSize()
                                        .verticalScroll(rememberScrollState())
                                        .padding(horizontal = 22.dp),
                            ) {
                                Spacer(modifier = Modifier.height(GlucoachSpacing.xxl))
                                TodayConditionHeader(
                                    onAddClick = {
                                        showReportSheet = false
                                        showAddMenuSheet = !showAddMenuSheet
                                    },
                                    onBellClick = onBellClick,
                                    hasUnread = state.notifications.any { it.isUnread },
                                    bellIcon = bellIcon,
                                )
                                Spacer(modifier = Modifier.height(GlucoachSpacing.xxl))

                                val kikiDrawable =
                                    KikiCharacterMapper.resolve(
                                        glucoseMgDl = state.currentGlucoseMgDl,
                                        trendRateMgDlPerMin = state.trendRateMgDlPerMin,
                                        diabetesType = state.diabetesType,
                                    )
                                val kikiTarget =
                                    if (System.currentTimeMillis() < state.walkingFeedbackUntil) {
                                        R.drawable.kiki_run
                                    } else {
                                        kikiDrawable
                                    }
                                var displayedKiki by remember { mutableIntStateOf(kikiTarget) }
                                var kikiSwitchTime by remember { mutableLongStateOf(0L) }
                                LaunchedEffect(kikiTarget) {
                                    if (displayedKiki == kikiTarget) return@LaunchedEffect
                                    val elapsed = System.currentTimeMillis() - kikiSwitchTime
                                    val remaining = kikiCycleDuration(displayedKiki) - elapsed
                                    if (remaining > 0) delay(remaining)
                                    displayedKiki = kikiTarget
                                    kikiSwitchTime = System.currentTimeMillis()
                                }

                                KikiSuggestionCard(
                                    notifications = state.notifications,
                                    isNewUser = state.isNewUser,
                                    onAlarmClick = onKikiAlarmClick,
                                    onChatClick = onKikiChatClick,
                                )
                                Spacer(modifier = Modifier.height(GlucoachSpacing.xl))

                                CurrentGlucoseCard(
                                    currentMgDl = state.currentGlucoseMgDl ?: 0,
                                    diffFromPrevious = state.diffFromPrevious,
                                    glucoseRange = state.glucoseRange,
                                    mascotSlot = {
                                        Box(
                                            modifier = Modifier.fillMaxSize(),
                                            contentAlignment =
                                                if (displayedKiki == R.drawable.kiki_run) {
                                                    Alignment.Center
                                                } else {
                                                    Alignment.BottomEnd
                                                },
                                        ) {
                                            KikiImage(
                                                drawableRes = displayedKiki,
                                                modifier =
                                                    if (displayedKiki == R.drawable.kiki_run) {
                                                        Modifier.fillMaxSize(
                                                            0.8f,
                                                        )
                                                    } else {
                                                        Modifier.fillMaxSize()
                                                    },
                                            )
                                        }
                                    },
                                )
                                Spacer(modifier = Modifier.height(GlucoachSpacing.xl))

                                val twoHoursAgoMs = remember { System.currentTimeMillis() - 2 * 60 * 60 * 1000L }
                                val chartTimeLabels =
                                    remember {
                                        val sdf = SimpleDateFormat("HH:mm", Locale.KOREA)
                                        sdf.timeZone = TimeZone.getTimeZone("Asia/Seoul")
                                        val cal = Calendar.getInstance(TimeZone.getTimeZone("Asia/Seoul"))
                                        listOf(-2, -1, 0).map { offset ->
                                            val c = cal.clone() as Calendar
                                            c.add(Calendar.HOUR_OF_DAY, offset)
                                            c.set(Calendar.MINUTE, 0)
                                            sdf.format(c.time)
                                        }
                                    }
                                GlucoseChartCard(
                                    readings = state.glucoseSeries.filter { it.timestampMillis >= twoHoursAgoMs },
                                    range = state.glucoseRange,
                                    isDeviceConnected = state.isDeviceConnected,
                                    hoursLabel = "최근 2시간",
                                    timeLabels = chartTimeLabels,
                                    onClick = onGraphClick,
                                )
                                Spacer(modifier = Modifier.height(GlucoachSpacing.xl))

                                SummaryRow(
                                    steps = state.summary.steps,
                                    sleepMinutes = state.summary.sleepMinutes,
                                )
                                Spacer(modifier = Modifier.height(GlucoachSpacing.xxl))
                            }
                    }
                }

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
                            selectedTab = "report"
                        },
                        onFoodReport = {
                            showReportSheet = false
                            selectedTab = "food-report"
                        },
                        onFoodComparison = {
                            showReportSheet = false
                            showCameraPanel = true
                            isAbMode = true
                        },
                        onClose = { showReportSheet = false },
                    )
                }

                androidx.compose.animation.AnimatedVisibility(
                    visible = showAddMenuSheet,
                    enter = fadeIn(animationSpec = tween(durationMillis = 280)),
                    exit = fadeOut(animationSpec = tween(durationMillis = 240)),
                ) {
                    Box(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.3f))
                                .clickable { showAddMenuSheet = false },
                    )
                }

                androidx.compose.animation.AnimatedVisibility(
                    visible = showAddMenuSheet,
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
                    AddMenuSheet(
                        onFoodScan = {
                            showAddMenuSheet = false
                            showCameraPanel = true
                            isAbMode = false
                        },
                        onClose = { showAddMenuSheet = false },
                    )
                }
            }

            val hasUnreadKiki = state.notifications.any { it.isUnread }
            BottomNavBar(
                items = defaultBottomNavItems(hasUnreadKiki = hasUnreadKiki),
                selectedId = selectedTab,
                onItemClick = { item ->
                    when (item.id) {
                        "kiki" -> {
                            showReportSheet = false
                            showAddMenuSheet = false
                            onKikiChatClick()
                        }
                        "report" -> {
                            showAddMenuSheet = false
                            showReportSheet = !showReportSheet
                        }
                        else -> {
                            showReportSheet = false
                            showAddMenuSheet = false
                            selectedTab = item.id
                        }
                    }
                },
            )
        }

        AnimatedVisibility(
            visible = showCameraPanel,
            enter = slideInHorizontally(animationSpec = tween(300)) { -it },
            exit = slideOutHorizontally(animationSpec = tween(300)) { -it },
            modifier = Modifier.fillMaxSize(),
        ) {
            InstagramCameraPanel(
                isAbMode = isAbMode,
                onModeChange = { isAbMode = it },
                onClose = {
                    showCameraPanel = false
                    initialFoodA = null
                    initialFoodB = null
                },
                onMealSaved = {
                    showCameraPanel = false
                    initialFoodA = null
                    initialFoodB = null
                    mealLogTargetDate =
                        java.time.LocalDate.now()
                            .format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE)
                    selectedTab = "meallog"
                },
                onNavigateHome = {
                    showCameraPanel = false
                    initialFoodA = null
                    initialFoodB = null
                    selectedTab = "home"
                },
                initialFoodA = initialFoodA,
                initialFoodB = initialFoodB,
            )
        }

        AnimatedVisibility(
            visible = keyboardPredictFoodName != null,
            enter = slideInHorizontally(animationSpec = tween(300)) { -it },
            exit = slideOutHorizontally(animationSpec = tween(300)) { -it },
            modifier = Modifier.fillMaxSize(),
        ) {
            keyboardPredictFoodName?.let { foodName ->
                KeyboardPredictionContent(
                    foodName = foodName,
                    onClose = { keyboardPredictFoodName = null },
                )
            }
        }

        AnimatedVisibility(
            visible = state.isNotificationPanelOpen,
            enter = fadeIn(animationSpec = tween(durationMillis = 280)),
            exit = fadeOut(animationSpec = tween(durationMillis = 240)),
        ) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.3f))
                        .clickable(onClick = onNotificationBack),
            )
        }

        AnimatedVisibility(
            visible = state.isNotificationPanelOpen,
            enter =
                slideInHorizontally(
                    initialOffsetX = { fullWidth -> fullWidth },
                    animationSpec = tween(durationMillis = 280),
                ),
            exit =
                slideOutHorizontally(
                    targetOffsetX = { fullWidth -> fullWidth },
                    animationSpec = tween(durationMillis = 240),
                ),
            modifier =
                Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .fillMaxWidth(0.85f),
        ) {
            NotificationPanel(
                notifications = state.notifications,
                onBack = onNotificationBack,
                onClearAll = onClearAllNotifications,
                onMarkAllRead = onMarkAllNotificationsRead,
                onNotificationClick = onNotificationClick,
            )
        }

        if (state.selectedNotification != null) {
            NotificationDetailOverlay(
                notification = state.selectedNotification,
                onDismiss = onDismissNotificationDetail,
            )
        }
    }
}

@Composable
private fun TodayConditionHeader(
    onAddClick: () -> Unit,
    onBellClick: () -> Unit,
    hasUnread: Boolean,
    bellIcon: (@Composable () -> Unit)? = null,
) {
    Box(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier =
                Modifier
                    .size(32.dp)
                    .align(Alignment.CenterStart)
                    .clickable(onClick = onAddClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = "음식 촬영",
                tint = GlucoachColors.TextPrimary,
                modifier = Modifier.size(24.dp),
            )
        }

        androidx.compose.foundation.Image(
            painter = painterResource(id = R.drawable.glucoach_logo),
            contentDescription = "Glucoach",
            modifier =
                Modifier
                    .height(24.dp)
                    .align(Alignment.Center),
            contentScale = ContentScale.Fit,
        )

        Box(
            modifier =
                Modifier
                    .size(32.dp)
                    .align(Alignment.CenterEnd)
                    .clickable(onClick = onBellClick),
            contentAlignment = Alignment.Center,
        ) {
            if (bellIcon != null) {
                bellIcon()
            } else {
                Icon(
                    imageVector = Icons.Outlined.Notifications,
                    contentDescription = if (hasUnread) "새 알림" else "알림",
                    tint = GlucoachColors.Primary,
                    modifier = Modifier.size(24.dp),
                )
            }
            if (hasUnread) {
                Box(
                    modifier =
                        Modifier
                            .align(Alignment.TopEnd)
                            .size(8.dp)
                            .background(Color(0xFFE53935), shape = CircleShape),
                )
            }
        }
    }
}

@Composable
private fun KikiSuggestionCard(
    notifications: List<com.ssafy.s309.data.model.NotificationItem>,
    isNewUser: Boolean,
    onAlarmClick: () -> Unit,
    onChatClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val unread = notifications.filter { it.isUnread }
    val bannerText = resolveBannerText(unread, isNewUser)
    val onClick = if (unread.size >= 2) onChatClick else onAlarmClick

    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .shadow(3.dp, RoundedCornerShape(GlucoachCorner.card))
                .clip(RoundedCornerShape(GlucoachCorner.card))
                .background(GlucoachColors.Surface)
                .border(
                    width = 1.dp,
                    color = GlucoachColors.Border,
                    shape = RoundedCornerShape(GlucoachCorner.card),
                )
                .clickable(onClick = onClick)
                .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.size(28.dp)) {
            Icon(
                imageVector = Icons.Outlined.MailOutline,
                contentDescription = null,
                tint = GlucoachColors.PrimaryDark,
                modifier =
                    Modifier
                        .size(24.dp)
                        .align(Alignment.Center),
            )
            if (unread.isNotEmpty()) {
                Box(
                    modifier =
                        Modifier
                            .align(Alignment.TopEnd)
                            .size(16.dp)
                            .background(Color(0xFFE53935), shape = CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "${unread.size}",
                        color = Color.White,
                        fontSize = 10.sp,
                        lineHeight = 10.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        style =
                            LocalTextStyle.current.merge(
                                TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false)),
                            ),
                    )
                }
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = bannerText,
            color = GlucoachColors.TextPrimary,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

private fun resolveBannerText(
    unread: List<com.ssafy.s309.data.model.NotificationItem>,
    isNewUser: Boolean,
): String =
    when {
        isNewUser -> "반가워요. 키키와 함께해요"
        unread.isEmpty() -> "키키가 오늘 컨디션을 보고 있어요"
        unread.size >= 2 -> "키키가 기다리고 있어요"
        else -> {
            // 백엔드가 AGENT_* 타입에 timestamp suffix를 붙이는 경우가 있어 prefix 매칭 사용
            val type = unread.first().alertType
            when {
                type == "HIGH" -> "혈당이 높아요"
                type == "LOW" -> "저혈당 주의가 필요해요"
                type == "SOS" -> "SOS 긴급 요청이 발생했어요"
                type.startsWith("AGENT_WAKE_UP") -> "키키가 오늘의 혈당 전략을 알려줬어요!"
                type.startsWith("AGENT_MEAL_FOLLOWUP") -> "키키가 식후 활동을 제안했어요!"
                type.startsWith("AGENT_MEAL_REPLY") -> "키키가 답변을 보냈어요!"
                type.startsWith("AGENT_MEAL_RETRY") -> "키키가 다시 확인하고 있어요!"
                type.startsWith("AGENT_CALENDAR_REMINDER") -> "키키가 오늘 일정을 확인했어요!"
                type == "WEEKLY_REPORT" -> "이번 주 건강 리포트가 도착했어요!"
                else -> "키키가 오늘 컨디션을 보고 있어요"
            }
        }
    }

@Composable
private fun SummaryRow(
    steps: Int,
    sleepMinutes: Int,
) {
    val sleepHours = sleepMinutes / 60
    val sleepMins = sleepMinutes % 60
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(GlucoachSpacing.lg),
    ) {
        SummaryStatCard(
            title = "걸음 수",
            periodLabel = "오늘",
            modifier =
                Modifier
                    .weight(1f)
                    .fillMaxHeight(),
        ) {
            val stepsFontSize =
                when {
                    steps >= 100_000 -> 22.sp
                    steps >= 10_000 -> 26.sp
                    else -> 32.sp
                }
            Text(
                text = "%,d".format(steps),
                color = GlucoachColors.PrimaryDark,
                fontSize = stepsFontSize,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                modifier = Modifier.weight(1f, fill = false),
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "걸음",
                color = GlucoachColors.PrimaryDark,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                modifier = Modifier.padding(bottom = 2.dp),
            )
        }
        SummaryStatCard(
            title = "수면",
            periodLabel = "오늘",
            modifier =
                Modifier
                    .weight(1f)
                    .fillMaxHeight(),
        ) {
            Text(
                text = "$sleepHours",
                color = GlucoachColors.PrimaryDark,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.width(3.dp))
            Text(
                text = "시간",
                color = GlucoachColors.PrimaryDark,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 4.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "$sleepMins",
                color = GlucoachColors.PrimaryDark,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.width(3.dp))
            Text(
                text = "분",
                color = GlucoachColors.PrimaryDark,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }
    }
}

internal fun defaultBottomNavItems(hasUnreadKiki: Boolean = false): List<BottomNavItem> =
    listOf(
        BottomNavItem(id = "home", label = "홈", icon = Icons.Outlined.Home),
        BottomNavItem(id = "report", label = "리포트", icon = Icons.Outlined.Description),
        BottomNavItem(id = "kiki", label = "키키", isCenter = true, hasUnread = hasUnreadKiki),
        BottomNavItem(id = "meallog", label = "기록", icon = Icons.Outlined.EditNote),
        BottomNavItem(id = "profile", label = "마이페이지", icon = Icons.Outlined.Person),
    )

@Composable
internal fun ReportMenuSheet(
    onAIReport: () -> Unit,
    onFoodReport: () -> Unit,
    onFoodComparison: () -> Unit,
    onClose: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .shadow(8.dp, RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                .background(GlucoachColors.Surface)
                .padding(horizontal = 22.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
        ) {
            IconButton(
                onClick = onClose,
                modifier =
                    Modifier
                        .align(Alignment.CenterEnd)
                        .size(32.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "닫기",
                    tint = GlucoachColors.TextSecondary,
                    modifier = Modifier.size(20.dp),
                )
            }
        }

        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clickable { onAIReport() }
                    .padding(vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Outlined.DateRange,
                contentDescription = null,
                tint = GlucoachColors.TextPrimary,
                modifier = Modifier.size(24.dp),
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "AI 주간 리포트",
                color = GlucoachColors.TextPrimary,
                fontSize = 16.sp,
            )
        }

        HorizontalDivider(color = GlucoachColors.Border)

        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clickable { onFoodReport() }
                    .padding(vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Outlined.Search,
                contentDescription = null,
                tint = GlucoachColors.TextPrimary,
                modifier = Modifier.size(24.dp),
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "나의 음식 성적표",
                color = GlucoachColors.TextPrimary,
                fontSize = 16.sp,
            )
        }

        HorizontalDivider(color = GlucoachColors.Border)

        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clickable { onFoodComparison() }
                    .padding(vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Outlined.Description,
                contentDescription = null,
                tint = GlucoachColors.TextPrimary,
                modifier = Modifier.size(24.dp),
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "A/B 비교",
                color = GlucoachColors.TextPrimary,
                fontSize = 16.sp,
            )
        }

        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
internal fun AddMenuSheet(
    onFoodScan: () -> Unit,
    onClose: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .shadow(8.dp, RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                .background(GlucoachColors.Surface)
                .padding(horizontal = 22.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
        ) {
            IconButton(
                onClick = onClose,
                modifier =
                    Modifier
                        .align(Alignment.CenterEnd)
                        .size(32.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "닫기",
                    tint = GlucoachColors.TextSecondary,
                    modifier = Modifier.size(20.dp),
                )
            }
        }

        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clickable { onFoodScan() }
                    .padding(vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Outlined.Search,
                contentDescription = null,
                tint = GlucoachColors.TextPrimary,
                modifier = Modifier.size(24.dp),
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "음식 인식",
                color = GlucoachColors.TextPrimary,
                fontSize = 16.sp,
            )
        }

        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
private fun InstagramCameraPanel(
    isAbMode: Boolean,
    onModeChange: (Boolean) -> Unit,
    onClose: () -> Unit,
    onMealSaved: () -> Unit,
    onNavigateHome: () -> Unit,
    initialFoodA: String? = null,
    initialFoodB: String? = null,
) {
    var capturedFile by remember { mutableStateOf<File?>(null) }
    var sessionId by remember { mutableIntStateOf(0) }

    androidx.compose.runtime.LaunchedEffect(isAbMode) {
        if (!isAbMode) capturedFile = null
    }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(GlucoachColors.Background)
                .clickable(
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                ),
    ) {
        when {
            isAbMode -> {
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .background(GlucoachColors.Background)
                            .statusBarsPadding()
                            .padding(bottom = 72.dp),
                ) {
                    FoodComparisonContent(
                        onMealSaved = onMealSaved,
                        onNavigateHome = onNavigateHome,
                        initialFoodAName = initialFoodA,
                        initialFoodBName = initialFoodB,
                    )
                }
                Box(
                    modifier =
                        Modifier
                            .align(Alignment.TopStart)
                            .statusBarsPadding()
                            .padding(start = 16.dp, top = 8.dp)
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(GlucoachColors.Border)
                            .clickable(onClick = onClose),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "닫기",
                        tint = GlucoachColors.TextSecondary,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            capturedFile != null -> {
                key(sessionId) {
                    FoodScanContent(
                        photoFile = capturedFile!!,
                        onBack = { capturedFile = null },
                        onRetakePhoto = {
                            sessionId++
                            capturedFile = null
                        },
                        onMealSaved = {
                            sessionId++
                            capturedFile = null
                            onMealSaved()
                        },
                        onGoHome = onClose,
                    )
                }
            }
            else -> {
                CameraScreen(
                    onClose = onClose,
                    onPhotoTaken = { file -> capturedFile = file },
                )
            }
        }

        if (capturedFile == null) {
            CameraModeToggle(
                isAbMode = isAbMode,
                onModeChange = onModeChange,
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = if (isAbMode) 16.dp else 180.dp),
            )
        }
    }
}

@Composable
private fun CameraModeToggle(
    isAbMode: Boolean,
    onModeChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .clip(RoundedCornerShape(50))
                .background(Color.Black.copy(alpha = 0.55f))
                .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        CameraModeChip(text = "음식 인식", selected = !isAbMode, onClick = { onModeChange(false) })
        CameraModeChip(text = "A/B 비교", selected = isAbMode, onClick = { onModeChange(true) })
    }
}

@Composable
private fun CameraModeChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .clip(RoundedCornerShape(50))
                .background(if (selected) Color.White else Color.Transparent)
                .clickable(onClick = onClick)
                .padding(horizontal = 18.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = if (selected) Color.Black else Color.White,
            fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

private fun kikiCycleDuration(drawableRes: Int): Long =
    when (drawableRes) {
        R.drawable.kiki_run -> 5_760L
        R.drawable.kiki_hello -> 5_760L
        R.drawable.kiki_fell_off -> 15_500L
        R.drawable.kiki_dehydrated_high -> 3_300L
        else -> 3_000L
    }
