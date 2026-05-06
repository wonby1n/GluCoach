package com.ssafy.s309.ui.screen.main

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ssafy.s309.data.model.GuardianItem
import com.ssafy.s309.ui.theme.Background
import com.ssafy.s309.ui.theme.BorderLight
import com.ssafy.s309.ui.theme.GlucoachColors
import com.ssafy.s309.ui.theme.GlucoachSpacing
import com.ssafy.s309.ui.theme.Primary
import com.ssafy.s309.ui.theme.TextLabel
import com.ssafy.s309.ui.theme.TextPlaceholder

@Composable
fun GuardianScreen(
    viewModel: GuardianViewModel = hiltViewModel(),
    onBack: () -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showAddDialog by remember { mutableStateOf(false) }
    var editingGuardian by remember { mutableStateOf<GuardianItem?>(null) }
    var deleteTarget by remember { mutableStateOf<GuardianItem?>(null) }

    Scaffold(
        containerColor = Background,
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = Primary,
                contentColor = Color.White,
            ) {
                Icon(imageVector = Icons.Outlined.Add, contentDescription = "보호자 추가")
            }
        },
    ) { innerPadding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
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
                    text = "보호자 관리",
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
                uiState.guardians.isEmpty() -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "등록된 보호자가 없어요",
                                color = GlucoachColors.TextSecondary,
                                fontSize = 15.sp,
                            )
                            Spacer(modifier = Modifier.height(GlucoachSpacing.sm))
                            Text(
                                text = "+ 버튼을 눌러 보호자를 추가해보세요",
                                color = GlucoachColors.TextSecondary,
                                fontSize = 13.sp,
                            )
                        }
                    }
                }
                else -> {
                    Column(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = 22.dp, vertical = GlucoachSpacing.md),
                        verticalArrangement = Arrangement.spacedBy(GlucoachSpacing.md),
                    ) {
                        uiState.guardians.forEach { guardian ->
                            GuardianCard(
                                guardian = guardian,
                                onEdit = { editingGuardian = guardian },
                                onDelete = { deleteTarget = guardian },
                            )
                        }
                        Spacer(modifier = Modifier.height(80.dp))
                    }
                }
            }
        }
    }

    // 보호자 추가 — 1단계: 전화번호 검색 / 2단계: 관계 입력 후 확정
    if (showAddDialog) {
        AddGuardianDialog(
            uiState = uiState,
            onSearch = { phone -> viewModel.searchUserByPhone(phone) },
            onConfirm = { relation ->
                viewModel.createGuardian(relation)
                showAddDialog = false
                viewModel.clearSearchState()
            },
            onDismiss = {
                showAddDialog = false
                viewModel.clearSearchState()
            },
        )
    }

    // 보호자 수정 — 관계(relation)만 수정 가능
    editingGuardian?.let { guardian ->
        EditGuardianDialog(
            initialRelation = guardian.relation ?: "",
            onDismiss = { editingGuardian = null },
            onConfirm = { relation ->
                viewModel.updateGuardian(guardian, relation)
                editingGuardian = null
            },
        )
    }

    deleteTarget?.let { guardian ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("보호자 삭제") },
            text = {
                val label = guardian.name.ifBlank { "보호자 #${guardian.guardianId}" }
                Text("${label}을(를) 보호자 목록에서 삭제할까요?")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteGuardian(guardian)
                        deleteTarget = null
                    },
                ) {
                    Text("삭제", color = com.ssafy.s309.ui.theme.Error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text("취소")
                }
            },
        )
    }
}

