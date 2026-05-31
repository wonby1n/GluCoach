package com.ssafy.s309.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme =
    darkColorScheme(
        primary = Primary,
        secondary = PrimaryDark,
        tertiary = PrimaryLight,
        background = Background,
        surface = Surface,
    )

private val LightColorScheme =
    lightColorScheme(
        primary = Primary,
        secondary = PrimaryDark,
        tertiary = PrimaryLight,
        background = Background,
        surface = Surface,
    )

@Composable
fun S309Theme(
    darkTheme: Boolean = false,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content,
    )
}
