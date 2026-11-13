package com.qingluan.schedule.ui

import android.app.Activity
import android.content.ContextWrapper
import android.graphics.drawable.ColorDrawable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.*
import androidx.compose.ui.unit.Density
import androidx.core.graphics.ColorUtils
import androidx.core.view.WindowCompat
import com.qingluan.schedule.data.AppSettings

val LocalPreferences = staticCompositionLocalOf { AppSettings() }
val LocalDarkAppearance = staticCompositionLocalOf { false }

internal enum class AppPalette(
    val key: String,
    val title: String,
    val seed: Color,
    val night: Color,
) {
    Jade("jade", "青玉", Color(0xFF08735F), Color(0xFF9FE3EB)),
    Iris("iris", "鸢尾", Color(0xFF6557A8), Color(0xFFC6BCFF)),
    Ocean("ocean", "海盐蓝", Color(0xFF32689A), Color(0xFFA2CAEF)),
    Rose("rose", "玫瑰", Color(0xFF9B526C), Color(0xFFF2B3CB)),
    Amber("amber", "琥珀", Color(0xFF8C641E), Color(0xFFE9C17E)),
    Slate("slate", "石墨", Color(0xFF52616D), Color(0xFFBACAD7));

    companion object {
        fun find(key: String) = entries.firstOrNull { it.key == key } ?: Jade
    }
}

internal fun dynamicBackdrop(colors: ColorScheme, dark: Boolean): ColorScheme =
    if (!dark) colors
    else
        colors.copy(
            surface = Color.Black,
            background = Color.Black,
            onSurface = Color.White,
            onBackground = Color.White,
            surfaceDim = Color.Black,
            surfaceBright = Color(0xFF252B2D),
            surfaceContainerLowest = Color(0xFF080B0C),
            surfaceContainerLow = Color(0xFF23292B),
            surfaceContainer = Color(0xFF292F31),
            surfaceContainerHigh = Color(0xFF303638),
            surfaceContainerHighest = Color(0xFF384043),
        )

internal fun appColorScheme(key: String, dark: Boolean): ColorScheme {
    val palette = AppPalette.find(key)
    val seed = palette.seed
    val primary = if (dark) palette.night else seed
    fun tone(color: Long, amount: Float = .035f) = lerp(Color(color), seed, amount)
    val surface = if (dark) Color(0xFF101416) else Color(0xFFF8FAFC)
    val container = if (dark) lerp(Color(0xFF20242B), seed, .16f) else lerp(Color.White, seed, .13f)
    val base = if (dark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = primary,
        onPrimary = if (dark) Color(0xFF111820) else Color.White,
        primaryContainer = container,
        onPrimaryContainer =
            if (dark) lerp(Color.White, primary, .16f) else lerp(Color(0xFF151B22), seed, .28f),
        secondary = if (dark) Color(0xFFC0C8D4) else Color(0xFF58616F),
        onSecondary = if (dark) Color(0xFF26303D) else Color.White,
        secondaryContainer = container,
        onSecondaryContainer = if (dark) Color(0xFFE4E9F3) else Color(0xFF273442),
        tertiary = if (dark) Color(0xFFB5C8FF) else Color(0xFF435CA9),
        onTertiary = if (dark) Color(0xFF102655) else Color.White,
        tertiaryContainer = if (dark) Color(0xFF293C67) else Color(0xFFDEE6FF),
        onTertiaryContainer = if (dark) Color(0xFFE0E8FF) else Color(0xFF172A58),
        background = surface,
        onBackground = if (dark) Color(0xFFE7EAF0) else Color(0xFF1A1D24),
        surface = surface,
        onSurface = if (dark) Color(0xFFE7EAF0) else Color(0xFF1A1D24),
        onSurfaceVariant = if (dark) Color(0xFFC0C6D1) else Color(0xFF535C69),
        surfaceDim = if (dark) tone(0xFF0D1015) else tone(0xFFDFE2E9),
        surfaceBright = if (dark) tone(0xFF353B45) else surface,
        surfaceContainerLowest = if (dark) tone(0xFF0B0E12) else Color.White,
        surfaceContainerLow = if (dark) Color(0xFF23292B) else Color(0xFFEDF1F2),
        surfaceContainer = if (dark) tone(0xFF21262F) else tone(0xFFEDF0F6),
        surfaceContainerHigh = if (dark) tone(0xFF292F39) else tone(0xFFE7EBF2),
        surfaceContainerHighest = if (dark) tone(0xFF343C47) else tone(0xFFE1E6EF),
        outline = if (dark) Color(0xFF65707F) else Color(0xFFA8B0BD),
        outlineVariant = if (dark) tone(0xFF3D4653) else tone(0xFFD6DDE6),
    )
}

