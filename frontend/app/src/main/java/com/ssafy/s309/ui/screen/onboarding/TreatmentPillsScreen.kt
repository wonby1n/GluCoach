package com.ssafy.s309.ui.screen.onboarding

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.outlined.CheckBoxOutlineBlank
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.s309.R
import com.ssafy.s309.ui.component.onboarding.OnboardingBackButton
import com.ssafy.s309.ui.component.onboarding.OnboardingButton
import com.ssafy.s309.ui.component.onboarding.OnboardingHeader
import com.ssafy.s309.ui.component.onboarding.ProgressIndicator
import com.ssafy.s309.ui.theme.Primary

@Composable
fun TreatmentPillsScreen(
    onNextClick: (List<String>) -> Unit,
    onBackClick: () -> Unit,
) {
    val medications =
        listOf(
            "메트포르민 계열(디아베스, 글루코판 등)",
            "DPP-4 억제제(자누비아, 트라젠타, 제미글로 등)",
            "SGLT-2 억제제(포시가, 자디앙 등)",
            "설포닐우레아(아마릴, 디아미크론 등)",
            "지아졸리딘디온(액토스, 듀비에 등)",
            "기타(직접 입력할까요?)",
        )

    val selectedMedications = remember { mutableStateListOf<Int>() }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(Color(0xFFF2F4F5))
                .padding(horizontal = 24.dp, vertical = 16.dp),
    ) {
        OnboardingBackButton(onClick = onBackClick)

        Spacer(modifier = Modifier.height(24.dp))

        Image(
            painter = painterResource(id = R.drawable.ic_glucoach_logo),
            contentDescription = "Glucoach Logo",
            modifier = Modifier.width(160.dp),
        )

        Spacer(modifier = Modifier.height(24.dp))

        ProgressIndicator(currentStep = 6, totalSteps = 7)

        Spacer(modifier = Modifier.height(32.dp))

        OnboardingHeader(
            title = "현재 복용하는 약물이 무엇인가요?",
            subtitle = "제2형 당뇨환자에서 복용 중인 약물을 모두 골라주세요.",
        )

        Spacer(modifier = Modifier.height(24.dp))

        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            medications.forEachIndexed { index, medication ->
                MedicationCheckbox(
                    medication = medication,
                    isChecked = selectedMedications.contains(index),
                    onCheckedChange = {
                        if (selectedMedications.contains(index)) {
                            selectedMedications.remove(index)
                        } else {
                            selectedMedications.add(index)
                        }
                    },
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        OnboardingButton(
            text = "다음으로",
            onClick = {
                val selected = selectedMedications.map { medications[it] }
                onNextClick(selected)
            },
            enabled = selectedMedications.isNotEmpty(),
        )
    }
}

@Composable
fun MedicationCheckbox(
    medication: String,
    isChecked: Boolean,
    onCheckedChange: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(Color.White, RoundedCornerShape(8.dp))
                .border(
                    width = if (isChecked) 2.dp else 1.dp,
                    color = if (isChecked) Primary else Color(0xFFE0E0E0),
                    shape = RoundedCornerShape(8.dp),
                )
                .clickable { onCheckedChange() }
                .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = medication,
            fontSize = 14.sp,
            color = Color(0xFF444444),
            modifier = Modifier.weight(1f),
        )

        Icon(
            imageVector = if (isChecked) Icons.Filled.CheckBox else Icons.Outlined.CheckBoxOutlineBlank,
            contentDescription = if (isChecked) "선택됨" else "선택 안 됨",
            tint = if (isChecked) Primary else Color(0xFFCCCCCC),
        )
    }
}
