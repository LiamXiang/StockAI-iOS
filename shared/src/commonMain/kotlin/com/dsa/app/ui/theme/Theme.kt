package com.dsa.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val DsaBlue = Color(0xFF1E3A5F)
val DsaRed = Color(0xFFE53E3E)      // 涨（A股红涨）
val DsaGreen = Color(0xFF089981)    // 跌（A股绿跌）
val DsaAmber = Color(0xFFE6A23C)

private val LightColors = lightColorScheme(
    primary = DsaBlue,
    secondary = DsaBlue,
    background = Color(0xFFF5F6FA),
    surface = Color.White,
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF7BA3D4),
    secondary = Color(0xFF7BA3D4),
    background = Color(0xFF121417),
    surface = Color(0xFF1B1E23),
)

@Composable
fun DsaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
