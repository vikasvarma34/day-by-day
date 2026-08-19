package com.vikaspokala.daybyday.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.vikaspokala.daybyday.R

val DayByDayFontFamily = FontFamily(
    Font(R.font.dm_sans_regular, FontWeight.Normal),
    Font(R.font.dm_sans_medium, FontWeight.Medium),
    Font(R.font.dm_sans_semibold, FontWeight.SemiBold),
    Font(R.font.dm_sans_bold, FontWeight.Bold)
)

val Typography = Typography(
    displayLarge = TextStyle(
        fontFamily = DayByDayFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        lineHeight = 40.sp,
        color = DayByDayPrimaryText
    ),
    titleLarge = TextStyle(
        fontFamily = DayByDayFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        color = DayByDayPrimaryText
    ),
    titleMedium = TextStyle(
        fontFamily = DayByDayFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 24.sp,
        color = DayByDayPrimaryText
    ),
    bodyLarge = TextStyle(
        fontFamily = DayByDayFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        color = DayByDayPrimaryText
    ),
    bodyMedium = TextStyle(
        fontFamily = DayByDayFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        color = DayByDaySecondaryText
    ),
    labelLarge = TextStyle(
        fontFamily = DayByDayFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        color = DayByDayPrimaryText
    )
)
