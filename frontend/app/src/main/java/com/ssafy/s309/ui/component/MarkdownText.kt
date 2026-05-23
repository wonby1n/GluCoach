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

@Composable
fun MarkdownText(
    text: String,
    color: Color,
    fontSize: TextUnit,
    modifier: Modifier = Modifier,
    lineHeight: TextUnit = TextUnit.Unspecified,
) {
    val annotated =
        buildAnnotatedString {
            val pattern = Regex("""\*\*(.+?)\*\*""")
            var lastEnd = 0
            for (match in pattern.findAll(text)) {
                append(text.substring(lastEnd, match.range.first))
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                    append(match.groupValues[1])
                }
                lastEnd = match.range.last + 1
            }
            append(text.substring(lastEnd))
        }
    Text(
        text = annotated,
        color = color,
        fontSize = fontSize,
        lineHeight = lineHeight,
        modifier = modifier,
    )
}
