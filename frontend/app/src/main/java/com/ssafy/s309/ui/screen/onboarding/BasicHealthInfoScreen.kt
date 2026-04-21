package com.ssafy.s309.ui.screen.onboarding

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import com.ssafy.s309.ui.theme.Primary

@Composable
fun BasicHealthInfoScreen(
    onNextClick: (String, String, String) -> Unit,
    onSkipClick: () -> Unit,
    onBackClick: () -> Unit,
) {
    var birthDate by remember { mutableStateOf("") }
    var height by remember { mutableStateOf("") }
    var weight by remember { mutableStateOf("") }
    var isAgreed by remember { mutableStateOf(false) }

    val isFormValid = birthDate.isNotEmpty() && height.isNotEmpty() && weight.isNotEmpty() && isAgreed

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

        ProgressIndicator(currentStep = 2, totalSteps = 7)

        Spacer(modifier = Modifier.height(32.dp))

        OnboardingHeader(
            title = "신체 정보를 알려주세요.",
            subtitle = "관리하고 싶은 목표 혈당 범위가 있나요?",
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
                text = "생년월일",
                fontSize = 13.sp,
                color = Color(0xFF444444),
                fontWeight = FontWeight.Medium,
            )
            Spacer(modifier = Modifier.height(6.dp))
            OutlinedTextField(
                value = birthDate,
                onValueChange = { birthDate = it },
                placeholder = { Text("YYYY.MM.DD.", color = Color(0xFFBBBBBB)) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                colors =
                    OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Primary,
                        unfocusedBorderColor = Color(0xFFDDDDDD),
                    ),
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "키(cm)",
                fontSize = 13.sp,
                color = Color(0xFF444444),
                fontWeight = FontWeight.Medium,
            )
            Spacer(modifier = Modifier.height(6.dp))
            OutlinedTextField(
                value = height,
                onValueChange = { height = it },
                placeholder = { Text("Value", color = Color(0xFFBBBBBB)) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                colors =
                    OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Primary,
                        unfocusedBorderColor = Color(0xFFDDDDDD),
                    ),
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "현재 체중(kg)",
                fontSize = 13.sp,
                color = Color(0xFF444444),
                fontWeight = FontWeight.Medium,
            )
            Spacer(modifier = Modifier.height(6.dp))
            OutlinedTextField(
                value = weight,
                onValueChange = { weight = it },
                placeholder = { Text("Value", color = Color(0xFFBBBBBB)) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                colors =
                    OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Primary,
                        unfocusedBorderColor = Color(0xFFDDDDDD),
                    ),
            )

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
                    tint = if (isAgreed) Primary else Color(0xFFCCCCCC),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "개인정보 수집에 동의해요.",
                        fontSize = 13.sp,
                        color = Color(0xFF444444),
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        text = "해당 정보는 혈당 예측 알고리즘을 개선하고 서비스를 향상하는데 사용돼요.",
                        fontSize = 11.sp,
                        color = Color(0xFF999999),
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "나중에 할게요",
            fontSize = 13.sp,
            color = Color(0xFF555555),
            textDecoration = TextDecoration.Underline,
            modifier =
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .clickable { onSkipClick() },
        )

        Spacer(modifier = Modifier.weight(1f))

        OnboardingButton(
            text = "다음으로",
            onClick = { onNextClick(birthDate, height, weight) },
            enabled = isFormValid,
        )
    }
}
