package com.ssafy.s309.ui.screen.projector

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ssafy.s309.projector.ProjectorViewModel
import com.ssafy.s309.ui.theme.Background
import com.ssafy.s309.ui.theme.BorderLight
import com.ssafy.s309.ui.theme.GlucoachColors
import com.ssafy.s309.ui.theme.GlucoachSpacing
import com.ssafy.s309.ui.theme.Primary

@Composable
fun ProjectorScreen(
    viewModel: ProjectorViewModel = hiltViewModel(),
    onBack: () -> Unit = {},
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var ipInput by remember(state.piIp) { mutableStateOf(state.piIp) }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(Background)
                .statusBarsPadding(),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = GlucoachSpacing.md, vertical = GlucoachSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = "뒤로가기",
                    tint = GlucoachColors.TextPrimary,
                )
            }
            Text(
                text = "프로젝터 제어",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = GlucoachColors.TextPrimary,
            )
        }

        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 22.dp),
            verticalArrangement = Arrangement.spacedBy(GlucoachSpacing.lg),
        ) {
            ProjectorSection(title = "연결 설정") {
                Text(text = "Raspberry Pi IP", fontSize = 13.sp, color = GlucoachColors.TextSecondary)
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = ipInput,
                    onValueChange = { ipInput = it },
                    placeholder = { Text("192.168.0.100", color = GlucoachColors.TextSecondary) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(8.dp),
                    keyboardOptions =
                        KeyboardOptions(
                            keyboardType = KeyboardType.Uri,
                            imeAction = ImeAction.Done,
                        ),
                    keyboardActions =
                        KeyboardActions(onDone = {
                            viewModel.updateIp(ipInput)
                            viewModel.connect()
                        }),
                    colors =
                        OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Primary,
                            unfocusedBorderColor = BorderLight,
                        ),
                )
                Spacer(modifier = Modifier.height(GlucoachSpacing.sm))
                Button(
                    onClick = {
                        viewModel.updateIp(ipInput)
                        viewModel.connect()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Primary),
                ) {
                    Text("Pi 연결")
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "상태: ${state.statusMessage}",
                    fontSize = 13.sp,
                    color = if (state.isConnected) Color(0xFF4CAF50) else GlucoachColors.TextSecondary,
                )
            }

            HorizontalDivider(color = BorderLight)

            ProjectorSection(title = "직접 제어") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(GlucoachSpacing.md),
                ) {
                    Button(
                        onClick = viewModel::show,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Primary),
                    ) {
                        Text("비서 ON")
                    }
                    OutlinedButton(
                        onClick = viewModel::hide,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                    ) {
                        Text("비서 OFF")
                    }
                }
            }

            HorizontalDivider(color = BorderLight)

            ProjectorSection(title = "발표 시뮬레이션") {
                Text(
                    text = "실제 혈당/수면 데이터 없이 트리거를 테스트합니다",
                    fontSize = 12.sp,
                    color = GlucoachColors.TextSecondary,
                )
                Spacer(modifier = Modifier.height(GlucoachSpacing.sm))
                Button(
                    onClick = viewModel::simulateMorning,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF5C6BC0)),
                ) {
                    Text("수면 종료 브리핑 (수면점수 78, 혈당 105)")
                }
                Spacer(modifier = Modifier.height(GlucoachSpacing.sm))
                Button(
                    onClick = viewModel::simulateHighGlucose,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE53935)),
                ) {
                    Text("고혈당 경고 (혈당 185 mg/dL)")
                }
            }

            Spacer(modifier = Modifier.height(GlucoachSpacing.xxl))
        }
    }
}

@Composable
private fun ProjectorSection(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Color.White)
                .padding(horizontal = 20.dp, vertical = GlucoachSpacing.lg),
    ) {
        Text(
            text = title,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = GlucoachColors.TextPrimary,
        )
        Spacer(modifier = Modifier.height(GlucoachSpacing.md))
        content()
    }
}
