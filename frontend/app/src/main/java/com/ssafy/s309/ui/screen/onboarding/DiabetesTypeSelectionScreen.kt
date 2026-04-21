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
import com.ssafy.s309.ui.theme.Primary

data class DiabetesType(
    val title: String,
    val description: String,
)

@Composable
fun DiabetesTypeSelectionScreen(
    onNextClick: (Int) -> Unit,
    onBackClick: () -> Unit,
) {
    val diabetesTypes =
        listOf(
            DiabetesType(
                title = "당뇨 전 혈당 관리를 하고 싶어요",
                description = "당뇨를 진단 받은 적 없는 일반인이에요.",
            ),
            DiabetesType(
                title = "제1형 당뇨예요",
                description = "선천적으로 당 조절이 어렵다고 진단 받았어요.",
            ),
            DiabetesType(
                title = "제2형 당뇨예요",
                description = "생활 습관으로 인한 당뇨라고 진단 받았어요.",
            ),
        )

    var selectedType by remember { mutableStateOf(0) }

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

        ProgressIndicator(currentStep = 2)

        Spacer(modifier = Modifier.height(32.dp))

        OnboardingHeader(
            title = "당뇨 유형을 선택해주세요.",
            subtitle = "더 정확한 관리를 위해 아래 정보를 선택해주세요.",
        )

        Spacer(modifier = Modifier.height(24.dp))

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            diabetesTypes.forEachIndexed { index, type ->
                DiabetesTypeCard(
                    type = type,
                    isSelected = selectedType == index,
                    onClick = { selectedType = index },
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        OnboardingButton(
            text = "다음으로",
            onClick = { onNextClick(selectedType) },
        )
    }
}

@Composable
fun DiabetesTypeCard(
    type: DiabetesType,
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
                    color = if (isSelected) Primary else Color(0xFFE0E0E0),
                    shape = RoundedCornerShape(12.dp),
                )
                .clickable { onClick() }
                .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = type.title,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF222222),
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = type.description,
                fontSize = 12.sp,
                color = Color(0xFF757575),
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
                        .border(2.dp, Color(0xFFCCCCCC), CircleShape),
            )
        }
    }
}