internal data class CourseInk(
    val fill: Color,
    val border: Color,
    val text: Color,
    val detail: Color,
)

internal fun courseInk(color: Color, dark: Boolean, inactive: Boolean = false): CourseInk {
    if (inactive)
        return CourseInk(
            fill = Color(0xFF101820).copy(alpha = .40f),
            border = if (dark) Color.White.copy(alpha = .10f) else Color.Black.copy(alpha = .10f),
            text = if (dark) Color(0xFF7F8B9B) else Color(0xFF2B343E),
            detail = if (dark) Color(0xFF7F8EA2) else Color(0xFF243140),
        )
    val paletteIndex =
        com.qingluan.schedule.model.courseColors.indexOf(color.toArgb().toLong() and 0xFFFFFFFF)
    if (paletteIndex >= 0) {
        val fill =
            if (dark) Color(com.qingluan.schedule.model.nightCourseColors[paletteIndex]) else color
        val text = if (dark) Color.White else Color(0xFF202124)
        return CourseInk(fill, lerp(fill, text, .18f), text, text.copy(alpha = .88f))
    }
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(color.toArgb(), hsl)
    val saturation = hsl[1].coerceIn(.25f, .8f) * .55f
    fun tone(lightness: Float, factor: Float = 1f): Color =
        Color(ColorUtils.HSLToColor(floatArrayOf(hsl[0], saturation * factor, lightness)))
    val fill = tone(if (dark) .225f else .915f)
    val text = tone(if (dark) .94f else .18f, .55f)
    return CourseInk(
        fill = fill,
        border = tone(if (dark) .34f else .82f),
        text = text,
        detail = lerp(text, fill, .15f),
    )
}

@Composable
fun QingluanTheme(settings: AppSettings, content: @Composable () -> Unit) {
    val dark =
        when (settings.theme) {
            "dark" -> true
            "light" -> false
            else -> isSystemInDarkTheme()
        }
    val ctx = LocalContext.current
    val colors =
        remember(dark, settings.dynamicColor, settings.palette, ctx) {
            if (settings.dynamicColor) {
                dynamicBackdrop(
                    if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx),
                    dark,
                )
            } else appColorScheme(settings.palette, dark)
        }
    val view = LocalView.current
    if (!view.isInEditMode) {
        DisposableEffect(view, dark, colors.surface) {
            val activity =
                generateSequence(view.context) { (it as? ContextWrapper)?.baseContext }
                    .filterIsInstance<Activity>()
                    .firstOrNull()
            activity?.window?.let { window ->
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = !dark
                    isAppearanceLightNavigationBars = !dark
                }
                window.setBackgroundDrawable(ColorDrawable(colors.surface.toArgb()))
            }
            onDispose {}
        }
    }
    val density = LocalDensity.current
    CompositionLocalProvider(
        LocalPreferences provides settings,
        LocalDarkAppearance provides dark,
        LocalDensity provides Density(density.density, density.fontScale * settings.fontScale),
    ) {
        MaterialTheme(colorScheme = colors, typography = Typography()) {
            CompositionLocalProvider(LocalContentColor provides colors.onSurface, content = content)
        }
    }
}
