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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.s309.R
import com.ssafy.s309.ui.component.onboarding.OnboardingBackButton
import com.ssafy.s309.ui.component.onboarding.OnboardingButton
import com.ssafy.s309.ui.component.onboarding.OnboardingHeader
import com.ssafy.s309.ui.component.onboarding.ProgressIndicator
import com.ssafy.s309.ui.theme.Background
import com.ssafy.s309.ui.theme.CardInactive
import com.ssafy.s309.ui.theme.Disabled
import com.ssafy.s309.ui.theme.Primary
import com.ssafy.s309.ui.theme.TextPrimary
import com.ssafy.s309.ui.theme.TextSecondary

data class TreatmentTime(
    val title: String,
    val description: String,
)

@Composable
fun TreatmentTimeScreen(
    onNextClick: (Int) -> Unit,
    onBackClick: () -> Unit,
) {
    val treatmentTimes =
        listOf(
            TreatmentTime(
                title = "식전",
                description = "식후 급격한 혈당 상승을 미리 방지해요",
            ),
            TreatmentTime(
                title = "식사 직후 / 식사 중",
                description = "위산 부담을 줄이고 식사 효과와 맞춰요",
            ),
            TreatmentTime(
                title = "아침 일찍(공복)",
                description = "하루 종일 일정한 약 효과를 유지해요",
            ),
            TreatmentTime(
                title = "기타(직접 입력할까요)",
                description = "처방받으신 특별한 복용 시간이 있나요?",
            ),
        )

    var selectedTime by remember { mutableStateOf<Int?>(null) }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(Background)
                .statusBarsPadding()
                .padding(horizontal = 24.dp, vertical = 16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OnboardingBackButton(onClick = onBackClick)
            Spacer(modifier = Modifier.weight(1f))
            Image(
                painter = painterResource(id = R.drawable.ic_glucoach_logo),
                contentDescription = "Glucoach Logo",
                modifier = Modifier.width(160.dp),
            )
            Spacer(modifier = Modifier.weight(1f))
        }

        Spacer(modifier = Modifier.height(24.dp))

        ProgressIndicator(currentStep = 5, totalSteps = 7)

        Spacer(modifier = Modifier.height(32.dp))

        OnboardingHeader(
            title = "치료는 언제 하시나요?",
            subtitle = "더 정확한 관리를 위해 아래 정보를 선택해주세요.",
        )

        Spacer(modifier = Modifier.height(24.dp))

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            treatmentTimes.forEachIndexed { index, time ->
                TreatmentTimeCard(
                    time = time,
                    isSelected = selectedTime == index,
                    onClick = { selectedTime = index },
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        OnboardingButton(
            text = "다음으로",
            onClick = { selectedTime?.let { onNextClick(it) } },
            enabled = selectedTime != null,
        )
    }
}

@Composable
fun TreatmentTimeCard(
    time: TreatmentTime,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(Color.White, RoundedCornerShape(12.dp))
                .border(
                    width = if (isSelected) 2.dp else 1.dp,
                    color = if (isSelected) Primary else CardInactive,
                    shape = RoundedCornerShape(12.dp),
                )
                .clickable { onClick() }
                .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = time.title,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = time.description,
                fontSize = 12.sp,
                color = TextSecondary,
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        if (isSelected) {
            Spacer(
                modifier =
                    Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(Primary),
            )
        } else {
            Spacer(
                modifier =
                    Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .border(2.dp, Disabled, CircleShape),
            )
        }
    }
}
