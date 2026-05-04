package com.ssafy.s309.ui.screen.main

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.ManageAccounts
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ssafy.s309.ui.theme.Background
import com.ssafy.s309.ui.theme.BorderLight
import com.ssafy.s309.ui.theme.GlucoachColors
import com.ssafy.s309.ui.theme.GlucoachSpacing
import com.ssafy.s309.ui.theme.Primary

@Composable
fun MyAccountScreen(
    viewModel: MyAccountViewModel = hiltViewModel(),
    onBack: () -> Unit = {},
    onWithdrawClick: (String) -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
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
            Icon(
                imageVector = Icons.Outlined.ManageAccounts,
                contentDescription = null,
                tint = GlucoachColors.TextPrimary,
                modifier = Modifier.size(24.dp),
            )
            Spacer(modifier = Modifier.size(8.dp))
            Text(
                text = "내 계정",
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
            uiState.error != null && uiState.email.isEmpty() -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = uiState.error ?: "",
                            color = GlucoachColors.TextSecondary,
                            fontSize = 14.sp,
                        )
                        Spacer(modifier = Modifier.height(GlucoachSpacing.lg))
                        Button(
                            onClick = viewModel::loadProfile,
                            colors = ButtonDefaults.buttonColors(containerColor = Primary),
                        ) {
                            Text("다시 시도")
                        }
                    }
                }
            }
            else -> {
                MyAccountForm(
                    uiState = uiState,
                    onSave = viewModel::saveProfile,
                    onWithdrawClick = { showWithdrawDialog = true },
                )
            }
        }
    }
}

@Composable
private fun MyAccountForm(
    uiState: MyAccountUiState,
    onSave: (String, Int?, String) -> Unit,
    onWithdrawClick: () -> Unit,
) {
    var name by remember(uiState.name) { mutableStateOf(uiState.name) }
    var age by remember(uiState.age) { mutableStateOf(uiState.age?.toString() ?: "") }
    var phone by remember(uiState.phone) { mutableStateOf(uiState.phone) }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp),
    ) {
        Spacer(modifier = Modifier.height(24.dp))

        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            Box(modifier = Modifier.size(120.dp)) {
                Box(
                    modifier =
                        Modifier
                            .size(120.dp)
                            .clip(CircleShape)
                            .background(BorderLight),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Person,
                        contentDescription = null,
                        modifier = Modifier.size(60.dp),
                        tint = GlucoachColors.TextSecondary,
                    )
                }
                Box(
                    modifier =
                        Modifier
                            .size(32.dp)
                            .align(Alignment.BottomEnd)
                            .clip(CircleShape)
                            .background(Color.White)
                            .border(1.dp, BorderLight, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.CameraAlt,
                        contentDescription = "사진 변경",
                        modifier = Modifier.size(18.dp),
                        tint = GlucoachColors.TextPrimary,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        AccountField(label = "이메일", value = uiState.email)
        HorizontalDivider(color = BorderLight)

        AccountEditableField(label = "이름", value = name, onValueChange = { name = it })
        HorizontalDivider(color = BorderLight)

        Text(
            text = "비밀번호 변경",
            color = Primary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            modifier =
                Modifier
                    .clickable { }
                    .padding(vertical = 16.dp),
        )
        HorizontalDivider(color = BorderLight)

        AccountEditableField(
            label = "나이",
            value = if (age.isNotEmpty()) "만 ${age}세" else "",
            onValueChange = { input ->
                age = input.filter { it.isDigit() }.take(3)
            },
            rawValue = age,
        )
        HorizontalDivider(color = BorderLight)

        AccountEditableField(label = "전화번호", value = phone, onValueChange = { phone = it })
        HorizontalDivider(color = BorderLight)

        AccountField(label = "연결된 보호자", value = uiState.guardian.ifEmpty { "-" })
        HorizontalDivider(color = BorderLight)

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "회원탈퇴",
            color = GlucoachColors.TextSecondary,
            fontSize = 14.sp,
            modifier =
                Modifier
                    .clickable(onClick = onWithdrawClick)
                    .padding(vertical = 12.dp),
        )

        Spacer(modifier = Modifier.weight(1f))

        Button(
            onClick = { onSave(name, age.toIntOrNull(), phone) },
            enabled = !uiState.isSaving,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp)
                    .height(52.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Primary),
        ) {
            if (uiState.isSaving) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = Color.White,
                    strokeWidth = 2.dp,
                )
            } else {
                Text(
                    text = "Save Changes",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun AccountField(
    label: String,
    value: String,
) {
    Column(modifier = Modifier.padding(vertical = 12.dp)) {
        Text(text = label, fontSize = 12.sp, color = GlucoachColors.TextSecondary)
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = value, fontSize = 16.sp, color = GlucoachColors.TextPrimary)
    }
}

@Composable
private fun AccountEditableField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    rawValue: String? = null,
) {
    Column(modifier = Modifier.padding(vertical = 12.dp)) {
        Text(text = label, fontSize = 12.sp, color = GlucoachColors.TextSecondary)
        Spacer(modifier = Modifier.height(4.dp))
        androidx.compose.foundation.text.BasicTextField(
            value = rawValue ?: value,
            onValueChange = onValueChange,
            textStyle =
                androidx.compose.ui.text.TextStyle(
                    fontSize = 16.sp,
                    color = GlucoachColors.TextPrimary,
                ),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            decorationBox = { innerTextField ->
                if ((rawValue ?: value).isEmpty()) {
                    Text(
                        text = "-",
                        fontSize = 16.sp,
                        color = GlucoachColors.TextSecondary,
                    )
                }
                innerTextField()
            },
        )
        if (rawValue != null && rawValue.isNotEmpty()) {
            Text(
                text = value,
                fontSize = 12.sp,
                color = GlucoachColors.TextSecondary,
            )
        }
    }
}
