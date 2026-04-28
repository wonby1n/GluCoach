package com.ssafy.s309.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
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
 * @param timeLabels X축 시간 라벨 (예: ["08:00","10:00","12:00","14:00"]). 빈 리스트면 렌더 생략.
 */
@Composable
fun GlucoseChartCard(
    readings: List<GlucoseReading>,
    range: GlucoseRange,
    meals: List<MealEvent>,
    modifier: Modifier = Modifier,
    hoursLabel: String = "최근 6시간",
    mealPinIcon: (@Composable () -> Unit)? = null,
    timeLabels: List<String> = emptyList(),
    onClick: (() -> Unit)? = null,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .shadow(3.dp, RoundedCornerShape(GlucoachCorner.card))
                .clip(RoundedCornerShape(GlucoachCorner.card))
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
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

        if (readings.isEmpty()) {
            DeviceNotConnected()
        } else {
            GlucoseChartBody(
                readings = readings,
                range = range,
                meals = meals,
                mealPinIcon = mealPinIcon,
            )
            if (timeLabels.isNotEmpty()) {
                GlucoseChartTimeAxis(labels = timeLabels)
            }
        }
    }
}

@Composable
private fun DeviceNotConnected() {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(CHART_BODY_HEIGHT + 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier =
                Modifier
                    .size(40.dp)
                    .background(GlucoachColors.Background, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.Bluetooth,
                contentDescription = null,
                tint = GlucoachColors.Primary,
                modifier = Modifier.size(22.dp),
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "기기가 연동되어 있지 않아요",
            color = GlucoachColors.TextPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "CGM 기기를 연동하면 혈당 흐름을 확인할 수 있어요",
            color = GlucoachColors.TextSecondary,
            fontSize = 11.sp,
            textAlign = TextAlign.Center,
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

    // Y 라벨 텍스트 높이를 실측 후 절반을 수직 정렬 보정값으로 사용.
    // 상수 대신 실측을 쓰는 이유: 시스템 폰트 스케일(1.3x/1.5x)이 적용돼도 회색 박스
    // 경계선과 라벨이 어긋나지 않도록 보장하기 위함.
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val yLabelStyle = remember { TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold) }
    val yLabelHalfHeight =
        remember(textMeasurer, yLabelStyle, density) {
            with(density) {
                (textMeasurer.measure(text = "0", style = yLabelStyle).size.height / 2).toDp()
            }
        }

    BoxWithConstraints(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(CHART_BODY_HEIGHT),
    ) {
        // Canvas 와 동일한 수직 패딩을 사용해 Y라벨을 회색 박스 상/하단에 정렬한다.
        val canvasInnerHeight = maxHeight - CHART_TOP_PADDING - CHART_BOTTOM_PADDING
        val yRange = (yMax - yMin).toFloat().coerceAtLeast(1f)
        val topLabelOffset =
            CHART_TOP_PADDING +
                canvasInnerHeight * ((yMax - range.maxMgDl).toFloat() / yRange) -
                yLabelHalfHeight
        val bottomLabelOffset =
            CHART_TOP_PADDING +
                canvasInnerHeight * ((yMax - range.minMgDl).toFloat() / yRange) -
                yLabelHalfHeight

        Text(
            text = "${range.maxMgDl}",
            color = GlucoachColors.TextPrimary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            modifier =
                Modifier
                    .offset(y = topLabelOffset)
                    .padding(start = 4.dp),
        )
        Text(
            text = "${range.minMgDl}",
            color = GlucoachColors.TextPrimary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            modifier =
                Modifier
                    .offset(y = bottomLabelOffset)
                    .padding(start = 4.dp),
        )

        Canvas(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(CHART_BODY_HEIGHT)
                    .padding(
                        start = CHART_X_START_PADDING,
                        end = CHART_X_END_PADDING,
                        top = CHART_TOP_PADDING,
                        bottom = CHART_BOTTOM_PADDING,
                    ),
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

        // 5) 식사 핀 — 그래프 하단(시간 축 바로 위)에 배치.
        //    matchParentSize 로 차트 본문 전체를 덮은 뒤 verticalBias=1f 로 하단 정렬,
        //    Canvas 와 동일한 수평 패딩을 적용해 x 좌표가 그래프 x축과 일치한다.
        meals.forEach { meal ->
            val ratio =
                ((meal.timestampMillis - minTime).toFloat() / timeSpan.toFloat())
                    .coerceIn(0f, 1f)
            MealPin(
                pinIcon = mealPinIcon,
                horizontalBias = ratio,
                modifier = Modifier.matchParentSize(),
            )
        }
    }
}

@Composable
private fun MealPin(
    pinIcon: (@Composable () -> Unit)?,
    horizontalBias: Float,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier.padding(
                start = CHART_X_START_PADDING,
                end = CHART_X_END_PADDING,
            ),
    ) {
        Box(
            modifier =
                Modifier
                    .align(alignmentFor(horizontalBias))
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
                .padding(
                    start = CHART_X_START_PADDING,
                    end = CHART_X_END_PADDING,
                    top = 4.dp,
                ),
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
private val CHART_TOP_PADDING = 4.dp
private val CHART_BOTTOM_PADDING = 8.dp
private val CHART_X_START_PADDING = 28.dp
private val CHART_X_END_PADDING = 8.dp
private val MEAL_PIN_SIZE = 28.dp
