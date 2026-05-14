package com.ssafy.s309.ui.screen.main

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ssafy.s309.data.model.UserSettingsUpdateRequest
import com.ssafy.s309.ui.theme.Background
import com.ssafy.s309.ui.theme.BorderLight
import com.ssafy.s309.ui.theme.GlucoachColors
import com.ssafy.s309.ui.theme.GlucoachSpacing
import com.ssafy.s309.ui.theme.Primary
import com.ssafy.s309.ui.theme.TextLabel
import com.ssafy.s309.ui.theme.TextSecondary

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
    onBack: () -> Unit = {},
    onProjectorClick: () -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(uiState.saveSuccess) {
        if (uiState.saveSuccess) {
            viewModel.clearSaveSuccess()
            onBack()
        }
    }

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
                text = "건강 설정",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = GlucoachColors.TextPrimary,
            )
        }

        when {
            uiState.isLoading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Primary)
                }
            }
            uiState.settings != null -> {
                SettingsForm(
                    settings = uiState.settings!!,
                    isSaving = uiState.isSaving,
                    error = uiState.error,
                    onSave = viewModel::saveSettings,
                    onProjectorClick = onProjectorClick,
                )
            }
            else -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = uiState.error ?: "데이터를 불러올 수 없어요",
                            color = GlucoachColors.TextSecondary,
                            fontSize = 14.sp,
                        )
                        Spacer(modifier = Modifier.height(GlucoachSpacing.lg))
                        Button(
                            onClick = viewModel::loadSettings,
                            colors = ButtonDefaults.buttonColors(containerColor = Primary),
                        ) {
                            Text("다시 시도")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsForm(
    settings: com.ssafy.s309.data.model.UserSettings,
    isSaving: Boolean,
    error: String?,
    onSave: (UserSettingsUpdateRequest) -> Unit,
    onProjectorClick: () -> Unit = {},
) {
    var height by remember(settings) { mutableStateOf(settings.height?.toString() ?: "") }
    var weight by remember(settings) { mutableStateOf(settings.weight?.toString() ?: "") }
    var diabetesType by remember(settings) { mutableStateOf(settings.diabetesType ?: "") }
    var isMedicated by remember(settings) { mutableStateOf(settings.isMedicated ?: false) }
    var targetLow by remember(settings) { mutableStateOf(settings.targetLow?.toInt()?.toString() ?: "") }
    var targetHigh by remember(settings) { mutableStateOf(settings.targetHigh?.toInt()?.toString() ?: "") }
    var alertLow by remember(settings) { mutableStateOf(settings.alertLow?.toString() ?: "") }
    var alertHigh by remember(settings) { mutableStateOf(settings.alertHigh?.toString() ?: "") }
    var nightWatch by remember(settings) { mutableStateOf(settings.nightWatch ?: false) }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp, vertical = GlucoachSpacing.md),
    ) {
        SettingsSection(title = "신체 정보") {
            SettingsTextField(
                label = "키 (cm)",
                value = height,
                onValueChange = { height = it },
                keyboardType = KeyboardType.Decimal,
                placeholder = "예: 170.0",
            )
            Spacer(modifier = Modifier.height(GlucoachSpacing.md))
            SettingsTextField(
                label = "체중 (kg)",
                value = weight,
                onValueChange = { weight = it },
                keyboardType = KeyboardType.Decimal,
                placeholder = "예: 65.0",
            )
        }

        Spacer(modifier = Modifier.height(GlucoachSpacing.lg))

        SettingsSection(title = "당뇨 정보") {
            Text(text = "당뇨 유형", fontSize = 13.sp, color = TextLabel, fontWeight = FontWeight.Medium)
            Spacer(modifier = Modifier.height(GlucoachSpacing.sm))
            Row(horizontalArrangement = Arrangement.spacedBy(GlucoachSpacing.sm)) {
                listOf("NONE" to "없음", "TYPE1" to "1형", "TYPE2" to "2형").forEach { (key, label) ->
                    DiabetesTypeChip(
                        label = label,
                        selected = diabetesType == key,
                        onClick = { diabetesType = key },
                    )
                }
            }
            Spacer(modifier = Modifier.height(GlucoachSpacing.lg))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text(text = "약물/인슐린 복용", fontSize = 13.sp, color = TextLabel, fontWeight = FontWeight.Medium)
                    Text(text = "당뇨약 또는 인슐린을 복용 중이에요", fontSize = 12.sp, color = TextSecondary)
                }
                Switch(
                    checked = isMedicated,
                    onCheckedChange = { isMedicated = it },
                    colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = Primary),
                )
            }
        }

        Spacer(modifier = Modifier.height(GlucoachSpacing.lg))

        SettingsSection(title = "혈당 목표 범위") {
            Row(horizontalArrangement = Arrangement.spacedBy(GlucoachSpacing.md)) {
                Column(modifier = Modifier.weight(1f)) {
                    SettingsTextField(
                        label = "하한 (mg/dL)",
                        value = targetLow,
                        onValueChange = { targetLow = it },
                        keyboardType = KeyboardType.Number,
                        placeholder = "70",
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    SettingsTextField(
                        label = "상한 (mg/dL)",
                        value = targetHigh,
                        onValueChange = { targetHigh = it },
                        keyboardType = KeyboardType.Number,
                        placeholder = "140",
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(GlucoachSpacing.lg))

        SettingsSection(title = "알림 설정") {
            Row(horizontalArrangement = Arrangement.spacedBy(GlucoachSpacing.md)) {
                Column(modifier = Modifier.weight(1f)) {
                    SettingsTextField(
                        label = "저혈당 기준 (mg/dL)",
                        value = alertLow,
                        onValueChange = { alertLow = it },
                        keyboardType = KeyboardType.Number,
                        placeholder = "70",
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    SettingsTextField(
                        label = "고혈당 기준 (mg/dL)",
                        value = alertHigh,
                        onValueChange = { alertHigh = it },
                        keyboardType = KeyboardType.Number,
                        placeholder = "180",
                    )
                }
            }
            Spacer(modifier = Modifier.height(GlucoachSpacing.lg))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text(text = "야간 모니터링", fontSize = 13.sp, color = TextLabel, fontWeight = FontWeight.Medium)
                    Text(text = "수면 중 혈당 이상 시 알림을 받아요", fontSize = 12.sp, color = TextSecondary)
                }
                Switch(
                    checked = nightWatch,
                    onCheckedChange = { nightWatch = it },
                    colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = Primary),
                )
            }
        }

        if (error != null) {
            Spacer(modifier = Modifier.height(GlucoachSpacing.md))
            Text(text = error, color = com.ssafy.s309.ui.theme.Error, fontSize = 13.sp)
        }

        Spacer(modifier = Modifier.height(GlucoachSpacing.lg))

        Button(
            onClick = onProjectorClick,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = androidx.compose.ui.graphics.Color(0xFF5C6BC0)),
        ) {
            Text(text = "프로젝터 제어", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(GlucoachSpacing.md))

        Button(
            onClick = {
                onSave(
                    UserSettingsUpdateRequest(
                        height = height.toFloatOrNull(),
                        weight = weight.toFloatOrNull(),
                        diabetesType = diabetesType,
                        isMedicated = isMedicated,
                        targetLow = targetLow.toDoubleOrNull(),
                        targetHigh = targetHigh.toDoubleOrNull(),
                        alertLow = alertLow.toIntOrNull(),
                        alertHigh = alertHigh.toIntOrNull(),
                        nightWatch = nightWatch,
                    ),
                )
            },
            enabled = !isSaving,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Primary),
        ) {
            if (isSaving) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = Color.White,
                    strokeWidth = 2.dp,
                )
            } else {
                Text(text = "저장", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(GlucoachSpacing.xxl))
    }
}

@Composable
private fun SettingsSection(
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

@Composable
private fun SettingsTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    keyboardType: KeyboardType = KeyboardType.Text,
    placeholder: String = "",
) {
    Text(text = label, fontSize = 13.sp, color = TextLabel, fontWeight = FontWeight.Medium)
    Spacer(modifier = Modifier.height(6.dp))
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text(text = placeholder, color = com.ssafy.s309.ui.theme.TextPlaceholder) },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        colors =
            OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Primary,
                unfocusedBorderColor = BorderLight,
            ),
    )
}

@Composable
private fun DiabetesTypeChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(if (selected) Primary else Color.Transparent)
                .border(
                    width = 1.dp,
                    color = if (selected) Primary else BorderLight,
                    shape = RoundedCornerShape(20.dp),
                )
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            color = if (selected) Color.White else GlucoachColors.TextPrimary,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        )
    }
}
