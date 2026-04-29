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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ssafy.s309.ui.component.BottomNavBar
import com.ssafy.s309.ui.component.BottomNavItem
import com.ssafy.s309.ui.component.CurrentGlucoseCard
import com.ssafy.s309.ui.component.GlucoseChartCard
import com.ssafy.s309.ui.component.SummaryStatCard
import com.ssafy.s309.ui.theme.GlucoachColors
import com.ssafy.s309.ui.theme.GlucoachSpacing

@Composable
fun MainScreen(
    viewModel: MainViewModel = hiltViewModel(),
    mascotSlot: (@Composable () -> Unit)? = null,
    bellIcon: (@Composable () -> Unit)? = null,
    mealPinIcon: (@Composable () -> Unit)? = null,
    onGraphClick: () -> Unit = {},
    onConnectedDeviceClick: () -> Unit = {},
    onLogoutClick: () -> Unit = {},
    userEmail: String = "",
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    MainScreenContent(
        state = uiState,
        onBellClick = viewModel::openNotificationPanel,
        onNotificationBack = viewModel::closeNotificationPanel,
        onClearAllNotifications = viewModel::clearAllNotifications,
        mascotSlot = mascotSlot,
        bellIcon = bellIcon,
        mealPinIcon = mealPinIcon,
        onGraphClick = onGraphClick,
        onConnectedDeviceClick = onConnectedDeviceClick,
        onLogoutClick = onLogoutClick,
        userEmail = userEmail,
    )
}

@Composable
fun MainScreenContent(
    state: MainUiState,
    onBellClick: () -> Unit,
    onNotificationBack: () -> Unit,
    onClearAllNotifications: () -> Unit,
    mascotSlot: (@Composable () -> Unit)? = null,
    bellIcon: (@Composable () -> Unit)? = null,
    mealPinIcon: (@Composable () -> Unit)? = null,
    onGraphClick: () -> Unit = {},
    onConnectedDeviceClick: () -> Unit = {},
    onLogoutClick: () -> Unit = {},
    userEmail: String = "",
) {
    var selectedTab by remember { mutableStateOf("home") }
    var showFoodScan by remember { mutableStateOf(false) }
    var showReportSheet by remember { mutableStateOf(false) }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(GlucoachColors.Background),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.weight(1f)) {
                Crossfade(
                    targetState = selectedTab,
                    animationSpec = tween(300),
                    modifier = Modifier.fillMaxSize(),
                    label = "tab-crossfade",
                ) { tab ->
                    when (tab) {
                        "profile" ->
                            MyPageContent(
                                onLogoutClick = onLogoutClick,
                                userEmail = userEmail,
                                onDeviceClick = onConnectedDeviceClick,
                            )
                        "edit" -> FoodComparisonContent()
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
                                    bellIcon = bellIcon,
                                )
                                Spacer(modifier = Modifier.height(GlucoachSpacing.xxl))

                                CurrentGlucoseCard(
                                    currentMgDl = state.currentGlucoseMgDl ?: 0,
                                    diffFromPrevious = state.diffFromPrevious,
                                    mascotSlot = mascotSlot,
                                )
                                Spacer(modifier = Modifier.height(GlucoachSpacing.xl))

                                GlucoseChartCard(
                                    readings = state.glucoseSeries,
                                    range = state.glucoseRange,
                                    meals = state.meals,
                                    hoursLabel = "최근 6시간",
                                    mealPinIcon = mealPinIcon,
                                    timeLabels = listOf("08:00", "10:00", "12:00", "14:00"),
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

            BottomNavBar(
                items = defaultBottomNavItems(),
                selectedId = selectedTab,
                onItemClick = { item ->
                    when (item.id) {
                        "add" -> showFoodScan = true
                        "report" -> showReportSheet = !showReportSheet
                        else -> {
                            showReportSheet = false
                            selectedTab = item.id
                        }
                    }
                },
            )
        }

        if (showFoodScan) {
            FoodScanFlow(onClose = { showFoodScan = false })
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
            )
        }
    }
}

@Composable
private fun TodayConditionHeader(
    onBellClick: () -> Unit,
    bellIcon: (@Composable () -> Unit)?,
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
            modifier =
                Modifier
                    .size(32.dp)
                    .clickable(onClick = onBellClick),
            contentAlignment = Alignment.Center,
        ) {
            bellIcon?.invoke()
                ?: Box(
                    modifier =
                        Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .border(1.dp, GlucoachColors.PrimaryDark, CircleShape),
                )
        }
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

private fun defaultBottomNavItems(): List<BottomNavItem> =
    listOf(
        BottomNavItem(id = "home", label = "홈", icon = Icons.Outlined.Home),
        BottomNavItem(id = "report", label = "리포트", icon = Icons.Outlined.Description),
        BottomNavItem(id = "add", label = "추가", icon = Icons.Outlined.Add, isCenter = true),
        BottomNavItem(id = "edit", label = "기록", icon = Icons.Outlined.EditNote),
        BottomNavItem(id = "profile", label = "마이페이지", icon = Icons.Outlined.Person),
    )

@Composable
private fun ReportMenuSheet(
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
