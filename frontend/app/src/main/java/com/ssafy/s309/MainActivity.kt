package com.ssafy.s309

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.ssafy.s309.ui.theme.S309Theme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            S309Theme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    greeting(
                        name = "S309",
                        modifier = Modifier.padding(innerPadding),
                    )
                }
            }
        }
    }

    // TODO: 다음 작업 - Navigation 그래프 설정
    // TODO: Retrofit API 서비스 인터페이스 생성
    // TODO: 로그인/회원가입 화면 구현
}

@Composable
fun greeting(
    name: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = "Hello $name!")
    }
}

@Preview(showBackground = true)
@Composable
fun greetingPreview() {
    S309Theme {
        greeting("S309")
    }
}
