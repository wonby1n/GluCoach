package com.ssafy.s309.ui.screen.auth

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.s309.R
import com.ssafy.s309.ui.theme.Primary

@Composable
fun SignInScreen(
    onSignInClick: (String, String) -> Unit,
    onForgotPasswordClick: () -> Unit,
    onBackClick: () -> Unit,
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(Color(0xFFF2F4F5))
                .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Image(
            painter = painterResource(id = R.drawable.ic_glucoach_logo),
            contentDescription = "Glucoach Logo",
            modifier = Modifier.width(240.dp),
        )

        Spacer(modifier = Modifier.height(40.dp))

        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(Color.White, RoundedCornerShape(12.dp))
                    .border(1.dp, Color(0xFFB8E0E8), RoundedCornerShape(12.dp))
                    .padding(horizontal = 20.dp, vertical = 24.dp),
        ) {
            Text(
                text = "이메일",
                fontSize = 13.sp,
                color = Color(0xFF444444),
                fontWeight = FontWeight.Medium,
            )
            Spacer(modifier = Modifier.height(6.dp))
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                placeholder = { Text("Value", color = Color(0xFFBBBBBB)) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                colors =
                    OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Primary,
                        unfocusedBorderColor = Color(0xFFDDDDDD),
                    ),
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "비밀번호",
                fontSize = 13.sp,
                color = Color(0xFF444444),
                fontWeight = FontWeight.Medium,
            )
            Spacer(modifier = Modifier.height(6.dp))
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                placeholder = { Text("Value", color = Color(0xFFBBBBBB)) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                // SignUp 과 동일. KeyboardType.Password 는 일부 한국 IME 에서 숫자 키패드로
                // 해석되어 알파벳/특수문자 입력이 막히므로 Text 를 사용한다.
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                colors =
                    OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Primary,
                        unfocusedBorderColor = Color(0xFFDDDDDD),
                    ),
            )

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = { onSignInClick(email, password) },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Primary),
            ) {
                Text(
                    text = "Sign In",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White,
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "비밀번호를 잊어버리셨나요?",
                fontSize = 13.sp,
                color = Color(0xFF555555),
                textDecoration = TextDecoration.Underline,
                modifier = Modifier.clickable { onForgotPasswordClick() },
            )
        }
    }
}
