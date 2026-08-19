package com.vikaspokala.daybyday.ui.components

import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vikaspokala.daybyday.ui.theme.DayByDayBackground
import com.vikaspokala.daybyday.ui.theme.DayByDayFontFamily
import com.vikaspokala.daybyday.ui.theme.DayByDayNeutralBorder
import com.vikaspokala.daybyday.ui.theme.DayByDayPrimaryText
import com.vikaspokala.daybyday.ui.theme.DayByDaySurface

@Composable
fun ChooseDateScreen(
    initialDateString: String?,
    onDateSelected: (LocalDate) -> Unit,
    onNavigateBack: () -> Unit
) {
    val initialDate = remember(initialDateString) { parseDate(initialDateString) }
    var selectedDate by remember { mutableStateOf(initialDate ?: LocalDate.now()) }
    var currentMonth by remember { mutableStateOf(YearMonth.from(selectedDate)) }

    val monthTitleFormatter = remember { DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DayByDayBackground)
            .padding(horizontal = 32.dp, vertical = 48.dp)
    ) {
        // Header
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
        ) {
            Surface(
                modifier = Modifier
                    .size(34.dp)
                    .align(Alignment.CenterStart)
                    .clickable { onNavigateBack() },
                shape = CircleShape,
                color = Color(0xFFFFFDFC),
                border = BorderStroke(1.dp, DayByDayNeutralBorder)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "‹",
                        style = TextStyle(
                            fontFamily = DayByDayFontFamily,
                            fontWeight = FontWeight.W500,
                            fontSize = 24.sp,
                            lineHeight = 31.sp,
                            color = DayByDayPrimaryText
                        )
                    )
                }
            }

            Text(
                text = "Choose date",
                style = TextStyle(
                    fontFamily = DayByDayFontFamily,
                    fontWeight = FontWeight.W600,
                    fontSize = 22.sp,
                    lineHeight = 29.sp,
                    color = DayByDayPrimaryText
                ),
                modifier = Modifier.align(Alignment.Center)
            )

            Surface(
                modifier = Modifier
                    .size(width = 64.dp, height = 32.dp)
                    .align(Alignment.CenterEnd),
                shape = CircleShape,
                color = DayByDaySurface,
                border = BorderStroke(1.dp, DayByDayNeutralBorder)
            ) {
                TextButton(
                    onClick = {
                        val today = LocalDate.now()
                        onDateSelected(today)
                    },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "Today",
                        style = TextStyle(
                            fontFamily = DayByDayFontFamily,
                            fontWeight = FontWeight.W400,
                            fontSize = 12.5.sp,
                            color = DayByDayPrimaryText
                        )
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Month Navigation Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Surface(
                shape = CircleShape,
                color = DayByDaySurface,
                border = BorderStroke(1.dp, DayByDayNeutralBorder)
            ) {
                IconButton(onClick = { currentMonth = currentMonth.minusMonths(1) }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                        contentDescription = "Previous month",
                        tint = DayByDayPrimaryText
                    )
                }
            }

            Text(
                text = currentMonth.format(monthTitleFormatter),
                style = TextStyle(
                    fontFamily = DayByDayFontFamily,
                    fontWeight = FontWeight.W500,
                    fontSize = 16.sp,
                    color = DayByDayPrimaryText
                )
            )

            Surface(
                shape = CircleShape,
                color = DayByDaySurface,
                border = BorderStroke(1.dp, DayByDayNeutralBorder)
            ) {
                IconButton(onClick = { currentMonth = currentMonth.plusMonths(1) }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = "Next month",
                        tint = DayByDayPrimaryText
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Month Calendar Grid
        MonthCalendar(
            month = currentMonth,
            selectedDate = selectedDate,
            todayDate = LocalDate.now(),
            hasImportantTask = { false },
            onDateSelected = { date ->
                selectedDate = date
                onDateSelected(date)
            }
        )
    }
}

private fun parseDate(dateStr: String?): LocalDate? {
    if (dateStr.isNullOrEmpty()) return null
    return try {
        val currentYear = LocalDate.now().year
        val fullStr = if (!dateStr.contains(currentYear.toString())) "$dateStr $currentYear" else dateStr
        val formatter = DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", Locale.getDefault())
        LocalDate.parse(fullStr, formatter)
    } catch (e: Exception) {
        null
    }
}
