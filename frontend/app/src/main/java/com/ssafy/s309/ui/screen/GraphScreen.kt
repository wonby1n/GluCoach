package com.ssafy.s309.ui.screen

import android.content.res.Configuration
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ssafy.s309.data.ble.BleConnectionState
import com.ssafy.s309.feature.glucofit.glucose.GlucoseSimulator
import com.ssafy.s309.ui.screen.ble.BleViewModel
import com.ssafy.s309.ui.screen.main.MainViewModel
import com.ssafy.s309.ui.theme.Primary
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

private val dummyGlucoseData = listOf(98f, 112f, 145f, 188f, 210f, 195f, 172f, 150f)
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

private fun getKrTimeLabels(
    count: Int = 8,
    intervalMinutes: Int = 5,
): List<String> {
    val sdf = SimpleDateFormat("HH:mm", Locale.KOREA)
    sdf.timeZone = TimeZone.getTimeZone("Asia/Seoul")
    val cal = Calendar.getInstance(TimeZone.getTimeZone("Asia/Seoul"))
    return (count - 1 downTo 0).map { i ->
        if (i == 0) {
            "현재"
        } else {
            val c = cal.clone() as Calendar
            c.add(Calendar.MINUTE, -(i * intervalMinutes))
            sdf.format(c.time)
        }
    }
}

private fun getCurrentWeekDates(): Pair<List<Int>, Int> {
    val cal = Calendar.getInstance(TimeZone.getTimeZone("Asia/Seoul"))
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
fun GraphScreen(
    onBack: () -> Unit = {},
    onNavigateToBle: () -> Unit = {},
    bleViewModel: BleViewModel = hiltViewModel(),
    mainViewModel: MainViewModel = hiltViewModel(),
) {
    val connectionState by bleViewModel.connectionState.collectAsStateWithLifecycle()
    val glucoseReadings by bleViewModel.glucoseReadings.collectAsStateWithLifecycle()
    val isDeviceConnected = connectionState is BleConnectionState.Connected
    val mainUiState by mainViewModel.uiState.collectAsStateWithLifecycle()
    val diabetesType = mainUiState.diabetesType

    // 시뮬레이터 롤링 버퍼 (BLE 미연결 시 사용)
    val context = LocalContext.current
    var simHistory by remember { mutableStateOf(dummyGlucoseData) }
    val simGlucose by GlucoseSimulator.glucoseState.collectAsStateWithLifecycle()

    DisposableEffect(diabetesType) {
        GlucoseSimulator.stop()
        GlucoseSimulator.start(context, diabetesType)
        onDispose { }
    }

    LaunchedEffect(simGlucose) {
        val v = simGlucose?.toFloat() ?: return@LaunchedEffect
        simHistory = (simHistory + v).takeLast(8)
    }

    // BLE 실데이터 우선, 없으면 시뮬레이터 데이터
    val displayData =
        if (glucoseReadings.isNotEmpty()) {
            glucoseReadings.map { it.valueMgDl.toFloat() }
        } else {
            simHistory
        }

    val (weekDates, todayIndex) = remember { getCurrentWeekDates() }
    var selectedDay by remember { mutableIntStateOf(todayIndex) }
    val currentValue =
        glucoseReadings.lastOrNull()?.valueMgDl?.toFloat()
            ?: simGlucose?.toFloat()
            ?: 0f
    val currentColor = glucoseColor(currentValue)
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    // 항상 그래프 표시 (BLE 연결 여부와 무관하게 시뮬레이터가 데이터 제공)
    val showGraph = true

    if (isLandscape) {
        GraphScreenLandscape(
            weekDates = weekDates,
            todayIndex = todayIndex,
            selectedDay = selectedDay,
            onDaySelect = { selectedDay = it },
            currentValue = currentValue,
            currentColor = currentColor,
            onBack = onBack,
            onNavigateToBle = onNavigateToBle,
            isDeviceConnected = showGraph,
            chartData = displayData,
        )
    } else {
        GraphScreenPortrait(
            weekDates = weekDates,
            todayIndex = todayIndex,
            selectedDay = selectedDay,
            onDaySelect = { selectedDay = it },
            currentValue = currentValue,
            currentColor = currentColor,
            onBack = onBack,
            onNavigateToBle = onNavigateToBle,
            isDeviceConnected = showGraph,
            chartData = displayData,
        )
    }
}

// ── 세로 모드 ────────────────────────────────────────────────────────────────

@Composable
private fun GraphScreenPortrait(
    weekDates: List<Int>,
    todayIndex: Int,
    selectedDay: Int,
    onDaySelect: (Int) -> Unit,
    currentValue: Float,
    currentColor: Color,
    onBack: () -> Unit,
    onNavigateToBle: () -> Unit = {},
    isDeviceConnected: Boolean = false,
    chartData: List<Float> = dummyGlucoseData,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(Color(0xFFF2F4F5)),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 52.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = "뒤로가기",
                    tint = Color(0xFF333333),
                    modifier = Modifier.size(28.dp).clickable(onClick = onBack),
                )
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

            if (!isDeviceConnected) {
                DeviceNotConnectedPlaceholder(onNavigateToBle = onNavigateToBle)
            } else {
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
                        GlucoseStatusBadge(currentValue, currentColor)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                GlucoseRangeLegend()

                Spacer(modifier = Modifier.height(16.dp))

                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .shadow(8.dp, RoundedCornerShape(20.dp))
                            .background(Color.White, RoundedCornerShape(20.dp))
                            .padding(horizontal = 20.dp, vertical = 24.dp),
                ) {
                    DaySelector(
                        weekDates = weekDates,
                        todayIndex = todayIndex,
                        selectedDay = selectedDay,
                        onDaySelect = onDaySelect,
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    HighLowRow(chartData = chartData)

                    Spacer(modifier = Modifier.height(16.dp))

                    GlucoseCanvas(modifier = Modifier.fillMaxWidth().height(300.dp), data = chartData)
                }
            }
        }
    }
}

