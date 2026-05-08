package com.ssafy.s309.ui.screen.health

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.health.connect.client.PermissionController
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ssafy.s309.ui.theme.GlucoachColors
import com.ssafy.s309.ui.theme.GlucoachCorner
import com.ssafy.s309.ui.theme.GlucoachSpacing

/**
 * 건강 데이터 소스 / 키보드 / 오버레이 권한을 한 곳에서 관리하는 화면.
 *
 * MyPage 의 "연결 기기" 진입점에서 도달한다. SDK 미지원 / 권한 미부여 디바이스에서도
 * 화면이 안전하게 표시되며, 어떤 상태에서도 메인 화면의 동작에 영향을 주지 않는다.
 */
@Composable
fun HealthSourceScreen(
    onBack: () -> Unit,
    viewModel: HealthSourceViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val hcPermissionLauncher =
        rememberLauncherForActivityResult(
            contract = PermissionController.createRequestPermissionResultContract(),
        ) { _ -> viewModel.onHealthConnectPermissionResult() }

    // 시스템 설정 다녀온 후 ON_RESUME 시점에 상태 갱신 (오버레이/IME 등).
    // 권한 자동 요청은 앱 실행 시 MainActivity 에서만 1회 트리거되며, 이 화면에서는
    // 사용자가 직접 "권한 요청" 버튼을 탭했을 때만 요청한다.
    DisposableEffect(lifecycleOwner) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) viewModel.refresh()
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(GlucoachColors.Background)
                .verticalScroll(rememberScrollState()),
    ) {
        TopBar(onBack = onBack)
        Spacer(modifier = Modifier.height(GlucoachSpacing.lg))

        SectionCard(
            title = "Samsung Health",
            statusLabel = samsungStatusLabel(uiState),
            statusOk = uiState.samsungReady,
            description =
                if (uiState.samsungSdkSupported) {
                    "Samsung Health 앱에서 운동 / 수면 / 혈당 데이터를 가져옵니다. " +
                        "API 29+ 와 Samsung Health 앱 설치가 필요합니다."
                } else {
                    "이 기기에서는 Samsung Health SDK 를 사용할 수 없습니다."
                },
            primaryButtonLabel = if (uiState.samsungReady) "권한 다시 확인" else "권한 요청",
            primaryButtonEnabled = uiState.samsungSdkSupported,
            onPrimaryClick = { viewModel.requestSamsungPermissions() },
        )

        Spacer(modifier = Modifier.height(GlucoachSpacing.lg))

        SectionCard(
            title = "Health Connect",
            statusLabel = healthConnectStatusLabel(uiState),
            statusOk = uiState.healthConnectReady,
            description =
                "Health Connect 앱에서 혈당 / 영양 데이터를 읽어옵니다. " +
                    "Health Connect 미설치 / 권한 미부여 시에도 메인 화면은 동일하게 동작합니다.",
            primaryButtonLabel = if (uiState.healthConnectReady) "권한 다시 확인" else "권한 요청",
            primaryButtonEnabled = uiState.healthConnectAvailable,
            onPrimaryClick = { hcPermissionLauncher.launch(viewModel.healthConnectPermissions) },
        )

        Spacer(modifier = Modifier.height(GlucoachSpacing.lg))

        SectionCard(
            title = "GlucoFit 키보드",
            statusLabel = if (uiState.imeEnabled) "활성화됨" else "비활성",
            statusOk = uiState.imeEnabled,
            description =
                "음식 이름을 입력하면 혈당에 따라 추천 메시지를 띄워주는 키보드입니다. " +
                    "시스템 설정 → 언어와 입력 → 화면 키보드에서 활성화하세요.",
            primaryButtonLabel = "입력기 설정 열기",
            primaryButtonEnabled = true,
            onPrimaryClick = {
                runCatching {
                    context.startActivity(
                        Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                }
            },
        )

        Spacer(modifier = Modifier.height(GlucoachSpacing.lg))

        SectionCard(
            title = "오버레이 권한",
            statusLabel = if (uiState.overlayGranted) "허용됨" else "미허용",
            statusOk = uiState.overlayGranted,
            description =
                "GlucoFit 키보드가 입력 시 다른 앱 위에 추천 배너를 띄우려면 오버레이 권한이 필요합니다. " +
                    "권한 미허용 시 키보드 자체는 정상 동작하며 배너만 표시되지 않습니다.",
            primaryButtonLabel = "오버레이 설정 열기",
            primaryButtonEnabled = true,
            onPrimaryClick = {
                runCatching {
                    val uri = Uri.fromParts("package", context.packageName, null)
                    context.startActivity(
                        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, uri)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                }
            },
        )

        Spacer(modifier = Modifier.height(GlucoachSpacing.xxl))
    }
}

@Composable
private fun TopBar(onBack: () -> Unit) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = GlucoachSpacing.lg, vertical = GlucoachSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .size(36.dp)
                    .clickable(onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                contentDescription = "뒤로",
                tint = GlucoachColors.TextPrimary,
                modifier = Modifier.size(24.dp),
            )
        }
        Spacer(modifier = Modifier.size(GlucoachSpacing.sm))
        Text(
            text = "연결 기기",
            color = GlucoachColors.TextPrimary,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun SectionCard(
    title: String,
    statusLabel: String,
    statusOk: Boolean,
    description: String,
    primaryButtonLabel: String,
    primaryButtonEnabled: Boolean,
    onPrimaryClick: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = GlucoachSpacing.lg)
                .clip(RoundedCornerShape(GlucoachCorner.card))
                .background(GlucoachColors.Surface)
                .padding(GlucoachSpacing.lg),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                color = GlucoachColors.TextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            StatusPill(label = statusLabel, ok = statusOk)
        }
        Spacer(modifier = Modifier.height(GlucoachSpacing.sm))
        Text(
            text = description,
            color = GlucoachColors.TextSecondary,
            fontSize = 13.sp,
        )
        Spacer(modifier = Modifier.height(GlucoachSpacing.md))
        Button(
            onClick = onPrimaryClick,
            enabled = primaryButtonEnabled,
            colors =
                ButtonDefaults.buttonColors(
                    containerColor = GlucoachColors.Primary,
                    contentColor = Color.White,
                    disabledContainerColor = GlucoachColors.Border,
                    disabledContentColor = GlucoachColors.TextSecondary,
                ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(text = primaryButtonLabel, fontSize = 14.sp)
        }
    }
}

@Composable
private fun StatusPill(
    label: String,
    ok: Boolean,
) {
    val (bg, fg) =
        if (ok) {
            GlucoachColors.StableBadgeBg to GlucoachColors.StableBadgeText
        } else {
            GlucoachColors.SpikeBadgeBg to GlucoachColors.SpikeBadgeText
        }
    Box(
        modifier =
            Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(bg)
                .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(text = label, color = fg, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

private fun samsungStatusLabel(state: HealthSourceUiState): String =
    when {
        !state.samsungSdkSupported -> "지원 안 함"
        state.samsungReady -> "연결됨"
        else -> "권한 필요"
    }

private fun healthConnectStatusLabel(state: HealthSourceUiState): String =
    when {
        !state.healthConnectAvailable -> "미설치"
        state.healthConnectReady -> "연결됨"
        else -> "권한 필요"
    }
