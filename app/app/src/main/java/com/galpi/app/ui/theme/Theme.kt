package com.galpi.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// 갈피 브랜드: 짙은 초록(책 표지) + 노란 책갈피 포인트
private val LightColors = lightColorScheme(
    primary = Color(0xFF2D5B4F),
    onPrimary = Color.White,
    secondary = Color(0xFFF3B94D),
    background = Color(0xFFFCFAF6),
    surface = Color(0xFFFCFAF6),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8FC7B5),
    onPrimary = Color(0xFF10281F),
    secondary = Color(0xFFF3B94D),
    background = Color(0xFF141917),
    surface = Color(0xFF141917),
)

@Composable
fun GalpiTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content,
    )
}