// ── 가로 모드 ────────────────────────────────────────────────────────────────

@Composable
private fun GraphScreenLandscape(
    weekDates: List<Int>,
    todayIndex: Int,
    selectedDay: Int,
    onDaySelect: (Int) -> Unit,
    currentValue: Float,
    currentColor: Color,
    onBack: () -> Unit,
    onNavigateToBle: () -> Unit = {},
    isDeviceConnected: Boolean = false,
    chartData: List<Float> = dummyGlucoseData,
) {
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(Color(0xFFF2F4F5))
                .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .shadow(8.dp, RoundedCornerShape(20.dp))
                    .background(Color.White, RoundedCornerShape(20.dp)),
            contentAlignment = Alignment.Center,
        ) {
            if (!isDeviceConnected) {
                DeviceNotConnectedPlaceholder(onNavigateToBle = onNavigateToBle)
            } else {
                Row(modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier =
                            Modifier
                                .fillMaxHeight()
                                .width(130.dp)
                                .padding(start = 16.dp, top = 14.dp, bottom = 14.dp, end = 10.dp),
                        verticalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            dayLabels.forEachIndexed { index, day ->
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.clickable { onDaySelect(index) },
                                ) {
                                    Text(
                                        text = day,
                                        fontSize = 9.sp,
                                        color = if (index == selectedDay) Primary else Color(0xFFBBBBBB),
                                        fontWeight = if (index == selectedDay) FontWeight.Bold else FontWeight.Normal,
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier =
                                            Modifier
                                                .size(18.dp)
                                                .background(
                                                    if (index == selectedDay) Primary else Color.Transparent,
                                                    CircleShape,
                                                ),
                                    ) {
                                        Text(
                                            text = "${weekDates[index]}",
                                            fontSize = 8.sp,
                                            color = if (index == selectedDay) Color.White else Color(0xFFBBBBBB),
                                            fontWeight = if (index == selectedDay) FontWeight.Bold else FontWeight.Normal,
                                        )
                                    }
                                }
                            }
                        }

                        Column {
                            Text(
                                text = "${currentValue.toInt()}",
                                fontSize = 42.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = currentColor,
                                lineHeight = 42.sp,
                                maxLines = 1,
                            )
                            Text(text = "mg/dL", fontSize = 12.sp, color = Color(0xFF888888))
                            Spacer(modifier = Modifier.height(4.dp))
                            GlucoseStatusBadge(currentValue, currentColor)
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(modifier = Modifier.size(7.dp).background(colorDanger, CircleShape))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(text = "최고 ", fontSize = 11.sp, color = Color(0xFF888888))
                                Text(
                                    text = "${(chartData.maxOrNull() ?: 0f).toInt()}",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF333333),
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(modifier = Modifier.size(7.dp).background(Primary, CircleShape))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(text = "최저 ", fontSize = 11.sp, color = Color(0xFF888888))
                                Text(
                                    text = "${(chartData.minOrNull() ?: 0f).toInt()}",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF333333),
                                )
                            }
                        }
                    }

                    Box(
                        modifier =
                            Modifier
                                .fillMaxHeight()
                                .width(1.dp)
                                .background(Color(0xFFEEEEEE)),
                    )

                    GlucoseCanvas(
                        modifier =
                            Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .padding(horizontal = 12.dp, vertical = 12.dp),
                        data = chartData,
                    )
                }
            }
        }

        Icon(
            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
            contentDescription = "뒤로가기",
            tint = Color(0xFF333333),
            modifier =
                Modifier
                    .align(Alignment.TopStart)
                    .padding(10.dp)
                    .size(22.dp)
                    .clickable(onClick = onBack),
        )
    }
}

