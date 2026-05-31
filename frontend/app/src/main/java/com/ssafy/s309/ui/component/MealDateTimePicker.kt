package com.ssafy.s309.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.s309.ui.theme.GlucoachColors
import java.time.LocalDateTime
import java.time.YearMonth

@Composable
fun MealDateTimePicker(
    initialDateTime: LocalDateTime = LocalDateTime.now(),
    onDateTimeChanged: (LocalDateTime) -> Unit,
    modifier: Modifier = Modifier,
) {
    var month by remember { mutableIntStateOf(initialDateTime.monthValue) }
    var day by remember { mutableIntStateOf(initialDateTime.dayOfMonth) }
    var isAm by remember { mutableIntStateOf(if (initialDateTime.hour < 12) 0 else 1) }
    var hour12 by remember {
        val h = initialDateTime.hour % 12
        mutableIntStateOf(if (h == 0) 12 else h)
    }
    var minute by remember { mutableIntStateOf(initialDateTime.minute) }
    val year = initialDateTime.year

    val maxDay by remember(month) {
        derivedStateOf { YearMonth.of(year, month).lengthOfMonth() }
    }

    fun emitChange() {
        val clampedDay = day.coerceAtMost(maxDay)
        val hour24 =
            when {
                isAm == 0 && hour12 == 12 -> 0
                isAm == 1 && hour12 == 12 -> 12
                isAm == 1 -> hour12 + 12
                else -> hour12
            }
        onDateTimeChanged(
            LocalDateTime.of(year, month, clampedDay, hour24, minute),
        )
    }

    val months = remember { (1..12).map { "${it}월" } }
    val days = remember(maxDay) { (1..maxDay).map { "${it}일" } }
    val amPm = remember { listOf("오전", "오후") }
    val hours = remember { (1..12).map { "$it" } }
    val minutes = remember { (0..59).map { String.format("%02d", it) } }

    val itemHeight = 44.dp

    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(GlucoachColors.Background)
                .padding(vertical = 8.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .height(itemHeight)
                    .clip(RoundedCornerShape(10.dp))
                    .background(GlucoachColors.Surface),
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            WheelPicker(
                items = months,
                selectedIndex = month - 1,
                onSelectedChange = {
                    month = it + 1
                    emitChange()
                },
                itemWidth = 56.dp,
                itemHeight = itemHeight,
            )
            WheelPicker(
                items = days,
                selectedIndex = (day - 1).coerceIn(0, days.size - 1),
                onSelectedChange = {
                    day = it + 1
                    emitChange()
                },
                itemWidth = 50.dp,
                itemHeight = itemHeight,
            )
            WheelPicker(
                items = amPm,
                selectedIndex = isAm,
                onSelectedChange = {
                    isAm = it
                    emitChange()
                },
                itemWidth = 52.dp,
                itemHeight = itemHeight,
            )
            WheelPicker(
                items = hours,
                selectedIndex = hour12 - 1,
                onSelectedChange = {
                    hour12 = it + 1
                    emitChange()
                },
                itemWidth = 36.dp,
                itemHeight = itemHeight,
            )
            Text(
                text = ":",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = GlucoachColors.TextPrimary,
            )
            WheelPicker(
                items = minutes,
                selectedIndex = minute,
                onSelectedChange = {
                    minute = it
                    emitChange()
                },
                itemWidth = 40.dp,
                itemHeight = itemHeight,
            )
        }
    }
}
