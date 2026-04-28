package com.ssafy.s309.ui.screen.onboarding

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.s309.R
import com.ssafy.s309.ui.component.onboarding.OnboardingBackButton
import com.ssafy.s309.ui.component.onboarding.OnboardingButton
import com.ssafy.s309.ui.component.onboarding.OnboardingHeader
import com.ssafy.s309.ui.component.onboarding.ProgressIndicator
import com.ssafy.s309.ui.theme.Background
import com.ssafy.s309.ui.theme.Border
import com.ssafy.s309.ui.theme.Primary
import com.ssafy.s309.ui.theme.TextHint
import com.ssafy.s309.ui.theme.TextMuted
import com.ssafy.s309.ui.theme.TextPrimary
import com.ssafy.s309.ui.theme.TextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BloodSugarRangeScreen(
    onNextClick: (Float, Float) -> Unit,
    onSkipClick: () -> Unit,
    onBackClick: () -> Unit,
) {
    var rangeStart by remember { mutableFloatStateOf(70f) }
    var rangeEnd by remember { mutableFloatStateOf(180f) }
    var showDialog by remember { mutableStateOf(true) }

    if (showDialog) {
        BloodSugarRangeDialog(
            onDismiss = { showDialog = false },
            onConfirm = { showDialog = false },
            onSkip = onSkipClick,
        )
    }

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

        ProgressIndicator(currentStep = 7, totalSteps = 7)

        Spacer(modifier = Modifier.height(32.dp))

        OnboardingHeader(
            title = "목표 혈당 범위 설정",
            subtitle = "관리하고 싶은 목표 혈당 범위가 있나요?",
        )

        Spacer(modifier = Modifier.height(48.dp))

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
            ) {
                RangeValueBox(value = rangeStart.toInt())
                Spacer(modifier = Modifier.width(40.dp))
                RangeValueBox(value = rangeEnd.toInt())
            }

            Spacer(modifier = Modifier.height(24.dp))

            RangeSlider(
                value = rangeStart..rangeEnd,
                onValueChange = { range ->
                    rangeStart = range.start
                    rangeEnd = range.endInclusive
                },
                valueRange = 20f..220f,
                steps = 39,
                colors =
                    SliderDefaults.colors(
                        thumbColor = Primary,
                        activeTrackColor = Primary,
                        inactiveTrackColor = Border,
                    ),
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                listOf(20, 60, 100, 140, 180, 220).forEach { value ->
                    Text(
                        text = value.toString(),
                        fontSize = 12.sp,
                        color = TextMuted,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

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

        Spacer(modifier = Modifier.weight(1f))

        OnboardingButton(
            text = "다음으로",
            onClick = { onNextClick(rangeStart, rangeEnd) },
        )
    }
}

@Composable
fun RangeValueBox(value: Int) {
    Box(
        modifier =
            Modifier
                .size(width = 70.dp, height = 50.dp)
                .background(Primary, RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = value.toString(),
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
        )
    }
}

@Composable
fun BloodSugarRangeDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    onSkip: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "닫기",
                        tint = TextMuted,
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "목표 혈당 범위가 있나요?",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "범위를 알려주시면 나만의 데이터로 도와드려요.",
                    fontSize = 14.sp,
                    color = TextSecondary,
                    textAlign = TextAlign.Center,
                )
            }
        },
        confirmButton = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OnboardingButton(
                    text = "설정하기",
                    onClick = onConfirm,
                )
                OutlinedButton(
                    onClick = onSkip,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Text(
                        text = "나중에 하기",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = Primary,
                    )
                }
            }
        },
    )
}
