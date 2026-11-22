package com.example.executorchllamademo

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val WasteColors = lightColorScheme(
    primary = Color(0xFF1B6B43),
    onPrimary = Color.White,
    secondary = Color(0xFF2E9E6B),
    background = Color(0xFFF2F7F3),
    onBackground = Color(0xFF1B1F1D),
    surface = Color.White,
    onSurface = Color(0xFF1B1F1D),
    error = Color(0xFFC62828)
)

private val base = Typography()

private val WasteTypography = base.copy(
    headlineSmall = TextStyle(fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 30.sp),
    titleLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 26.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
    titleSmall = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
    bodyMedium = TextStyle(fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 22.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 14.sp),
    labelMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 12.sp),
    labelSmall = TextStyle(fontWeight = FontWeight.Medium, fontSize = 11.sp)
)

private val WasteShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(28.dp)
)

/** One look for the whole app: green brand colour, soft grey background, rounded cards. */
@Composable
fun WasteTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = WasteColors,
        typography = WasteTypography,
        shapes = WasteShapes,
        content = content
    )
}