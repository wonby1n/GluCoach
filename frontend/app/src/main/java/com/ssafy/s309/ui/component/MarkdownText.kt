package com.ssafy.s309.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun MarkdownText(
    text: String,
    color: Color,
    fontSize: TextUnit,
    modifier: Modifier = Modifier,
    lineHeight: TextUnit = TextUnit.Unspecified,
    headerBackground: Color? = null,
    bodyBackground: Color? = null,
    sectionPaddingHorizontal: Dp = 0.dp,
    sectionPaddingVertical: Dp = 0.dp,
) {
    val boldPattern = Regex("""\*\*(.+?)\*\*""")
    val sharpHeaderPattern = Regex("""^#{1,3} """)
    val headerSize = (fontSize.value * 1.18f).sp

    fun buildSpanned(lines: List<String>): androidx.compose.ui.text.AnnotatedString =
        buildAnnotatedString {
            lines.forEachIndexed { idx, line ->
                val isHeader = sharpHeaderPattern.containsMatchIn(line)
                val content = if (isHeader) sharpHeaderPattern.replace(line, "") else line
                var cursor = 0
                for (match in boldPattern.findAll(content)) {
                    val before = content.substring(cursor, match.range.first)
                    if (before.isNotEmpty()) {
                        if (isHeader) {
                            withStyle(SpanStyle(fontSize = headerSize, fontWeight = FontWeight.SemiBold)) { append(before) }
                        } else {
                            append(before)
                        }
                    }
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = if (isHeader) headerSize else TextUnit.Unspecified)) {
                        append(match.groupValues[1])
                    }
                    cursor = match.range.last + 1
                }
                val tail = content.substring(cursor)
                if (tail.isNotEmpty()) {
                    if (isHeader) {
                        withStyle(SpanStyle(fontSize = headerSize, fontWeight = FontWeight.SemiBold)) { append(tail) }
                    } else {
                        append(tail)
                    }
                }
                if (idx < lines.lastIndex) append("\n")
            }
        }

    if (headerBackground == null && bodyBackground == null) {
        Text(
            text = buildSpanned(text.split("\n")),
            color = color,
            fontSize = fontSize,
            lineHeight = lineHeight,
            modifier = modifier,
        )
    } else {
        // isBold: **...** 단독 줄 (추천메뉴 타입) → 항상 headerBackground
        // isSharp: # 으로 시작 → 다음이 헤더일 때만 headerBackground
        data class Block(val isHeader: Boolean, val isBold: Boolean, val lines: List<String>)
        val blocks = mutableListOf<Block>()
        var buffer = mutableListOf<String>()

        fun flush() {
            val trimmed = buffer.dropWhile { it.isBlank() }.dropLastWhile { it.isBlank() }
            if (trimmed.isNotEmpty()) {
                blocks.add(Block(false, false, trimmed))
            }
            buffer = mutableListOf()
        }

        val foodItemPattern = Regex("""\(.+\)\s*$""")
        for (line in text.split("\n")) {
            val isBoldLine = line.startsWith("**") && line.endsWith("**") && line.length > 4
            val isSharpHeader = sharpHeaderPattern.containsMatchIn(line)
            // 음식 항목 bold(예: **🐟 연어구이 (S등급)**)는 body로 처리
            val isFoodItem = isBoldLine && foodItemPattern.containsMatchIn(line.removePrefix("**").removeSuffix("**"))
            val isBoldHeader = isBoldLine && !isFoodItem
            if (isBoldHeader || isSharpHeader) {
                flush()
                blocks.add(Block(true, isBoldHeader, listOf(line)))
            } else {
                buffer.add(line)
            }
        }
        flush()

        Column(modifier = modifier) {
            blocks.forEachIndexed { idx, block ->
                val prevBlock = blocks.getOrNull(idx - 1)
                val nextBlock = blocks.getOrNull(idx + 1)

                // 헤더→헤더 전환 시 간격 삽입
                if (block.isHeader && prevBlock?.isHeader == true) {
                    Spacer(modifier = Modifier.height(12.dp))
                }
                // 헤더→본문 전환 시 간격 삽입
                if (!block.isHeader && prevBlock?.isHeader == true) {
                    Spacer(modifier = Modifier.height(6.dp))
                }

                val applyHeaderBg = block.isHeader
                val bg =
                    when {
                        applyHeaderBg -> headerBackground
                        !block.isHeader -> bodyBackground
                        else -> null
                    }
                val blockModifier =
                    if (bg != null) {
                        Modifier.fillMaxWidth().background(
                            bg,
                        ).padding(horizontal = sectionPaddingHorizontal, vertical = sectionPaddingVertical)
                    } else {
                        Modifier.fillMaxWidth().padding(horizontal = sectionPaddingHorizontal, vertical = sectionPaddingVertical)
                    }
                Box(modifier = blockModifier) {
                    Text(
                        text = buildSpanned(block.lines),
                        color = color,
                        fontSize = fontSize,
                        lineHeight = lineHeight,
                    )
                }
            }
        }
    }
}
