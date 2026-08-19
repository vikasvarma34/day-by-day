package com.vikaspokala.daybyday.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColorScheme = lightColorScheme(
    primary = DayByDayAccent,
    onPrimary = DayByDaySurface,
    primaryContainer = DayByDayBackground,
    onPrimaryContainer = DayByDayStrongAccent,
    secondary = DayByDayStrongAccent,
    onSecondary = DayByDaySurface,
    background = DayByDayBackground,
    onBackground = DayByDayPrimaryText,
    surface = DayByDaySurface,
    onSurface = DayByDayPrimaryText,
    surfaceVariant = DayByDayBackground,
    onSurfaceVariant = DayByDaySecondaryText,
    outline = DayByDayNeutralBorder,
    error = DayByDayDestructive,
    onError = DayByDaySurface
)

@Composable
fun DayByDayTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        typography = Typography,
        content = content
    )
}
