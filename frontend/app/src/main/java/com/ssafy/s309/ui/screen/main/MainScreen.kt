package com.ssafy.s309.ui.screen.main

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ssafy.s309.ui.component.BottomNavBar
import com.ssafy.s309.ui.component.BottomNavItem
import com.ssafy.s309.ui.component.CurrentGlucoseCard
import com.ssafy.s309.ui.component.GlucoseChartCard
import com.ssafy.s309.ui.component.GlucoseChartTimeAxis
import com.ssafy.s309.ui.component.SummaryStatCard
import com.ssafy.s309.ui.theme.GlucoachColors

/**
 * 메인/일반 화면.
 *
 * - 상단: "오늘의 컨디션" 타이틀 + 벨(알림) 아이콘
 * - 현재 혈당 카드 (키키 마스코트 자리는 asset 주입 슬롯)
 * - 오늘 혈당 흐름 그래프
 * - 칼로리 소모 / 수면 카드
 * - 하단바
 * - 벨 클릭 시 [NotificationPanel] 이 우측에서 슬라이드 인
 *
 * @param mascotSlot 키키 캐릭터 이미지 Composable (asset 추가 후 Image 주입)
 * @param bellIcon 벨 아이콘 Composable (asset 추가 후 Icon 주입)
 * @param mealPinIcon 밥그릇 핀 Composable (asset 추가 후 Image 주입)
 */
@Composable
fun MainScreen(
    viewModel: MainViewModel = hiltViewModel(),
    mascotSlot: (@Composable () -> Unit)? = null,
    bellIcon: (@Composable () -> Unit)? = null,
    mealPinIcon: (@Composable () -> Unit)? = null,
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
    )
}

/**
 * UI 전용 컨텐츠. ViewModel 의존성을 제거하여 Preview/테스트에서 재사용 가능.
 */
@Composable
fun MainScreenContent(
    state: MainUiState,
    onBellClick: () -> Unit,
    onNotificationBack: () -> Unit,
    onClearAllNotifications: () -> Unit,
    mascotSlot: (@Composable () -> Unit)? = null,
    bellIcon: (@Composable () -> Unit)? = null,
    mealPinIcon: (@Composable () -> Unit)? = null,
) {
    var selectedTab by remember { mutableStateOf("home") }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(GlucoachColors.Background),
    ) {
        // === 메인 컨텐츠 ===
        Column(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier =
                    Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 22.dp),
            ) {
                Spacer(modifier = Modifier.height(24.dp))
                TodayConditionHeader(
                    onBellClick = onBellClick,
                    bellIcon = bellIcon,
                )
                Spacer(modifier = Modifier.height(24.dp))

                CurrentGlucoseCard(
                    currentMgDl = state.currentGlucoseMgDl ?: 0,
                    diffFromPrevious = state.diffFromPrevious,
                    mascotSlot = mascotSlot,
                )
                Spacer(modifier = Modifier.height(20.dp))

                GlucoseChartCard(
                    readings = state.glucoseSeries,
                    range = state.glucoseRange,
                    meals = state.meals,
                    hoursLabel = "최근 6시간",
                    mealPinIcon = mealPinIcon,
                )
                GlucoseChartTimeAxis(
                    labels = listOf("08:00", "10:00", "12:00", "14:00"),
                )
                Spacer(modifier = Modifier.height(20.dp))

                SummaryRow(
                    caloriesKcal = state.summary.caloriesBurnedKcal,
                    sleepMinutes = state.summary.sleepMinutes,
                )
                Spacer(modifier = Modifier.height(24.dp))
            }

            BottomNavBar(
                items = defaultBottomNavItems(),
                selectedId = selectedTab,
                onItemClick = { selectedTab = it.id },
            )
        }

        // === 알림 슬라이드 패널 (오른쪽 오버레이) ===
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
                    // asset 미주입 상태에서 클릭 가능한 원형 자리 표시자
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
        horizontalArrangement = Arrangement.spacedBy(16.dp),
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

/**
 * 기본 하단 탭 구성. 추후 navigation-compose 연결 시 id 를 route 와 매핑한다.
 * 아이콘은 asset 추가 전까지 null 로 비워둔다.
 */
private fun defaultBottomNavItems(): List<BottomNavItem> =
    listOf(
        BottomNavItem(id = "home", label = "홈"),
        BottomNavItem(id = "report", label = "리포트"),
        BottomNavItem(id = "add", label = "추가", isCenter = true),
        BottomNavItem(id = "edit", label = "기록"),
        BottomNavItem(id = "profile", label = "마이페이지"),
    )
