package com.ssafy.s309.ui.screen.auth

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.s309.R
import com.ssafy.s309.ui.theme.AccentBorder
import com.ssafy.s309.ui.theme.Background
import com.ssafy.s309.ui.theme.BorderLight
import com.ssafy.s309.ui.theme.Disabled
import com.ssafy.s309.ui.theme.Error
import com.ssafy.s309.ui.theme.Primary
import com.ssafy.s309.ui.theme.TextHint
import com.ssafy.s309.ui.theme.TextLabel
import com.ssafy.s309.ui.theme.TextPlaceholder

@Composable
fun SignUpScreen(
    onSignUpClick: (String, String, String, String, String) -> Unit,
    onAlreadyMemberClick: () -> Unit,
    onBackClick: () -> Unit,
    initialEmail: String = "",
    initialPassword: String = "",
    initialName: String = "",
    initialPhone: String = "",
    isLoading: Boolean = false,
    errorMessage: String? = null,
) {
    var email by remember { mutableStateOf(initialEmail) }
    var password by remember { mutableStateOf(initialPassword) }
    var confirmPassword by remember { mutableStateOf(initialPassword) }
    var name by remember { mutableStateOf(initialName) }
    var phone by remember { mutableStateOf(initialPhone) }

    val isEmailValid = email.isNotEmpty() && email.contains("@") && email.length <= 255
    val isPasswordValid = password.length >= 6
    val isPasswordMatch = password.isNotEmpty() && confirmPassword.isNotEmpty() && password == confirmPassword
    val isNameValid = name.isNotEmpty() && name.length <= 20
    val isPhoneValid = phone.isNotEmpty() && phone.length <= 20
    val isFormValid = isEmailValid && isPasswordValid && isPasswordMatch && isNameValid && isPhoneValid

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(Background)
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 32.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
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
                    .border(1.dp, AccentBorder, RoundedCornerShape(12.dp))
                    .padding(horizontal = 20.dp, vertical = 24.dp),
        ) {
            Text(
                text = "이메일",
                fontSize = 13.sp,
                color = TextLabel,
                fontWeight = FontWeight.Medium,
            )
            Spacer(modifier = Modifier.height(6.dp))
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                placeholder = { Text("example@email.com", color = TextPlaceholder) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                singleLine = true,
                keyboardOptions =
                    KeyboardOptions(
                        keyboardType = KeyboardType.Email,
                        imeAction = ImeAction.Next,
                    ),
                colors =
                    OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Primary,
                        unfocusedBorderColor = BorderLight,
                    ),
            )

            if (email.isNotEmpty() && !isEmailValid) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "올바른 이메일 형식을 입력해주세요",
                    fontSize = 12.sp,
                    color = Error,
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "비밀번호",
                fontSize = 13.sp,
                color = TextLabel,
                fontWeight = FontWeight.Medium,
            )
            Spacer(modifier = Modifier.height(6.dp))
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                placeholder = { Text("6자 이상", color = TextPlaceholder) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions =
                    KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Next,
                    ),
                colors =
                    OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Primary,
                        unfocusedBorderColor = BorderLight,
                    ),
            )

            if (password.isNotEmpty() && !isPasswordValid) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "비밀번호는 6자 이상이어야 합니다",
                    fontSize = 12.sp,
                    color = Error,
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "비밀번호 확인",
                fontSize = 13.sp,
                color = TextLabel,
                fontWeight = FontWeight.Medium,
            )
            Spacer(modifier = Modifier.height(6.dp))
            OutlinedTextField(
                value = confirmPassword,
                onValueChange = { confirmPassword = it },
                placeholder = { Text("비밀번호 재입력", color = TextPlaceholder) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions =
                    KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Next,
                    ),
                colors =
                    OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Primary,
                        unfocusedBorderColor = BorderLight,
                    ),
            )

            if (confirmPassword.isNotEmpty() && !isPasswordMatch) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "비밀번호가 일치하지 않습니다",
                    fontSize = 12.sp,
                    color = Error,
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "이름",
                fontSize = 13.sp,
                color = TextLabel,
                fontWeight = FontWeight.Medium,
            )
            Spacer(modifier = Modifier.height(6.dp))
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                placeholder = { Text("이름을 입력하세요", color = TextPlaceholder) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                singleLine = true,
                keyboardOptions =
                    KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Next,
                    ),
                colors =
                    OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Primary,
                        unfocusedBorderColor = BorderLight,
                    ),
            )

            if (name.isNotEmpty() && name.length > 20) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "형식에 맞도록 값을 입력해주세요",
                    fontSize = 12.sp,
                    color = Error,
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "전화번호",
                fontSize = 13.sp,
                color = TextLabel,
                fontWeight = FontWeight.Medium,
            )
            Spacer(modifier = Modifier.height(6.dp))
            OutlinedTextField(
                value = phone,
                onValueChange = { phone = it },
                placeholder = { Text("010-0000-0000", color = TextPlaceholder) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                singleLine = true,
                keyboardOptions =
                    KeyboardOptions(
                        keyboardType = KeyboardType.Phone,
                        imeAction = ImeAction.Done,
                    ),
                colors =
                    OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Primary,
                        unfocusedBorderColor = BorderLight,
                    ),
            )

            if (phone.isNotEmpty() && phone.length > 20) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "형식에 맞도록 값을 입력해주세요",
                    fontSize = 12.sp,
                    color = Error,
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "이미 회원이세요?",
                fontSize = 13.sp,
                color = TextHint,
                textDecoration = TextDecoration.Underline,
                modifier = Modifier.clickable { onAlreadyMemberClick() },
            )

            Spacer(modifier = Modifier.height(20.dp))

            if (errorMessage != null) {
                Text(
                    text = errorMessage,
                    fontSize = 13.sp,
                    color = Error,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            Button(
                onClick = { onSignUpClick(email, password, confirmPassword, name, phone) },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                shape = RoundedCornerShape(8.dp),
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = Primary,
                        disabledContainerColor = Disabled,
                    ),
                enabled = isFormValid && !isLoading,
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = Color.White,
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text(
                        text = "Sign Up",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White,
                    )
                }
            }
        }
    }
}
