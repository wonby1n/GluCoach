package com.ssafy.s309.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.s309.data.model.GlucoseRange
import com.ssafy.s309.ui.theme.GlucoachColors
import com.ssafy.s309.ui.theme.GlucoachCorner
import com.ssafy.s309.ui.theme.GlucoachSpacing

/**
 * 메인 화면 상단 "현재 혈당" 카드.
 *
 * @param currentMgDl 현재 혈당 수치
 * @param diffFromPrevious 30분 전 대비 변화 (양수/음수/0)
 * @param mascotSlot 키키 캐릭터 이미지 슬롯 — 추후 asset 주입. null 이면 비워둠.
 */
@Composable
fun CurrentGlucoseCard(
    currentMgDl: Int,
    diffFromPrevious: Int,
    glucoseRange: GlucoseRange = GlucoseRange(minMgDl = 70, maxMgDl = 140),
    modifier: Modifier = Modifier,
    mascotSlot: (@Composable () -> Unit)? = null,
) {
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .height(304.dp)
                .shadow(4.dp, RoundedCornerShape(GlucoachCorner.card))
                .clip(RoundedCornerShape(GlucoachCorner.card))
                .background(GlucoachColors.Surface)
                .border(
                    width = 1.dp,
                    color = GlucoachColors.Border,
                    shape = RoundedCornerShape(GlucoachCorner.card),
                ),
    ) {
        val glucoseColor = glucoseZoneColor(currentMgDl, glucoseRange)

        // 좌측 텍스트 블록
        Column(
            modifier =
                Modifier
                    .padding(start = GlucoachSpacing.xxl, top = 18.dp)
                    .width(160.dp),
            verticalArrangement = Arrangement.spacedBy(13.dp),
        ) {
            Text(
                text = "현재 혈당",
                color = glucoseColor,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
            )
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = "$currentMgDl",
                    color = glucoseColor,
                    fontSize = 52.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.width(7.dp))
                Text(
                    text = "mg/dL",
                    color = GlucoachColors.TextSecondary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = GlucoachSpacing.sm),
                )
            }
            Text(
                text = "30분 전 대비 ${formatDiff(diffFromPrevious)}",
                color = glucoseColor,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
            )
        }

        // 우측 마스코트 슬롯 (asset 주입용)
        Box(
            modifier =
                Modifier
                    .align(Alignment.BottomEnd)
                    .size(width = 200.dp, height = 290.dp),
            contentAlignment = Alignment.BottomEnd,
        ) {
            mascotSlot?.invoke()
            // asset 이 없으면 빈 공간만 유지
        }
    }
}

private fun formatDiff(diff: Int): String =
    when {
        diff > 0 -> "+$diff"
        else -> "$diff"
    }

internal fun glucoseZoneColor(
    mgDl: Int,
    range: GlucoseRange,
) = when {
    mgDl < range.minMgDl || mgDl > range.maxMgDl + 40 -> GlucoachColors.GlucoseDanger
    mgDl > range.maxMgDl -> GlucoachColors.GlucoseWarning
    else -> GlucoachColors.GlucoseNormal
}

/**
 * 2x1 배치되는 하단 정보 카드 (걸음수 / 수면). 값 영역은 슬롯으로 받아
 * 단일 (값+단위) 또는 (값+단위)×N 같은 구성을 호출자가 자유롭게 조립한다.
 */
@Composable
fun SummaryStatCard(
    title: String,
    periodLabel: String,
    modifier: Modifier = Modifier,
    value: @Composable RowScope.() -> Unit,
) {
    Column(
        modifier =
            modifier
                .shadow(3.dp, RoundedCornerShape(GlucoachCorner.card))
                .clip(RoundedCornerShape(GlucoachCorner.card))
                .background(GlucoachColors.Surface)
                .padding(horizontal = 19.dp, vertical = GlucoachSpacing.lg),
    ) {
        Text(
            text = title,
            color = GlucoachColors.PrimaryDark,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = periodLabel,
            color = GlucoachColors.PrimaryDark,
            fontSize = 10.sp,
        )
        Spacer(modifier = Modifier.height(2.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom,
            content = value,
        )
    }
}
