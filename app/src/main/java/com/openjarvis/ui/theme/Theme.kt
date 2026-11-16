package com.openjarvis.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object VoidColor {
    val Void950 = Color(0xFF08070F)
    val Void900 = Color(0xFF100E1B)
    val Void800 = Color(0xFF191629)
    val Void700 = Color(0xFF25213A)
    val Void600 = Color(0xFF342D4D)
    val Violet = Color(0xFF9B7BFF)
    val VioletDim = Color(0xFF6547B8)
    val Cyan = Color(0xFF55D6E8)
    val Green = Color(0xFF53D6A0)
    val Amber = Color(0xFFFFC66D)
    val Red = Color(0xFFFF6B7A)
    val TextPrimary = Color(0xFFF5F2FF)
    val TextSecondary = Color(0xFFB7B0CB)
    val TextDisabled = Color(0xFF777188)
    val BorderSubtle = Color(0xFF302A43)
    val BorderGlow = Color(0xFF66509D)
}

private val OpenJarvisColors = darkColorScheme(
    primary = VoidColor.Violet,
    onPrimary = Color.White,
    secondary = VoidColor.Cyan,
    onSecondary = VoidColor.Void950,
    background = VoidColor.Void950,
    onBackground = VoidColor.TextPrimary,
    surface = VoidColor.Void900,
    onSurface = VoidColor.TextPrimary,
    surfaceVariant = VoidColor.Void800,
    onSurfaceVariant = VoidColor.TextSecondary,
    outline = VoidColor.BorderSubtle,
    error = VoidColor.Red
)

@Composable
fun OpenJarvisTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = OpenJarvisColors, content = content)
}
