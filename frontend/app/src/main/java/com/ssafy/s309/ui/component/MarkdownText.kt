package com.ssafy.s309.ui.component

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

@Composable
fun MarkdownText(
    text: String,
    color: Color,
    fontSize: TextUnit,
    modifier: Modifier = Modifier,
    lineHeight: TextUnit = TextUnit.Unspecified,
) {
    val boldPattern = Regex("""\*\*(.+?)\*\*""")
    val headerSize = (fontSize.value * 1.18f).sp
    val lines = text.split("\n")

    val annotated =
        buildAnnotatedString {
            lines.forEachIndexed { idx, line ->
                val isHeader = line.startsWith("# ")
                val content = if (isHeader) line.removePrefix("# ") else line
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
                    withStyle(
                        SpanStyle(
                            fontWeight = FontWeight.Bold,
                            fontSize = if (isHeader) headerSize else TextUnit.Unspecified,
                        ),
                    ) { append(match.groupValues[1]) }
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

    Text(
        text = annotated,
        color = color,
        fontSize = fontSize,
        lineHeight = lineHeight,
        modifier = modifier,
    )
}
