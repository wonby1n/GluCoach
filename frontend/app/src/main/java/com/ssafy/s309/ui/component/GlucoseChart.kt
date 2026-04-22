package com.ssafy.s309.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.s309.data.model.GlucoseRange
import com.ssafy.s309.data.model.GlucoseReading
import com.ssafy.s309.data.model.MealEvent
import com.ssafy.s309.ui.theme.GlucoachColors
import com.ssafy.s309.ui.theme.GlucoachCorner
import com.ssafy.s309.ui.theme.GlucoachSpacing
import kotlin.math.max
import kotlin.math.min

/**
 * "오늘 혈당 흐름" 카드 전체 컴포넌트.
 *
 * @param readings 시간순 정렬된 혈당 시리즈
 * @param range 사용자 목표 범위 (그래프 내부 회색 박스)
 * @param meals 식사 이벤트 (밥그릇 핀으로 표시)
 * @param hoursLabel 제목 우측 라벨 (예: "최근 6시간")
 * @param mealPinIcon 밥그릇 아이콘 asset — null 이면 원형 자리 표시자로 렌더
 */
@Composable
fun GlucoseChartCard(
    readings: List<GlucoseReading>,
    range: GlucoseRange,
    meals: List<MealEvent>,
    modifier: Modifier = Modifier,
    hoursLabel: String = "최근 6시간",
    mealPinIcon: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .shadow(3.dp, RoundedCornerShape(GlucoachCorner.card))
                .clip(RoundedCornerShape(GlucoachCorner.card))
                .background(GlucoachColors.Surface)
                .padding(start = 22.dp, end = 16.dp, top = 14.dp, bottom = 12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "오늘 혈당 흐름",
                color = GlucoachColors.TextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = hoursLabel,
                color = GlucoachColors.Primary,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
            )
        }

        Spacer(modifier = Modifier.height(GlucoachSpacing.sm))

        GlucoseChartBody(
            readings = readings,
            range = range,
            meals = meals,
            mealPinIcon = mealPinIcon,
        )
    }
}

