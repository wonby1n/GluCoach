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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
    onLogoutClick: () -> Unit = {},
    onWithdrawClick: (String) -> Unit = {},
    onSettingsClick: () -> Unit = {},
    onGuardianClick: () -> Unit = {},
    onProjectorClick: () -> Unit = {},
    onAccountClick: () -> Unit = {},
    onKikiChatClick: () -> Unit = {},
    onKikiAlarmClick: () -> Unit = {},
    userEmail: String = "",
    requestedTab: String? = null,
    onTabHandled: () -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    MainScreenContent(
        state = uiState,
        onBellClick = viewModel::openNotificationPanel,
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
        onLogoutClick = onLogoutClick,
        onWithdrawClick = onWithdrawClick,
        onSettingsClick = onSettingsClick,
        onGuardianClick = onGuardianClick,
        onProjectorClick = onProjectorClick,
        onAccountClick = onAccountClick,
        onKikiChatClick = onKikiChatClick,
        onKikiAlarmClick = onKikiAlarmClick,
        userEmail = userEmail,
        requestedTab = requestedTab,
        onTabHandled = onTabHandled,
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
    onLogoutClick: () -> Unit = {},
    onWithdrawClick: (String) -> Unit = {},
    onSettingsClick: () -> Unit = {},
    onGuardianClick: () -> Unit = {},
    onProjectorClick: () -> Unit = {},
    onAccountClick: () -> Unit = {},
    onKikiChatClick: () -> Unit = {},
    onKikiAlarmClick: () -> Unit = {},
    userEmail: String = "",
    requestedTab: String? = null,
    onTabHandled: () -> Unit = {},
) {
    var selectedTab by rememberSaveable { mutableStateOf("home") }
    var showReportSheet by remember { mutableStateOf(false) }
    var showCameraPanel by remember { mutableStateOf(false) }
    var isAbMode by remember { mutableStateOf(false) }
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
                    showCameraPanel = true
                    isAbMode = true
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
                        .pointerInput(showCameraPanel) {
                            if (!showCameraPanel) {
                                detectHorizontalDragGestures(
                                    onDragStart = { cumulativeDrag = 0f },
                                    onDragEnd = {
                                        if (cumulativeDrag > 80.dp.toPx()) {
                                            showCameraPanel = true
                                            isAbMode = false
                                        }
                                        cumulativeDrag = 0f
                                    },
                                    onDragCancel = { cumulativeDrag = 0f },
                                    onHorizontalDrag = { _, dragAmount ->
                                        if (dragAmount > 0) cumulativeDrag += dragAmount
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
                                onHealthDetailClick = onSettingsClick,
                                onGuardianClick = onGuardianClick,
                                onProjectorClick = onProjectorClick,
                                userEmail = userEmail,
                            )
                        "meallog" ->
                            MealLogContent(
                                onBackToHome = { selectedTab = "home" },
                                onNavigateToFoodReport = { selectedTab = "food-report" },
                            )
                        "report" -> AIReportContent()
                        "food-report" -> FoodReportContent()

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
                                CurrentGlucoseCard(
                                    currentMgDl = state.currentGlucoseMgDl ?: 0,
                                    diffFromPrevious = state.diffFromPrevious,
                                    mascotSlot = {
                                        KikiImage(
                                            drawableRes = kikiDrawable,
                                            modifier = Modifier.fillMaxSize(),
                                        )
                                    },
                                )
                                Spacer(modifier = Modifier.height(GlucoachSpacing.xl))

                                KikiSuggestionCard(
                                    notifications = state.notifications,
                                    onAlarmClick = onKikiAlarmClick,
                                    onChatClick = onKikiChatClick,
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
                                    caloriesKcal = state.summary.caloriesBurnedKcal,
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
                        onClose = { showReportSheet = false },
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
                            onKikiChatClick()
                        }
                        "report" -> {
                            showReportSheet = !showReportSheet
                        }
                        else -> {
                            showReportSheet = false
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
                onClose = { showCameraPanel = false },
                onMealSaved = {
                    showCameraPanel = false
                    selectedTab = if (isAbMode) "meallog" else "home"
                },
            )
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
    onBellClick: () -> Unit,
    hasUnread: Boolean,
    bellIcon: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = "오늘의 컨디션",
            color = GlucoachColors.TextPrimary,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
        )
        Box(
            modifier = Modifier.size(32.dp).clickable(onClick = onBellClick),
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
    onAlarmClick: () -> Unit,
    onChatClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val unread = notifications.filter { it.isUnread }
    val bannerText = resolveBannerText(unread)
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

private fun resolveBannerText(unread: List<com.ssafy.s309.data.model.NotificationItem>): String =
    when {
        unread.isEmpty() -> "키키가 오늘 컨디션을 보고 있어요"
        unread.size >= 2 -> "키키가 기다리고 있어요"
        else ->
            when (unread.first().alertType) {
                "HIGH" -> "혈당이 높아요"
                "LOW" -> "저혈당 주의가 필요해요"
                "SOS" -> "SOS 긴급 요청이 발생했어요"
                "AGENT_WAKE_UP" -> "키키가 오늘의 혈당 전략을 알려줬어요!"
                "AGENT_MEAL_FOLLOWUP" -> "키키가 식후 활동을 제안했어요!"
                "AGENT_MEAL_REPLY" -> "키키가 답변을 보냈어요!"
                "AGENT_MEAL_RETRY" -> "키키가 다시 확인하고 있어요!"
                "WEEKLY_REPORT" -> "이번 주 건강 리포트가 도착했어요!"
                else -> "키키가 오늘 컨디션을 보고 있어요"
            }
    }

@Composable
private fun SummaryRow(
    caloriesKcal: Int,
    sleepMinutes: Int,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(GlucoachSpacing.lg),
    ) {
        SummaryStatCard(
            title = "칼로리 소모",
            periodLabel = "오늘",
            primaryValue = "$caloriesKcal",
            unitOrSuffix = "kcal",
            modifier = Modifier.weight(1f),
        )
        SummaryStatCard(
            title = "수면",
            periodLabel = "오늘",
            primaryValue = "${sleepMinutes / 60}:",
            unitOrSuffix = String.format("%02d", sleepMinutes % 60),
            modifier = Modifier.weight(1f),
        )
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

        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
private fun InstagramCameraPanel(
    isAbMode: Boolean,
    onModeChange: (Boolean) -> Unit,
    onClose: () -> Unit,
    onMealSaved: () -> Unit,
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
                    FoodComparisonContent(onMealSaved = onMealSaved)
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
                        onMealSaved = onMealSaved,
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
