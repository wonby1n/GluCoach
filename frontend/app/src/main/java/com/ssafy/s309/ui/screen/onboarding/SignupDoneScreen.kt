package com.ssafy.s309.ui.screen.onboarding

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.s309.R
import com.ssafy.s309.ui.component.onboarding.OnboardingButton
import com.ssafy.s309.ui.theme.Background
import com.ssafy.s309.ui.theme.Error
import com.ssafy.s309.ui.theme.Primary
import com.ssafy.s309.ui.theme.TextPrimary

@Composable
fun SignupDoneScreen(
    isLoading: Boolean = false,
    errorMessage: String? = null,
    onRetryClick: () -> Unit = {},
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(Background)
                .padding(horizontal = 24.dp, vertical = 40.dp),
    ) {
        Spacer(modifier = Modifier.weight(1f))

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Image(
                painter = painterResource(id = R.drawable.ic_glucoach_logo),
                contentDescription = "Glucoach Logo",
                modifier = Modifier.width(200.dp),
            )

            Spacer(modifier = Modifier.height(48.dp))

            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(120.dp),
                    color = Primary,
                    strokeWidth = 4.dp,
                )

                Spacer(modifier = Modifier.height(32.dp))

                Text(
                    text = "회원가입 처리 중...",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                )
            } else if (errorMessage != null) {
                Text(
                    text = "회원가입에 실패했어요",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = errorMessage,
                    fontSize = 14.sp,
                    color = Error,
                )
            } else {
                Box(
                    modifier =
                        Modifier
                            .size(120.dp)
                            .background(Primary, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "완료",
                        modifier = Modifier.size(70.dp),
                        tint = Color.White,
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))

                Text(
                    text = "회원가입이 완료됐어요!",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        if (errorMessage != null) {
            OnboardingButton(
                text = "다시 시도",
                onClick = onRetryClick,
            )
        }
    }
}
