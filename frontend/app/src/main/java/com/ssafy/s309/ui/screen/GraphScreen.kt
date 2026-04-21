package com.ssafy.s309.ui.screen

import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.s309.ui.theme.Primary
import java.util.Calendar

// TODO: 백엔드 연동 후 실제 API 데이터로 교체
// 5분 간격 데이터 (8개 = 35분치)
private val dummyGlucoseData =
    listOf(
        98f,
        112f,
        145f,
        188f,
        210f,
        195f,
        172f,
        150f,
    )

// 5분 간격 시간 레이블 (마지막은 현재)
private val dummyTimeLabels = listOf("12:00", "12:05", "12:10", "12:15", "12:20", "12:25", "12:30", "현재")

// Y축: 혈당 실용 범위만
private val yAxisValues = listOf(250, 180, 140, 70, 0)
private val dayLabels = listOf("월", "화", "수", "목", "금", "토", "일")

private val colorNormal = Color(0xFF71C1D2)
private val colorWarning = Color(0xFFF6B44C)
private val colorDanger = Color(0xFFE96A6A)

private fun glucoseColor(value: Float) =
    when {
        value < 70 || value > 180 -> colorDanger
        value > 140 -> colorWarning
        else -> colorNormal
    }

private fun getCurrentWeekDates(): Pair<List<Int>, Int> {
    val cal = Calendar.getInstance()
    val todayDow = cal.get(Calendar.DAY_OF_WEEK)
    val offsetToMonday = if (todayDow == Calendar.SUNDAY) -6 else -(todayDow - Calendar.MONDAY)
    cal.add(Calendar.DAY_OF_MONTH, offsetToMonday)
    val dates =
        (0..6).map {
            val d = cal.get(Calendar.DAY_OF_MONTH)
            cal.add(Calendar.DAY_OF_MONTH, 1)
            d
        }
    val todayIndex = if (todayDow == Calendar.SUNDAY) 6 else todayDow - Calendar.MONDAY
    return Pair(dates, todayIndex)
}

