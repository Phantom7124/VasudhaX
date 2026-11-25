package com.example.executorchllamademo

import android.content.Context
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class WasteThemeMode(val id: String, val displayName: String, val icon: String) {
    CYBER_OBSIDIAN("cyber", "Cyber Neon", "⚡"),
    FOREST_EMERALD("forest", "Forest Jade", "🌿"),
    NORDIC_ARCTIC("nordic", "Nordic Ice", "❄️")
}

data class ExtraThemeColors(
    val cardBackground: Color,
    val cardBorder: Color,
    val subText: Color,
    val headerGradientStart: Color,
    val headerGradientEnd: Color,
    val heroCardBg: Color,
    val heroCardBorder: Color,
    val reticleColor: Color,
    val badgeBg: Color,
    val badgeText: Color,
    val isDark: Boolean
)

val cyberColors: ColorScheme = darkColorScheme(
    primary = Color(0xFF00F5A0),       // Neon mint
    onPrimary = Color(0xFF031A10),
    secondary = Color(0xFF06B6D4),     // Cyber cyan
    onSecondary = Color(0xFF041920),
    tertiary = Color(0xFFA855F7),      // Laser violet
    background = Color(0xFF090D16),    // Pitch obsidian
    onBackground = Color(0xFFF1F5F9),  // Crisp white/silver
    surface = Color(0xFF111827),       // Deep slate glass
    onSurface = Color(0xFFF8FAFC),
    error = Color(0xFFFF4D4D)
)

val cyberExtraColors = ExtraThemeColors(
    cardBackground = Color(0xFF131C2E),
    cardBorder = Color(0x3300F5A0),
    subText = Color(0xFF94A3B8),
    headerGradientStart = Color(0xFF060A12),
    headerGradientEnd = Color(0xFF0F1C30),
    heroCardBg = Color(0xFF10192A),
    heroCardBorder = Color(0x6600F5A0),
    reticleColor = Color(0xFF00F5A0),
    badgeBg = Color(0x2B00F5A0),
    badgeText = Color(0xFF00F5A0),
    isDark = true
)

val forestColors: ColorScheme = lightColorScheme(
    primary = Color(0xFF056B39),       // Deep forest emerald
    onPrimary = Color.White,
    secondary = Color(0xFF10B981),     // Spring leaf green
    onSecondary = Color.White,
    tertiary = Color(0xFFF59E0B),      // Solar amber
    background = Color(0xFFF0FDF4),    // Sage mist
    onBackground = Color(0xFF0F291E),
    surface = Color(0xFFFFFFFF),       // Pure snow
    onSurface = Color(0xFF143324),
    error = Color(0xFFDC2626)
)

val forestExtraColors = ExtraThemeColors(
    cardBackground = Color(0xFFFFFFFF),
    cardBorder = Color(0x26056B39),
    subText = Color(0xFF4B6354),
    headerGradientStart = Color(0xFF064E3B),
    headerGradientEnd = Color(0xFF059669),
    heroCardBg = Color(0xFFFFFFFF),
    heroCardBorder = Color(0x38056B39),
    reticleColor = Color(0xFF10B981),
    badgeBg = Color(0x1F056B39),
    badgeText = Color(0xFF056B39),
    isDark = false
)

val nordicColors: ColorScheme = lightColorScheme(
    primary = Color(0xFF1D4ED8),       // Arctic cobalt
    onPrimary = Color.White,
    secondary = Color(0xFF0284C7),     // Glacial cyan
    onSecondary = Color.White,
    tertiary = Color(0xFFEF4444),      // Nordic coral
    background = Color(0xFFF1F5F9),    // Ice slate
    onBackground = Color(0xFF0F172A),
    surface = Color(0xFFFFFFFF),       // Crisp snow
    onSurface = Color(0xFF0F172A),
    error = Color(0xFFDC2626)
)

val nordicExtraColors = ExtraThemeColors(
    cardBackground = Color(0xFFFFFFFF),
    cardBorder = Color(0x221D4ED8),
    subText = Color(0xFF64748B),
    headerGradientStart = Color(0xFF0F172A),
    headerGradientEnd = Color(0xFF1E3A8A),
    heroCardBg = Color(0xFFFFFFFF),
    heroCardBorder = Color(0x381D4ED8),
    reticleColor = Color(0xFF0284C7),
    badgeBg = Color(0x1A1D4ED8),
    badgeText = Color(0xFF1D4ED8),
    isDark = false
)

val LocalExtraColors = staticCompositionLocalOf { cyberExtraColors }

object ThemeManager {
    var currentTheme by mutableStateOf(WasteThemeMode.CYBER_OBSIDIAN)
    private var initialized = false

    fun init(context: Context) {
        if (initialized) return
        val sp = context.getSharedPreferences("waste_theme_prefs", Context.MODE_PRIVATE)
        val saved = sp.getString("theme_mode", WasteThemeMode.CYBER_OBSIDIAN.id)
        currentTheme = WasteThemeMode.values().find { it.id == saved } ?: WasteThemeMode.CYBER_OBSIDIAN
        initialized = true
    }

    fun setTheme(context: Context, mode: WasteThemeMode) {
        currentTheme = mode
        val sp = context.getSharedPreferences("waste_theme_prefs", Context.MODE_PRIVATE)
        sp.edit().putString("theme_mode", mode.id).apply()
    }
}

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
    large = RoundedCornerShape(26.dp)
)

/** Dynamic Theme Provider supporting Cyber Obsidian, Forest Emerald, and Nordic Arctic. */
@Composable
fun WasteTheme(content: @Composable () -> Unit) {
    val (scheme, extra) = when (ThemeManager.currentTheme) {
        WasteThemeMode.CYBER_OBSIDIAN -> Pair(cyberColors, cyberExtraColors)
        WasteThemeMode.FOREST_EMERALD -> Pair(forestColors, forestExtraColors)
        WasteThemeMode.NORDIC_ARCTIC -> Pair(nordicColors, nordicExtraColors)
    }

    CompositionLocalProvider(LocalExtraColors provides extra) {
        MaterialTheme(
            colorScheme = scheme,
            typography = WasteTypography,
            shapes = WasteShapes,
            content = content
        )
    }
}