// ── 공용 컴포넌트 ─────────────────────────────────────────────────────────────

@Composable
private fun DeviceNotConnectedPlaceholder(onNavigateToBle: () -> Unit = {}) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .size(72.dp)
                    .background(Color(0xFFE8F7FA), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.Bluetooth,
                contentDescription = null,
                tint = Primary,
                modifier = Modifier.size(36.dp),
            )
        }
        Text(
            text = "기기가 연동되어 있지 않아요",
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF333333),
        )
        Text(
            text = "기기를 연동하면\n실시간 혈당 추이를 확인할 수 있어요",
            fontSize = 14.sp,
            color = Color(0xFF888888),
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Button(
            onClick = onNavigateToBle,
            colors = ButtonDefaults.buttonColors(containerColor = Primary),
            shape = RoundedCornerShape(12.dp),
        ) {
            Text(text = "기기 연동하기", fontSize = 15.sp)
        }
    }
}

@Composable
private fun GlucoseRangeLegend() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        RangeBadge(color = colorNormal, label = "정상  70~140")
        RangeBadge(color = colorWarning, label = "주의  ~180")
        RangeBadge(color = colorDanger, label = "위험  <70 / >180")
    }
}

@Composable
private fun RangeBadge(
    color: Color,
    label: String,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(8.dp).background(color, CircleShape))
        Spacer(modifier = Modifier.width(4.dp))
        Text(text = label, fontSize = 11.sp, color = Color(0xFF888888))
    }
}

@Composable
private fun GlucoseStatusBadge(
    value: Float,
    color: Color,
) {
    Box(
        modifier =
            Modifier
                .background(color.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                .padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        Text(
            text =
                when {
                    value < 70 -> "저혈당"
                    value > 180 -> "고혈당"
                    value > 140 -> "주의"
                    else -> "정상"
                },
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = color,
        )
    }
}

@Composable
private fun DaySelector(
    weekDates: List<Int>,
    todayIndex: Int,
    selectedDay: Int,
    onDaySelect: (Int) -> Unit,
) {
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
                    modifier = Modifier.clickable { onDaySelect(index) },
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
                    .clickable { onDaySelect(todayIndex) }
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
}

@Composable
private fun HighLowRow(chartData: List<Float> = dummyGlucoseData) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(8.dp).background(colorDanger, CircleShape))
        Spacer(modifier = Modifier.width(6.dp))
        Text(text = "최고 ", fontSize = 15.sp, color = Color(0xFF888888))
        Text(
            text = "${(chartData.maxOrNull() ?: 0f).toInt()}",
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF333333),
        )
        Spacer(modifier = Modifier.width(16.dp))
        Box(modifier = Modifier.size(8.dp).background(Primary, CircleShape))
        Spacer(modifier = Modifier.width(6.dp))
        Text(text = "최저 ", fontSize = 15.sp, color = Color(0xFF888888))
        Text(
            text = "${(chartData.minOrNull() ?: 0f).toInt()}",
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF333333),
        )
    }
}

