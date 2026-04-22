package com.ssafy.s309.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.s309.ui.theme.GlucoachColors
import com.ssafy.s309.ui.theme.GlucoachSpacing

/**
 * 하단 탭 엔트리. 아이콘은 추후 asset 으로 교체되므로 현재는 nullable 로 둔다.
 *
 * @property id 안정적인 식별자 (navigation route 와 매핑)
 * @property label 접근성/툴팁용 라벨
 * @property icon 추후 주입될 아이콘 (null 이면 자리 표시자 원이 렌더됨)
 * @property isCenter 가운데 강조 FAB 스타일로 렌더할지 여부
 */
data class BottomNavItem(
    val id: String,
    val label: String,
    val icon: ImageVector? = null,
    val isCenter: Boolean = false,
)

/**
 * 앱 전역에서 재사용되는 하단 내비게이션 바.
 *
 * 디자인 기준: 높이 60dp, 흰 배경, 연회색 테두리, 5개 탭, 중앙은 원형 강조.
 * 아이콘은 추후 Figma export 후 [BottomNavItem.icon] 에 연결한다.
 */
@Composable
fun BottomNavBar(
    items: List<BottomNavItem>,
    selectedId: String,
    onItemClick: (BottomNavItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .height(BOTTOM_BAR_HEIGHT)
                .background(GlucoachColors.Surface)
                .border(width = 1.dp, color = GlucoachColors.Border)
                .padding(horizontal = GlucoachSpacing.lg),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        items.forEach { item ->
            BottomNavEntry(
                item = item,
                isSelected = item.id == selectedId,
                onClick = { onItemClick(item) },
            )
        }
    }
}

@Composable
private fun BottomNavEntry(
    item: BottomNavItem,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    if (item.isCenter) {
        Box(
            modifier =
                Modifier
                    .size(CENTER_BUTTON_SIZE)
                    .clip(CircleShape)
                    .background(GlucoachColors.Primary)
                    .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            item.icon?.let {
                Icon(
                    imageVector = it,
                    contentDescription = item.label,
                    tint = Color.White,
                    modifier = Modifier.size(ICON_SIZE),
                )
            } ?: Text(
                text = "+",
                color = Color.White,
                fontSize = 24.sp,
            )
        }
    } else {
        Box(
            modifier =
                Modifier
                    .size(ICON_SIZE)
                    .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            item.icon?.let {
                Icon(
                    imageVector = it,
                    contentDescription = item.label,
                    tint =
                        if (isSelected) {
                            GlucoachColors.PrimaryDark
                        } else {
                            GlucoachColors.TextSecondary
                        },
                    modifier = Modifier.size(ICON_SIZE),
                )
            }
            // 아이콘이 아직 없을 때는 빈 자리만 차지 (추후 drawable 삽입 시 자동 표시)
        }
    }
}

private val BOTTOM_BAR_HEIGHT = 60.dp
private val CENTER_BUTTON_SIZE = 48.dp
private val ICON_SIZE = 24.dp
