package com.ssafy.s309.ui.component

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ssafy.s309.ui.theme.GlucoachColors
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

private const val INFINITE_HALF = 50_000

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WheelPicker(
    items: List<String>,
    selectedIndex: Int,
    onSelectedChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    itemWidth: Dp = 56.dp,
    itemHeight: Dp = 44.dp,
    visibleCount: Int = 3,
) {
    val count = items.size
    val startIndex = INFINITE_HALF - (INFINITE_HALF % count) + selectedIndex
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = startIndex - visibleCount / 2)
    val flingBehavior = rememberSnapFlingBehavior(lazyListState = listState)

    LaunchedEffect(listState) {
        snapshotFlow {
            val layoutInfo = listState.layoutInfo
            val viewportCenter = layoutInfo.viewportStartOffset + layoutInfo.viewportSize.height / 2
            layoutInfo.visibleItemsInfo.minByOrNull {
                kotlin.math.abs((it.offset + it.size / 2) - viewportCenter)
            }?.index
        }
            .distinctUntilChanged()
            .map { it?.mod(count) ?: selectedIndex }
            .distinctUntilChanged()
            .collect { onSelectedChange(it) }
    }

    val totalHeight = itemHeight * visibleCount

    Box(
        modifier =
            modifier
                .height(totalHeight)
                .width(itemWidth)
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    val fadeHeight = itemHeight.toPx()
                    drawRect(
                        brush =
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black),
                                startY = 0f,
                                endY = fadeHeight,
                            ),
                        blendMode = BlendMode.DstIn,
                    )
                    drawRect(
                        brush =
                            Brush.verticalGradient(
                                colors = listOf(Color.Black, Color.Transparent),
                                startY = size.height - fadeHeight,
                                endY = size.height,
                            ),
                        blendMode = BlendMode.DstIn,
                    )
                },
    ) {
        LazyColumn(
            state = listState,
            flingBehavior = flingBehavior,
            modifier = Modifier.height(totalHeight),
        ) {
            items(count = INFINITE_HALF * 2) { globalIndex ->
                val realIndex = globalIndex.mod(count)
                val isCentered = realIndex == selectedIndex

                Box(
                    modifier =
                        Modifier
                            .height(itemHeight)
                            .width(itemWidth),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = items[realIndex],
                        fontSize = if (isCentered) 20.sp else 15.sp,
                        fontWeight = if (isCentered) FontWeight.Bold else FontWeight.Normal,
                        color = if (isCentered) GlucoachColors.TextPrimary else GlucoachColors.TextSecondary.copy(alpha = 0.5f),
                    )
                }
            }
        }
    }
}
