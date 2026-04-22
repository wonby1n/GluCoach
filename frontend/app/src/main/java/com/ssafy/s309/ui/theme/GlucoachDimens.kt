package com.ssafy.s309.ui.theme

import androidx.compose.ui.unit.dp

/**
 * Glucoach 디자인 토큰 — 반복되는 공간(spacing) 값.
 *
 * 8pt 그리드 다중값만 토큰화한다. Figma 특수치(예: 13, 18, 22, 28.dp) 는
 * 의도된 디자인 디테일이므로 토큰에 우겨넣지 말고 사용처에 그대로 둔다.
 * 아이콘 크기 / 컨트롤 치수는 spacing 이 아니므로 여기 두지 않는다.
 */
object GlucoachSpacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 20.dp
    val xxl = 24.dp
}

/** 카드 / 컨테이너 반복 모서리 값. */
object GlucoachCorner {
    val card = 20.dp
}