@Composable
private fun GlucoseCanvas(
    modifier: Modifier = Modifier,
    data: List<Float> = dummyGlucoseData,
) {
    val timeLabels = remember { getKrTimeLabels() }
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val yMin = 0f
        val yMax = 250f
        val leftPad = 48f
        val chartW = w - leftPad

        val n = data.size
        if (n < 2) return@Canvas
        val stepX = chartW / (n - 1)

        fun xOf(i: Int) = leftPad + i * stepX

        fun yOf(v: Float) = h - (v - yMin) / (yMax - yMin) * h

        drawRect(
            color = Primary.copy(alpha = 0.07f),
            topLeft = Offset(leftPad, yOf(140f)),
            size = Size(chartW, yOf(70f) - yOf(140f)),
        )

        val textPaint =
            Paint().asFrameworkPaint().apply {
                isAntiAlias = true
                textSize = 28f
                color = android.graphics.Color.parseColor("#AAAAAA")
            }
        yAxisValues.forEach { label ->
            val y = yOf(label.toFloat())
            drawLine(
                color = Color(0xFFEEEEEE),
                start = Offset(leftPad, y),
                end = Offset(w, y),
                strokeWidth = 1.5f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)),
            )
            drawIntoCanvas { it.nativeCanvas.drawText("$label", 0f, y + 10f, textPaint) }
        }

        val linePath = Path()
        linePath.moveTo(xOf(0), yOf(data[0]))
        for (i in 1 until n) {
            val cpX = (xOf(i - 1) + xOf(i)) / 2f
            linePath.cubicTo(cpX, yOf(data[i - 1]), cpX, yOf(data[i]), xOf(i), yOf(data[i]))
        }

        drawPath(
            path =
                Path().apply {
                    addPath(linePath)
                    lineTo(xOf(n - 1), h)
                    lineTo(leftPad, h)
                    close()
                },
            brush =
                Brush.verticalGradient(
                    colors = listOf(Primary.copy(alpha = 0.35f), Primary.copy(alpha = 0f)),
                    startY = 0f,
                    endY = h,
                ),
        )

        drawPath(
            path = linePath,
            color = Primary,
            style = Stroke(width = 7f, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )

        val lastX = xOf(n - 1)
        val lastY = yOf(data.last())
        drawCircle(color = Primary.copy(alpha = 0.15f), radius = 22f, center = Offset(lastX, lastY))
        drawCircle(color = Primary.copy(alpha = 0.35f), radius = 14f, center = Offset(lastX, lastY))
        drawCircle(color = Primary, radius = 8f, center = Offset(lastX, lastY))
        drawCircle(color = Color.White, radius = 4f, center = Offset(lastX, lastY))

        val peakIdx = data.indexOf(data.maxOrNull() ?: return@Canvas)
        drawCircle(color = colorDanger.copy(alpha = 0.2f), radius = 18f, center = Offset(xOf(peakIdx), yOf(data[peakIdx])))
        drawCircle(color = colorDanger, radius = 8f, center = Offset(xOf(peakIdx), yOf(data[peakIdx])))
        drawCircle(color = Color.White, radius = 4f, center = Offset(xOf(peakIdx), yOf(data[peakIdx])))

        val xTextPaint =
            Paint().asFrameworkPaint().apply {
                isAntiAlias = true
                textSize = 26f
                color = android.graphics.Color.parseColor("#AAAAAA")
                textAlign = android.graphics.Paint.Align.CENTER
            }
        timeLabels.forEachIndexed { i, label ->
            drawIntoCanvas { it.nativeCanvas.drawText(label, xOf(i), h, xTextPaint) }
        }
    }
}
