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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.outlined.Vaccines
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.s309.R
import com.ssafy.s309.ui.component.onboarding.OnboardingBackButton
import com.ssafy.s309.ui.component.onboarding.OnboardingButton
import com.ssafy.s309.ui.component.onboarding.OnboardingHeader
import com.ssafy.s309.ui.component.onboarding.ProgressIndicator
import com.ssafy.s309.ui.theme.Primary

data class TreatmentMethod(
    val title: String,
    val icon: ImageVector,
)

@Composable
fun TreatmentSelectionScreen(
    onNextClick: (Int) -> Unit,
    onBackClick: () -> Unit,
) {
    val treatmentMethods =
        listOf(
            TreatmentMethod("약물 복용하고\n있어요.", Icons.Filled.Medication),
            TreatmentMethod("주사를 투여하고\n있어요.", Icons.Outlined.Vaccines),
        )

    var selectedMethod by remember { mutableStateOf<Int?>(null) }

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

        ProgressIndicator(currentStep = 4, totalSteps = 7)

        Spacer(modifier = Modifier.height(32.dp))

        OnboardingHeader(
            title = "치료 방법을 선택해주세요.",
            subtitle = "더 정확한 관리를 위해 아래 정보를 선택해주세요.",
        )

        Spacer(modifier = Modifier.height(32.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            treatmentMethods.forEachIndexed { index, method ->
                TreatmentMethodCard(
                    method = method,
                    isSelected = selectedMethod == index,
                    onClick = { selectedMethod = index },
                    modifier = Modifier.weight(1f),
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        OnboardingButton(
            text = "다음으로",
            onClick = { selectedMethod?.let { onNextClick(it) } },
            enabled = selectedMethod != null,
        )
    }
}

@Composable
fun TreatmentMethodCard(
    method: TreatmentMethod,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .height(200.dp)
                .background(Color.White, RoundedCornerShape(12.dp))
                .border(
                    width = if (isSelected) 2.dp else 1.dp,
                    color = if (isSelected) Primary else Color(0xFFE0E0E0),
                    shape = RoundedCornerShape(12.dp),
                )
                .clickable { onClick() }
                .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = method.icon,
            contentDescription = method.title,
            modifier = Modifier.size(64.dp),
            tint = if (isSelected) Primary else Color(0xFF999999),
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = method.title,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = Color(0xFF444444),
            textAlign = TextAlign.Center,
        )
    }
}