@Composable
fun GraphScreen() {
    val (weekDates, todayIndex) = remember { getCurrentWeekDates() }
    var selectedDay by remember { mutableIntStateOf(todayIndex) }
    val currentValue = dummyGlucoseData.last()
    val currentColor = glucoseColor(currentValue)

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(Color(0xFFF2F4F5))
                .padding(horizontal = 24.dp, vertical = 52.dp),
    ) {
        // 헤더
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "혈당 그래프 추이",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF333333),
            )
            Icon(
                imageVector = Icons.Outlined.Notifications,
                contentDescription = "알림",
                tint = Primary,
                modifier = Modifier.size(28.dp),
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // 혈당 수치 + 상태 뱃지 (카드 밖)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = "${currentValue.toInt()}",
                fontSize = 80.sp,
                fontWeight = FontWeight.ExtraBold,
                color = currentColor,
                lineHeight = 80.sp,
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.padding(bottom = 14.dp)) {
                Text(text = "mg/dL", fontSize = 18.sp, color = Color(0xFF888888))
                Spacer(modifier = Modifier.height(4.dp))
                Box(
                    modifier =
                        Modifier
                            .background(currentColor.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                ) {
                    Text(
                        text =
                            when {
                                currentValue < 70 -> "저혈당"
                                currentValue > 180 -> "고혈당"
                                currentValue > 140 -> "주의"
                                else -> "정상"
                            },
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = currentColor,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // 카드
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .shadow(8.dp, RoundedCornerShape(20.dp))
                    .background(Color.White, RoundedCornerShape(20.dp))
                    .padding(horizontal = 20.dp, vertical = 24.dp),
        ) {
            // 요일 선택 + 오늘 버튼
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    dayLabels.forEachIndexed { index, day ->
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.clickable { selectedDay = index },
                        ) {
                            Text(
                                text = day,
                                fontSize = 13.sp,
                                color = if (index == selectedDay) Primary else Color(0xFFBBBBBB),
                                fontWeight = if (index == selectedDay) FontWeight.Bold else FontWeight.Normal,
                            )
                            Spacer(modifier = Modifier.height(5.dp))
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier =
                                    Modifier
                                        .size(30.dp)
                                        .background(
                                            if (index == selectedDay) Primary else Color.Transparent,
                                            CircleShape,
                                        ),
                            ) {
                                Text(
                                    text = "${weekDates[index]}",
                                    fontSize = 13.sp,
                                    color = if (index == selectedDay) Color.White else Color(0xFFBBBBBB),
                                    fontWeight = if (index == selectedDay) FontWeight.Bold else FontWeight.Normal,
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.width(10.dp))

                Box(
                    contentAlignment = Alignment.Center,
                    modifier =
                        Modifier
                            .background(
                                if (selectedDay == todayIndex) Primary else Color(0xFFE8F7FA),
                                RoundedCornerShape(20.dp),
                            )
                            .clickable { selectedDay = todayIndex }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Text(
                        text = "오늘",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (selectedDay == todayIndex) Color.White else Primary,
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 최고/최저
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier =
                        Modifier
                            .size(8.dp)
                            .background(colorDanger, CircleShape),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = "최고 ", fontSize = 15.sp, color = Color(0xFF888888))
                Text(text = "92", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color(0xFF333333))
                Spacer(modifier = Modifier.width(16.dp))
                Box(
                    modifier =
                        Modifier
                            .size(8.dp)
                            .background(Primary, CircleShape),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = "최저 ", fontSize = 15.sp, color = Color(0xFF888888))
                Text(text = "63", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color(0xFF333333))
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 그래프 (Y축 레이블 포함, 캔버스 안에 직접 그림)
            Canvas(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(300.dp),
            ) {
                val w = size.width
                val h = size.height
                val yMin = 0f
                val yMax = 250f
                val leftPad = 48f // Y축 레이블 공간
                val chartW = w - leftPad

                val data = dummyGlucoseData
                val n = data.size
                val stepX = chartW / (n - 1)

                fun xOf(i: Int) = leftPad + i * stepX

                fun yOf(v: Float) = h - (v - yMin) / (yMax - yMin) * h

                // 정상 범위 배경 (70~140)
                val normalTop = yOf(140f)
                val normalBot = yOf(70f)
                drawRect(
                    color = Primary.copy(alpha = 0.07f),
                    topLeft = Offset(leftPad, normalTop),
                    size = androidx.compose.ui.geometry.Size(chartW, normalBot - normalTop),
                )

                // Y축 레이블 + 가로 점선
                val textPaint =
                    Paint().asFrameworkPaint().apply {
                        isAntiAlias = true
                        textSize = 28f
                        color = android.graphics.Color.parseColor("#AAAAAA")
                    }
                yAxisValues.forEach { label ->
                    val y = yOf(label.toFloat())
                    // 점선
                    drawLine(
                        color = Color(0xFFEEEEEE),
                        start = Offset(leftPad, y),
                        end = Offset(w, y),
                        strokeWidth = 1.5f,
                        pathEffect =
                            androidx.compose.ui.graphics.PathEffect.dashPathEffect(
                                floatArrayOf(10f, 8f),
                            ),
                    )
                    // 레이블 (캔버스 왼쪽에 직접 그리기)
                    drawIntoCanvas {
                        it.nativeCanvas.drawText(
                            "$label",
                            0f,
                            y + 10f,
                            textPaint,
                        )
                    }
                }

                // 베지어 곡선 경로
                val linePath = Path()
                linePath.moveTo(xOf(0), yOf(data[0]))
                for (i in 1 until n) {
                    val cpX = (xOf(i - 1) + xOf(i)) / 2f
                    linePath.cubicTo(
                        cpX,
                        yOf(data[i - 1]),
                        cpX,
                        yOf(data[i]),
                        xOf(i),
                        yOf(data[i]),
                    )
                }

                // 그라데이션 채우기
                val fillPath =
                    Path().apply {
                        addPath(linePath)
                        lineTo(xOf(n - 1), h)
                        lineTo(leftPad, h)
                        close()
                    }
                drawPath(
                    path = fillPath,
                    brush =
                        Brush.verticalGradient(
                            colors = listOf(Primary.copy(alpha = 0.35f), Primary.copy(alpha = 0f)),
                            startY = 0f,
                            endY = h,
                        ),
                )

                // 라인
                drawPath(
                    path = linePath,
                    color = Primary,
                    style = Stroke(width = 7f, cap = StrokeCap.Round, join = StrokeJoin.Round),
                )

                // 현재(마지막) 포인트 — 발광 효과
                val lastX = xOf(n - 1)
                val lastY = yOf(data.last())
                drawCircle(color = Primary.copy(alpha = 0.15f), radius = 22f, center = Offset(lastX, lastY))
                drawCircle(color = Primary.copy(alpha = 0.35f), radius = 14f, center = Offset(lastX, lastY))
                drawCircle(color = Primary, radius = 8f, center = Offset(lastX, lastY))
                drawCircle(color = Color.White, radius = 4f, center = Offset(lastX, lastY))

                // 피크 포인트
                val peakIdx = data.indexOf(data.max())
                val peakX = xOf(peakIdx)
                val peakY = yOf(data[peakIdx])
                drawCircle(color = colorDanger.copy(alpha = 0.2f), radius = 18f, center = Offset(peakX, peakY))
                drawCircle(color = colorDanger, radius = 8f, center = Offset(peakX, peakY))
                drawCircle(color = Color.White, radius = 4f, center = Offset(peakX, peakY))

                // X축 시간 레이블
                val xTextPaint =
                    Paint().asFrameworkPaint().apply {
                        isAntiAlias = true
                        textSize = 26f
                        color = android.graphics.Color.parseColor("#AAAAAA")
                        textAlign = android.graphics.Paint.Align.CENTER
                    }
                dummyTimeLabels.forEachIndexed { i, label ->
                    drawIntoCanvas {
                        it.nativeCanvas.drawText(label, xOf(i), h, xTextPaint)
                    }
                }
            }
        }
    }
}
