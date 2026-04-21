package com.ssafy.s309.ui.screen

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.s309.ui.theme.Primary
import java.util.Calendar

// TODO: 백엔드 연동 후 실제 API 데이터로 교체
private val dummyGlucoseData =
    listOf(
        160f, 155f, 158f, 162f, 160f, 165f, 163f,
        160f, 158f, 162f, 165f, 180f, 195f, 200f,
        198f, 192f, 188f, 185f, 180f, 175f, 170f,
        165f, 162f, 158f, 155f, 152f, 150f,
    )

private val dummyTimeLabels = listOf("12:25", "12:30", "현재")
private val yAxisLabels = listOf(300, 250, 200, 150, 100, 50, 0)
private val dayLabels = listOf("월", "화", "수", "목", "금", "토", "일")

private fun getCurrentWeekDates(): Pair<List<Int>, Int> {
    val cal = Calendar.getInstance()
    val todayDow = cal.get(Calendar.DAY_OF_WEEK) // 1=일, 2=월, ..., 7=토
    // 월요일(2) 기준으로 이번 주 월요일로 이동
    val offsetToMonday = if (todayDow == Calendar.SUNDAY) -6 else -(todayDow - Calendar.MONDAY)
    cal.add(Calendar.DAY_OF_MONTH, offsetToMonday)

    val dates =
        (0..6).map { i ->
            val d = cal.get(Calendar.DAY_OF_MONTH)
            cal.add(Calendar.DAY_OF_MONTH, 1)
            d
        }
    // 오늘의 index (0=월 ~ 6=일)
    val todayIndex = if (todayDow == Calendar.SUNDAY) 6 else todayDow - Calendar.MONDAY
    return Pair(dates, todayIndex)
}

@Composable
fun GraphScreen() {
    val (weekDates, todayIndex) = remember { getCurrentWeekDates() }
    var selectedDay by remember { mutableIntStateOf(todayIndex) }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(Color(0xFFF2F4F5))
                .padding(horizontal = 20.dp, vertical = 48.dp),
    ) {
        // 헤더
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "혈당 그래프 추이",
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF333333),
            )
            Icon(
                imageVector = Icons.Outlined.Notifications,
                contentDescription = "알림",
                tint = Primary,
                modifier = Modifier.size(24.dp),
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // 카드
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(Color.White, RoundedCornerShape(16.dp))
                    .border(1.dp, Color(0xFFE0F0F4), RoundedCornerShape(16.dp))
                    .padding(horizontal = 16.dp, vertical = 20.dp),
        ) {
            // 현재 혈당 수치
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = "82",
                    fontSize = 52.sp,
                    fontWeight = FontWeight.Bold,
                    color = Primary,
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "mg/dL",
                    fontSize = 16.sp,
                    color = Color(0xFF888888),
                    modifier = Modifier.padding(bottom = 10.dp),
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 요일 선택
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                dayLabels.forEachIndexed { index, day ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.clickable { selectedDay = index },
                    ) {
                        Text(
                            text = day,
                            fontSize = 12.sp,
                            color = if (index == selectedDay) Primary else Color(0xFF999999),
                            fontWeight = if (index == selectedDay) FontWeight.Bold else FontWeight.Normal,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier =
                                Modifier
                                    .size(28.dp)
                                    .background(
                                        if (index == selectedDay) Primary else Color.Transparent,
                                        CircleShape,
                                    ),
                        ) {
                            Text(
                                text = "${weekDates[index]}",
                                fontSize = 13.sp,
                                color = if (index == selectedDay) Color.White else Color(0xFF999999),
                                fontWeight = if (index == selectedDay) FontWeight.Bold else FontWeight.Normal,
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 최고/최저
            Row {
                Text(text = "최고 ", fontSize = 14.sp, color = Color(0xFF666666))
                Text(text = "92", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF333333))
                Spacer(modifier = Modifier.width(16.dp))
                Text(text = "최저 ", fontSize = 14.sp, color = Color(0xFF666666))
                Text(text = "63", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Primary)
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 그래프 + Y축
            Row(modifier = Modifier.fillMaxWidth()) {
                // Y축 레이블
                Column(
                    modifier =
                        Modifier
                            .width(36.dp)
                            .height(220.dp),
                    verticalArrangement = Arrangement.SpaceBetween,
                ) {
                    yAxisLabels.forEach { label ->
                        Text(
                            text = "$label",
                            fontSize = 10.sp,
                            color = Color(0xFFBBBBBB),
                        )
                    }
                }

                // 라인 차트
                Canvas(
                    modifier =
                        Modifier
                            .weight(1f)
                            .height(220.dp),
                ) {
                    val w = size.width
                    val h = size.height
                    val yMin = 0f
                    val yMax = 300f
                    val data = dummyGlucoseData
                    val stepX = w / (data.size - 1)

                    // 가로 점선
                    yAxisLabels.forEach { label ->
                        val y = h - (label - yMin) / (yMax - yMin) * h
                        drawLine(
                            color = Color(0xFFEEEEEE),
                            start = Offset(0f, y),
                            end = Offset(w, y),
                            strokeWidth = 1f,
                            pathEffect =
                                androidx.compose.ui.graphics.PathEffect.dashPathEffect(
                                    floatArrayOf(6f, 6f),
                                ),
                        )
                    }

                    // 라인
                    val path = Path()
                    data.forEachIndexed { i, value ->
                        val x = i * stepX
                        val y = h - (value - yMin) / (yMax - yMin) * h
                        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    }
                    drawPath(
                        path = path,
                        color = Primary,
                        style =
                            Stroke(
                                width = 3f,
                                cap = StrokeCap.Round,
                                join = StrokeJoin.Round,
                            ),
                    )

                    // 피크 포인트 (빨간 점)
                    val peakIndex = data.indexOf(data.max())
                    val peakX = peakIndex * stepX
                    val peakY = h - (data[peakIndex] - yMin) / (yMax - yMin) * h
                    drawCircle(color = Color(0xFFE96A6A), radius = 8f, center = Offset(peakX, peakY))
                    drawCircle(color = Color.White, radius = 4f, center = Offset(peakX, peakY))
                }
            }

            // X축 레이블
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 36.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                dummyTimeLabels.forEach { label ->
                    Text(text = label, fontSize = 10.sp, color = Color(0xFFBBBBBB))
                }
            }
        }
    }
}
