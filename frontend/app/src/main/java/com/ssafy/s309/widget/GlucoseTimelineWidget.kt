package com.ssafy.s309.widget

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.ssafy.s309.MainActivity
import java.io.File

class GlucoseTimelineWidget : GlanceAppWidget() {
    override val stateDefinition = GlucoseWidgetStateDefinition

    override suspend fun provideGlance(
        context: Context,
        id: GlanceId,
    ) {
        provideContent {
            val state = currentState<GlucoseWidgetState>()
            WidgetContent(state)
        }
    }

    @Composable
    private fun WidgetContent(state: GlucoseWidgetState) {
        Box(
            modifier =
                GlanceModifier
                    .fillMaxSize()
                    .background(Color.White)
                    .cornerRadius(20.dp)
                    .padding(14.dp)
                    .clickable(actionStartActivity<MainActivity>()),
        ) {
            Column(modifier = GlanceModifier.fillMaxSize()) {
                HeaderRow(state)
                Spacer(GlanceModifier.height(6.dp))
                CurrentValueRow(state)
                Spacer(GlanceModifier.height(6.dp))
                ChartArea(state)
                Spacer(GlanceModifier.height(6.dp))
                StatsRow(state)
            }
        }
    }

    @Composable
    private fun HeaderRow(state: GlucoseWidgetState) {
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "● 오늘 혈당 추이",
                style =
                    TextStyle(
                        color = androidx.glance.unit.ColorProvider(Color(0xFF475569)),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                    ),
            )
            Spacer(GlanceModifier.width(8.dp))
            Text(
                text = state.updatedAtText,
                style =
                    TextStyle(
                        color = androidx.glance.unit.ColorProvider(Color(0xFF94A3B8)),
                        fontSize = 10.sp,
                    ),
                modifier = GlanceModifier.defaultWeight(),
            )
            Text(
                text = "↻",
                style =
                    TextStyle(
                        color = androidx.glance.unit.ColorProvider(Color(0xFF0EA5E9)),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                modifier =
                    GlanceModifier
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                        .clickable(actionRunCallback<RefreshGlucoseWidgetAction>()),
            )
        }
    }

    @Composable
    private fun CurrentValueRow(state: GlucoseWidgetState) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = state.currentValue?.toString() ?: "--",
                style =
                    TextStyle(
                        color = androidx.glance.unit.ColorProvider(Color(0xFF0F172A)),
                        fontSize = 34.sp,
                        fontWeight = FontWeight.Bold,
                    ),
            )
            Spacer(GlanceModifier.width(6.dp))
            Text(
                text = "mg/dL",
                style =
                    TextStyle(
                        color = androidx.glance.unit.ColorProvider(Color(0xFF64748B)),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                modifier = GlanceModifier.padding(bottom = 6.dp),
            )
            Spacer(GlanceModifier.width(8.dp))
            state.trendDelta?.let { delta ->
                val arrow = if (delta >= 0) "↗" else "↘"
                val sign = if (delta >= 0) "+" else ""
                Text(
                    text = "$arrow $sign$delta",
                    style =
                        TextStyle(
                            color = androidx.glance.unit.ColorProvider(Color(0xFF92400E)),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                    modifier =
                        GlanceModifier
                            .background(Color(0xFFFEF3C7))
                            .cornerRadius(10.dp)
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                )
            }
        }
    }

    @Composable
    private fun ChartArea(state: GlucoseWidgetState) {
        val bitmap =
            state.chartPngPath?.let { path ->
                runCatching {
                    val file = File(path)
                    if (file.exists()) BitmapFactory.decodeFile(path) else null
                }.getOrNull()
            }
        if (bitmap != null) {
            Image(
                provider = ImageProvider(bitmap),
                contentDescription = "혈당 추이 차트",
                contentScale = ContentScale.Fit,
                modifier = GlanceModifier.fillMaxWidth().height(64.dp),
            )
        } else {
            Box(
                modifier =
                    GlanceModifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .background(Color(0xFFF1F5F9))
                        .cornerRadius(8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text =
                        if (!state.isLoggedIn) {
                            "로그인 후 사용 가능"
                        } else if (state.errorMessage != null) {
                            state.errorMessage
                        } else {
                            "측정 데이터 없음"
                        },
                    style =
                        TextStyle(
                            color = androidx.glance.unit.ColorProvider(Color(0xFF94A3B8)),
                            fontSize = 11.sp,
                        ),
                )
            }
        }
    }

    @Composable
    private fun StatsRow(state: GlucoseWidgetState) {
        Row(modifier = GlanceModifier.fillMaxWidth()) {
            Stat(modifier = GlanceModifier.defaultWeight(), label = "평균", value = state.avg)
            Stat(modifier = GlanceModifier.defaultWeight(), label = "최고", value = state.max)
            Stat(modifier = GlanceModifier.defaultWeight(), label = "최저", value = state.min)
            Stat(
                modifier = GlanceModifier.defaultWeight(),
                label = "범위내",
                value = state.inRangePct,
                suffix = "%",
                accent = Color(0xFF22C55E),
            )
        }
    }

    @Composable
    private fun Stat(
        modifier: GlanceModifier,
        label: String,
        value: Int?,
        suffix: String = "",
        accent: Color = Color(0xFF0F172A),
    ) {
        Column(
            modifier = modifier,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = value?.let { "$it$suffix" } ?: "--",
                style =
                    TextStyle(
                        color = androidx.glance.unit.ColorProvider(accent),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                    ),
            )
            Text(
                text = label,
                style =
                    TextStyle(
                        color = androidx.glance.unit.ColorProvider(Color(0xFF64748B)),
                        fontSize = 9.sp,
                    ),
            )
        }
    }
}
