package com.ssafy.s309

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.Modifier
import com.ssafy.s309.navigation.AppNavigation
import com.ssafy.s309.ui.theme.S309Theme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            S309Theme {
                // 앱 전역 기본값: edge-to-edge 로 그려지는 상태바와 컨텐츠가 겹치지 않도록
                // 회원가입 온보딩과 동일하게 statusBarsPadding() 을 루트에 적용한다.
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .statusBarsPadding(),
                ) {
                    AppNavigation()
                }
            }
        }
    }
}