@Composable
private fun GlucoseChartBody(
    readings: List<GlucoseReading>,
    range: GlucoseRange,
    meals: List<MealEvent>,
    mealPinIcon: (@Composable () -> Unit)?,
) {
    // 축 라벨 계산
    val yMax = max(range.maxMgDl, readings.maxOfOrNull { it.valueMgDl } ?: range.maxMgDl) + 20
    val yMin = min(range.minMgDl, readings.minOfOrNull { it.valueMgDl } ?: range.minMgDl) - 20

    val minTime = readings.firstOrNull()?.timestampMillis ?: 0L
    val maxTime = readings.lastOrNull()?.timestampMillis ?: 1L
    val timeSpan = (maxTime - minTime).coerceAtLeast(1L)

    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(CHART_BODY_HEIGHT),
    ) {
        // Y축 라벨 (상단/하단)
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "${range.maxMgDl}",
                color = GlucoachColors.TextPrimary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 4.dp),
            )
            Text(
                text = "${range.minMgDl}",
                color = GlucoachColors.TextPrimary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
            )
        }

        Canvas(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(CHART_BODY_HEIGHT)
                    .padding(start = 28.dp, end = 8.dp, top = 4.dp, bottom = 8.dp),
        ) {
            val chartWidth = size.width
            val chartHeight = size.height

            fun yFor(value: Int): Float {
                val ratio = (value - yMin).toFloat() / (yMax - yMin).toFloat()
                return chartHeight * (1f - ratio.coerceIn(0f, 1f))
            }

            fun xFor(millis: Long): Float {
                val ratio = (millis - minTime).toFloat() / timeSpan.toFloat()
                return chartWidth * ratio.coerceIn(0f, 1f)
            }

            // 1) 목표 범위 회색 박스
            val rangeTop = yFor(range.maxMgDl)
            val rangeBottom = yFor(range.minMgDl)
            drawRect(
                color = GlucoachColors.RangeBox,
                topLeft = Offset(0f, rangeTop),
                size = Size(chartWidth, rangeBottom - rangeTop),
            )

            // 2) 상/하한 점선
            val dash = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))
            drawLine(
                color = GlucoachColors.ChartGrid,
                start = Offset(0f, rangeTop),
                end = Offset(chartWidth, rangeTop),
                strokeWidth = 1f,
                pathEffect = dash,
            )
            drawLine(
                color = GlucoachColors.ChartGrid,
                start = Offset(0f, rangeBottom),
                end = Offset(chartWidth, rangeBottom),
                strokeWidth = 1f,
                pathEffect = dash,
            )

            // 3) 혈당 라인 (부드러운 path)
            if (readings.size >= 2) {
                val path = Path()
                readings.forEachIndexed { index, reading ->
                    val x = xFor(reading.timestampMillis)
                    val y = yFor(reading.valueMgDl)
                    if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                drawPath(
                    path = path,
                    color = GlucoachColors.Primary,
                    style = Stroke(width = 3f),
                )
            }

            // 4) 마지막 포인트 강조 원
            readings.lastOrNull()?.let { last ->
                val cx = xFor(last.timestampMillis)
                val cy = yFor(last.valueMgDl)
                drawCircle(
                    color = GlucoachColors.Primary,
                    radius = 6f,
                    center = Offset(cx, cy),
                    style = Stroke(width = 2f),
                )
            }
        }

        // 5) 식사 핀 (밥그릇 아이콘) - Canvas 위에 Composable 로 겹쳐둔다.
        //    실제 asset 이 주입되면 mealPinIcon 으로 렌더.
        meals.forEach { meal ->
            val ratio = (meal.timestampMillis - minTime).toFloat() / timeSpan.toFloat()
            val clamped = ratio.coerceIn(0f, 1f)
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.BottomStart,
            ) {
                // 좌측 Y라벨 padding(28dp) + 오른쪽 padding(8dp)을 고려한 수평 비율 계산은
                // 복잡도를 낮추기 위해 근사치로 처리. 정확 매칭은 BoxWithConstraints 로 확장 가능.
                MealPin(
                    pinIcon = mealPinIcon,
                    horizontalBias = clamped,
                )
            }
        }
    }
}

@Composable
private fun MealPin(
    pinIcon: (@Composable () -> Unit)?,
    horizontalBias: Float,
) {
    Box(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box(
            modifier =
                Modifier
                    .align(
                        alignmentFor(horizontalBias),
                    )
                    .padding(bottom = GlucoachSpacing.xs)
                    .size(MEAL_PIN_SIZE),
            contentAlignment = Alignment.Center,
        ) {
            if (pinIcon != null) {
                pinIcon()
            } else {
                // 자리 표시자: asset 주입 전에도 위치 확인 가능
                Box(
                    modifier =
                        Modifier
                            .size(16.dp)
                            .clip(RoundedCornerShape(50))
                            .background(GlucoachColors.TextSecondary),
                )
            }
        }
    }
}

/**
 * 0f..1f 를 Compose Alignment 로 근사 변환한다.
 * Canvas 와 정확히 정렬하려면 BoxWithConstraints + dp 오프셋으로 교체 가능.
 */
private fun alignmentFor(bias: Float): Alignment {
    val clamped = bias.coerceIn(0f, 1f)
    val horizontal = -1f + 2f * clamped
    return androidx.compose.ui.BiasAlignment(horizontalBias = horizontal, verticalBias = 1f)
}

/** X축 시간 라벨 행 (예: 08:00 / 10:00 / 12:00 / 14:00) */
@Composable
fun GlucoseChartTimeAxis(
    labels: List<String>,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(start = 28.dp, end = 8.dp, top = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        labels.forEach { label ->
            Text(
                text = label,
                color = GlucoachColors.TextPrimary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

private val CHART_BODY_HEIGHT = 110.dp
private val MEAL_PIN_SIZE = 28.dp
