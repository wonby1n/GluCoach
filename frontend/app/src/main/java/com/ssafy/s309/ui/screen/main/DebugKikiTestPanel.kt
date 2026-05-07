// [DEBUG_KIKI_TEST] 이 파일 전체가 디버그 전용입니다. 배포 전 삭제하세요.
// 검색 태그: DEBUG_KIKI_TEST
package com.ssafy.s309.ui.screen.main

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun DebugKikiTestPanel(onSet: (Int, Float) -> Unit) {
    var expanded by remember { mutableStateOf(false) }

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .shadow(2.dp, RoundedCornerShape(12.dp))
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFFFFF3E0))
                .clickable { expanded = !expanded }
                .padding(12.dp),
    ) {
        Text(
            text = if (expanded) "DEBUG: 키키 테스트 (닫기)" else "DEBUG: 키키 테스트 (열기)",
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFFE65100),
        )

        if (expanded) {
            Spacer(modifier = Modifier.height(8.dp))
            val presets =
                listOf(
                    "정상 120" to Pair(120, 0f),
                    "약한+안정 160" to Pair(160, 0f),
                    "약한+급상승 160" to Pair(160, 4f),
                    "약한+서서히 170" to Pair(170, 2f),
                    "확연한+안정 220" to Pair(220, 0f),
                    "확연한+급상승 200" to Pair(200, 4f),
                    "확연한+서서히 210" to Pair(210, 2f),
                    "심각+안정 300" to Pair(300, 0f),
                    "심각+급상승 280" to Pair(280, 5f),
                    "심각+서서히 270" to Pair(270, 1.5f),
                )
            presets.chunked(2).forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    row.forEach { (label, params) ->
                        Text(
                            text = label,
                            fontSize = 11.sp,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            modifier =
                                Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color(0xFFFF6D00))
                                    .clickable { onSet(params.first, params.second) }
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
            }
        }
    }
}
