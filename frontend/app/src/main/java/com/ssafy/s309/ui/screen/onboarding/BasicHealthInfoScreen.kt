package com.ssafy.s309.ui.screen.onboarding

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.outlined.CheckBoxOutlineBlank
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.s309.R
import com.ssafy.s309.ui.component.onboarding.OnboardingBackButton
import com.ssafy.s309.ui.component.onboarding.OnboardingButton
import com.ssafy.s309.ui.component.onboarding.OnboardingHeader
import com.ssafy.s309.ui.component.onboarding.ProgressIndicator
import com.ssafy.s309.ui.theme.Background
import com.ssafy.s309.ui.theme.BorderLight
import com.ssafy.s309.ui.theme.Disabled
import com.ssafy.s309.ui.theme.Error
import com.ssafy.s309.ui.theme.Primary
import com.ssafy.s309.ui.theme.TextHint
import com.ssafy.s309.ui.theme.TextLabel
import com.ssafy.s309.ui.theme.TextMuted
import com.ssafy.s309.ui.theme.TextPlaceholder

@Composable
fun BasicHealthInfoScreen(
    onNextClick: (String, String, String) -> Unit,
    onSkipClick: () -> Unit,
    onBackClick: () -> Unit,
    initialAge: String = "",
    initialHeight: String = "",
    initialWeight: String = "",
) {
    var age by remember { mutableStateOf(initialAge) }
    var height by remember { mutableStateOf(initialHeight) }
    var weight by remember { mutableStateOf(initialWeight) }
    var isAgreed by remember { mutableStateOf(false) }

    val isAgeValid = age.isNotEmpty() && age.toIntOrNull()?.let { it in 1..127 } == true
    val decimalPattern = Regex("^\\d{1,3}(\\.\\d)?$")
    val isHeightValid = height.isNotEmpty() && height.matches(decimalPattern)
    val isWeightValid = weight.isNotEmpty() && weight.matches(decimalPattern)
    val isFormValid = isAgeValid && isHeightValid && isWeightValid && isAgreed

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(Background)
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
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

        ProgressIndicator(currentStep = 2, totalSteps = 4)

        Spacer(modifier = Modifier.height(32.dp))

        OnboardingHeader(
            title = "신체 정보를 알려주세요.",
            subtitle = "정확한 데이터 분석을 위해 기본 정보가 필요해요.",
        )

        Spacer(modifier = Modifier.height(32.dp))

        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(Color.White, RoundedCornerShape(12.dp))
                    .padding(horizontal = 20.dp, vertical = 24.dp),
        ) {
            Text(
                text = "나이",
                fontSize = 13.sp,
                color = TextLabel,
                fontWeight = FontWeight.Medium,
            )
            Spacer(modifier = Modifier.height(6.dp))
            OutlinedTextField(
                value = age,
                onValueChange = { newValue ->
                    age = newValue.filter { it.isDigit() }.take(3)
                },
                placeholder = { Text("만 00세", color = TextPlaceholder) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                colors =
                    OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Primary,
                        unfocusedBorderColor = BorderLight,
                    ),
            )

            if (age.isNotEmpty() && !isAgeValid) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "형식에 맞도록 값을 입력해주세요",
                    fontSize = 12.sp,
                    color = Error,
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "키(cm)",
                fontSize = 13.sp,
                color = TextLabel,
                fontWeight = FontWeight.Medium,
            )
            Spacer(modifier = Modifier.height(6.dp))
            OutlinedTextField(
                value = height,
                onValueChange = { newValue ->
                    if (newValue.isEmpty() || newValue.matches(Regex("^\\d{0,3}(\\.\\d{0,1})?$"))) {
                        height = newValue
                    }
                },
                placeholder = { Text("000.0", color = TextPlaceholder) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                colors =
                    OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Primary,
                        unfocusedBorderColor = BorderLight,
                    ),
            )

            if (height.isNotEmpty() && !isHeightValid) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "형식에 맞도록 값을 입력해주세요",
                    fontSize = 12.sp,
                    color = Error,
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "현재 체중(kg)",
                fontSize = 13.sp,
                color = TextLabel,
                fontWeight = FontWeight.Medium,
            )
            Spacer(modifier = Modifier.height(6.dp))
            OutlinedTextField(
                value = weight,
                onValueChange = { newValue ->
                    if (newValue.isEmpty() || newValue.matches(Regex("^\\d{0,3}(\\.\\d{0,1})?$"))) {
                        weight = newValue
                    }
                },
                placeholder = { Text("000.0", color = TextPlaceholder) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                colors =
                    OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Primary,
                        unfocusedBorderColor = BorderLight,
                    ),
            )

            if (weight.isNotEmpty() && !isWeightValid) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "형식에 맞도록 값을 입력해주세요",
                    fontSize = 12.sp,
                    color = Error,
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clickable { isAgreed = !isAgreed }
                        .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = if (isAgreed) Icons.Filled.CheckBox else Icons.Outlined.CheckBoxOutlineBlank,
                    contentDescription = if (isAgreed) "동의함" else "동의 안 함",
                    tint = if (isAgreed) Primary else Disabled,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "개인정보 수집에 동의해요.",
                        fontSize = 13.sp,
                        color = TextLabel,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        text = "해당 정보는 혈당 예측 알고리즘을 개선하고 서비스를 향상하는데 사용돼요.",
                        fontSize = 11.sp,
                        color = TextMuted,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "나중에 할게요",
            fontSize = 13.sp,
            color = TextHint,
            textDecoration = TextDecoration.Underline,
            modifier =
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .clickable { onSkipClick() },
        )

        Spacer(modifier = Modifier.height(32.dp))

        OnboardingButton(
            text = "다음으로",
            onClick = { onNextClick(age, height, weight) },
            enabled = isFormValid,
        )
    }
}
