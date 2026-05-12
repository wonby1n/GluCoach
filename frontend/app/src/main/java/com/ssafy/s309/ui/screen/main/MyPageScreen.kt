package com.ssafy.s309.ui.screen.main

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.BluetoothDisabled
import androidx.compose.material.icons.outlined.FamilyRestroom
import androidx.compose.material.icons.outlined.ManageAccounts
import androidx.compose.material.icons.outlined.SentimentSatisfied
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.s309.ui.theme.GlucoachColors
import com.ssafy.s309.ui.theme.GlucoachCorner
import com.ssafy.s309.ui.theme.GlucoachSpacing

@Composable
fun MyPageContent(
    onAccountClick: () -> Unit = {},
    onHealthDetailClick: () -> Unit = {},
    onGuardianClick: () -> Unit = {},
    onDeviceClick: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
    onLogoutClick: () -> Unit = {},
    onWithdrawClick: (String) -> Unit = {},
    isDeviceConnected: Boolean = false,
    userEmail: String = "",
    modifier: Modifier = Modifier,
) {
    var showWithdrawDialog by remember { mutableStateOf(false) }
    var withdrawPassword by remember { mutableStateOf("") }

    if (showWithdrawDialog) {
        AlertDialog(
            onDismissRequest = {
                showWithdrawDialog = false
                withdrawPassword = ""
            },
            title = {
                Text(
                    text = "회원탈퇴",
                    fontWeight = FontWeight.Bold,
                )
            },
            text = {
                Column {
                    Text("정말로 탈퇴하시겠습니까?")
                    Spacer(modifier = Modifier.height(12.dp))
                    androidx.compose.material3.OutlinedTextField(
                        value = withdrawPassword,
                        onValueChange = { withdrawPassword = it },
                        label = { Text("비밀번호 확인") },
                        singleLine = true,
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showWithdrawDialog = false
                        onWithdrawClick(withdrawPassword)
                        withdrawPassword = ""
                    },
                    enabled = withdrawPassword.isNotEmpty(),
                    colors = ButtonDefaults.buttonColors(containerColor = com.ssafy.s309.ui.theme.Error),
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Text("예")
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = {
                        showWithdrawDialog = false
                        withdrawPassword = ""
                    },
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Text("아니오")
                }
            },
        )
    }

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp),
    ) {
        Spacer(modifier = Modifier.height(GlucoachSpacing.xxl))

        Text(
            text = "마이페이지",
            color = GlucoachColors.TextPrimary,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
        )

        Spacer(modifier = Modifier.height(40.dp))

        SectionLabel("정보")

        Spacer(modifier = Modifier.height(GlucoachSpacing.lg))

        InfoMenuItem(
            icon = Icons.Outlined.ManageAccounts,
            title = "내 계정",
            value = userEmail.ifEmpty { null },
            onClick = onAccountClick,
        )
        InfoMenuItem(
            icon = Icons.Outlined.SentimentSatisfied,
            title = "건강 세부사항",
            onClick = onHealthDetailClick,
        )
        InfoMenuItem(
            icon = Icons.Outlined.FamilyRestroom,
            title = "보호자 관리",
            onClick = onGuardianClick,
        )
        InfoMenuItem(
            icon = Icons.Outlined.Smartphone,
            title = "연결 기기",
            value = "Gluco Patch(PHI)",
            onClick = onDeviceClick,
        )
        InfoMenuItem(
            icon = Icons.Outlined.Settings,
            title = "설정",
            onClick = onSettingsClick,
        )

        Spacer(modifier = Modifier.height(GlucoachSpacing.lg))

        BluetoothCard(
            isConnected = isDeviceConnected,
            onClick = onDeviceClick,
        )

        Spacer(modifier = Modifier.height(64.dp))

        SectionLabel("서비스")

        Spacer(modifier = Modifier.height(GlucoachSpacing.lg))

        ServiceMenuItem("버전")
        ServiceMenuItem("서비스 약관")
        ServiceMenuItem("개인정보 약관")

        Spacer(modifier = Modifier.height(GlucoachSpacing.sm))

        Text(
            text = "로그아웃",
            color = GlucoachColors.Primary,
            fontSize = 16.sp,
            modifier =
                Modifier
                    .clickable(onClick = onLogoutClick)
                    .padding(vertical = GlucoachSpacing.md),
        )

        Spacer(modifier = Modifier.height(GlucoachSpacing.sm))

        Text(
            text = "회원탈퇴",
            color = GlucoachColors.TextSecondary,
            fontSize = 14.sp,
            modifier =
                Modifier
                    .clickable { showWithdrawDialog = true }
                    .padding(vertical = GlucoachSpacing.md),
        )

        Spacer(modifier = Modifier.height(GlucoachSpacing.xxl))
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        color = GlucoachColors.TextSecondary,
        fontSize = 13.sp,
    )
}

@Composable
private fun InfoMenuItem(
    icon: ImageVector,
    title: String,
    value: String? = null,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = GlucoachColors.TextPrimary,
            modifier = Modifier.size(24.dp),
        )

        Spacer(modifier = Modifier.width(GlucoachSpacing.md))

        Text(
            text = title,
            color = GlucoachColors.TextPrimary,
            fontSize = 16.sp,
        )

        Spacer(modifier = Modifier.weight(1f))

        if (value != null) {
            Text(
                text = value,
                color = GlucoachColors.TextSecondary,
                fontSize = 14.sp,
            )
            Spacer(modifier = Modifier.width(GlucoachSpacing.xs))
        }

        Icon(
            imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
            contentDescription = null,
            tint = GlucoachColors.TextSecondary,
            modifier = Modifier.size(24.dp),
        )
    }
}

@Composable
private fun BluetoothCard(
    isConnected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .shadow(elevation = 2.dp, shape = RoundedCornerShape(GlucoachCorner.card))
                .clip(RoundedCornerShape(GlucoachCorner.card))
                .background(GlucoachColors.Surface)
                .clickable(onClick = onClick)
                .padding(horizontal = GlucoachSpacing.lg, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(GlucoachSpacing.md),
    ) {
        val iconColor = if (isConnected) GlucoachColors.Primary else GlucoachColors.TextSecondary
        val bgColor = if (isConnected) GlucoachColors.Primary.copy(alpha = 0.1f) else GlucoachColors.TextSecondary.copy(alpha = 0.08f)

        Box(
            modifier =
                Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(bgColor),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (isConnected) Icons.Outlined.Bluetooth else Icons.Outlined.BluetoothDisabled,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(24.dp),
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Gluco Patch 블루투스",
                color = GlucoachColors.TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = if (isConnected) "연결됨" else "연결 안됨 — 탭하여 연결",
                color = iconColor,
                fontSize = 13.sp,
            )
        }

        Icon(
            imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
            contentDescription = null,
            tint = GlucoachColors.TextSecondary,
            modifier = Modifier.size(24.dp),
        )
    }
}

@Composable
private fun ServiceMenuItem(text: String) {
    Text(
        text = text,
        color = GlucoachColors.TextPrimary,
        fontSize = 16.sp,
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = GlucoachSpacing.md),
    )
}