/** 보호자 추가 다이얼로그 — 전화번호 검색 후 관계 입력 */
@Composable
private fun AddGuardianDialog(
    uiState: GuardianUiState,
    onSearch: (String) -> Unit,
    onConfirm: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var phone by remember { mutableStateOf("") }
    var relation by remember { mutableStateOf("") }

    val searchResult = uiState.searchResult
    val isPhoneValid = phone.length in 10..11 && phone.all { it.isDigit() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("보호자 추가", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(GlucoachSpacing.md)) {
                if (searchResult == null) {
                    // 1단계: 전화번호로 검색
                    FormField(
                        label = "보호자 전화번호 (숫자만)",
                        value = phone,
                        onValueChange = { if (it.all { c -> c.isDigit() } && it.length <= 11) phone = it },
                        placeholder = "01012345678",
                        keyboardType = KeyboardType.Phone,
                    )
                    uiState.searchError?.let { err ->
                        Text(text = err, fontSize = 12.sp, color = com.ssafy.s309.ui.theme.Error)
                    }
                    if (uiState.isSearching) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Primary, strokeWidth = 2.dp)
                    }
                } else {
                    // 2단계: 검색된 사용자 확인 + 관계 입력
                    Text(
                        text = "찾은 사용자: ${searchResult.name}",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = GlucoachColors.TextPrimary,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    FormField(
                        label = "관계 (선택)",
                        value = relation,
                        onValueChange = { relation = it },
                        placeholder = "예: 부모님, 배우자",
                    )
                }
            }
        },
        confirmButton = {
            if (searchResult == null) {
                Button(
                    onClick = { onSearch(phone) },
                    enabled = isPhoneValid && !uiState.isSearching,
                    colors = ButtonDefaults.buttonColors(containerColor = Primary),
                ) {
                    Text("검색")
                }
            } else {
                Button(
                    onClick = { onConfirm(relation.ifBlank { null }) },
                    colors = ButtonDefaults.buttonColors(containerColor = Primary),
                ) {
                    Text("추가")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("취소", color = GlucoachColors.TextSecondary)
            }
        },
    )
}

/** 보호자 수정 다이얼로그 — 관계(relation)만 수정 */
@Composable
private fun EditGuardianDialog(
    initialRelation: String,
    onDismiss: () -> Unit,
    onConfirm: (String?) -> Unit,
) {
    var relation by remember { mutableStateOf(initialRelation) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("보호자 수정", fontWeight = FontWeight.Bold) },
        text = {
            FormField(
                label = "관계 (선택)",
                value = relation,
                onValueChange = { relation = it },
                placeholder = "예: 부모님, 배우자",
            )
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(relation.ifBlank { null }) },
                colors = ButtonDefaults.buttonColors(containerColor = Primary),
            ) {
                Text("저장")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("취소", color = GlucoachColors.TextSecondary)
            }
        },
    )
}

@Composable
private fun GuardianCard(
    guardian: GuardianItem,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .shadow(2.dp, RoundedCornerShape(16.dp))
                .clip(RoundedCornerShape(16.dp))
                .background(Color.White)
                .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = guardian.name.ifBlank { "보호자 #${guardian.guardianId}" },
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = GlucoachColors.TextPrimary,
                )
                if (guardian.priority == 0) {
                    Spacer(modifier = Modifier.width(GlucoachSpacing.sm))
                    Box(
                        modifier =
                            Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(Primary.copy(alpha = 0.15f))
                                .padding(horizontal = 8.dp, vertical = 2.dp),
                    ) {
                        Text(text = "주 보호자", fontSize = 11.sp, color = Primary, fontWeight = FontWeight.Bold)
                    }
                }
            }
            if (!guardian.relation.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = guardian.relation, fontSize = 13.sp, color = GlucoachColors.TextSecondary)
            }
        }
        IconButton(onClick = onEdit, modifier = Modifier.size(40.dp)) {
            Icon(
                imageVector = Icons.Outlined.Edit,
                contentDescription = "수정",
                tint = GlucoachColors.TextSecondary,
                modifier = Modifier.size(20.dp),
            )
        }
        IconButton(onClick = onDelete, modifier = Modifier.size(40.dp)) {
            Icon(
                imageVector = Icons.Outlined.Delete,
                contentDescription = "삭제",
                tint = com.ssafy.s309.ui.theme.Error,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun FormField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String = "",
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    Column {
        Text(text = label, fontSize = 12.sp, color = TextLabel, fontWeight = FontWeight.Medium)
        Spacer(modifier = Modifier.height(4.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = { Text(text = placeholder, color = TextPlaceholder) },
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
}
