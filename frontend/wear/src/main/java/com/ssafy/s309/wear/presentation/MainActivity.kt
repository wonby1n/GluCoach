package com.ssafy.s309.wear.presentation

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import androidx.wear.compose.material3.Text
import com.ssafy.s309.wear.presentation.theme.GlucoFitTheme
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class MainActivity : ComponentActivity() {
    private var stepsValue by mutableStateOf<Double?>(null)

    private val prefListener =
        SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
            // "latest" 값이 직전과 같으면 일부 안드로이드 버전에서 해당 키 콜백이 누락됨.
            // GlucoseListenerService 가 매번 갱신하는 "updated_at" timestamp 콜백을 트리거로
            // latest 를 다시 읽으면 동일 값이 와도 화면 갱신이 보장됨.
            if (key == "latest" || key == "updated_at") {
                val value = prefs.getFloat("latest", -1f)
                Log.d("WearGlucose", "SharedPreferences 변경 ($key): $value")
                if (value >= 0) runOnUiThread { stepsValue = value.toDouble() }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 앱 시작 시 저장된 최신값 로드
        val initial = getSharedPreferences("glucose", Context.MODE_PRIVATE).getFloat("latest", -1f)
        if (initial >= 0) stepsValue = initial.toDouble()

        // Data Layer 연결 확인
        lifecycleScope.launch {
            try {
                val nodes =
                    com.google.android.gms.wearable.Wearable
                        .getNodeClient(this@MainActivity).connectedNodes.await()
                Log.d("WearGlucose", "워치에서 보이는 노드 ${nodes.size}개: ${nodes.map { it.displayName }}")
            } catch (e: Exception) {
                Log.e("WearGlucose", "노드 조회 실패", e)
            }
        }

        setContent {
            GlucoFitTheme {
                StepsScreen(stepsValue)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val prefs = getSharedPreferences("glucose", Context.MODE_PRIVATE)
        prefs.registerOnSharedPreferenceChangeListener(prefListener)
        // RESUME 시점에 마지막 저장된 값 강제 반영. 워치 앱이 PAUSED 인 동안 도착한
        // 메시지는 listener 가 unregister 라 못 받았어도, 앱 다시 여는 순간 최신값이 보임.
        val latest = prefs.getFloat("latest", -1f)
        if (latest >= 0) stepsValue = latest.toDouble()
    }

    override fun onPause() {
        super.onPause()
        getSharedPreferences("glucose", Context.MODE_PRIVATE)
            .unregisterOnSharedPreferenceChangeListener(prefListener)
    }
}

@Composable
fun StepsScreen(value: Double?) {
    // 걸음수 임계치: < 5000 부족 / 5000~9999 진행 중 / >= 10000 목표 달성 (10K 보 기준)
    val bgColor =
        when {
            value == null -> Color(0xFF1A1A2E)
            value < 5000 -> Color(0xFF2A1A1A) // 부족 - 어두운 적갈색
            value < 10000 -> Color(0xFF1A2A3A) // 진행 중 - 어두운 청색
            else -> Color(0xFF003300) // 목표 달성 - 어두운 녹색
        }
    val valueColor =
        when {
            value == null -> Color.Gray
            value < 5000 -> Color(0xFFFFAB40)
            value < 10000 -> Color(0xFF64B5F6)
            else -> Color(0xFF69F0AE)
        }
    val statusText =
        when {
            value == null -> "대기 중"
            value < 5000 -> "조금 더 걸어요"
            value < 10000 -> "진행 중"
            else -> "목표 달성!"
        }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(bgColor),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = if (value != null) "${value.toInt()}" else "--",
            color = valueColor,
            fontSize = 48.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = "보",
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 14.sp,
        )
        Text(
            text = statusText,
            color = valueColor.copy(alpha = 0.8f),
            fontSize = 13.sp,
            modifier = androidx.compose.ui.Modifier,
        )
    }
}
