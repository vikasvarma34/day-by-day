package com.vikaspokala.daybyday.ui.components

import java.time.LocalDate
import java.time.YearMonth
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.vikaspokala.daybyday.ui.theme.DayByDayAccent
import com.vikaspokala.daybyday.ui.theme.DayByDayNeutralBorder
import com.vikaspokala.daybyday.ui.theme.DayByDayPrimaryText
import com.vikaspokala.daybyday.ui.theme.DayByDaySecondaryText
import com.vikaspokala.daybyday.ui.theme.DayByDaySurface

@Composable
fun MonthCalendar(
    month: YearMonth,
    selectedDate: LocalDate,
    todayDate: LocalDate,
    hasImportantTask: (LocalDate) -> Boolean,
    onDateSelected: (LocalDate) -> Unit,
    modifier: Modifier = Modifier
) {
    val weekdays = remember { listOf("S", "M", "T", "W", "T", "F", "S") }

    val (rows, totalCells, emptyOffset) = remember(month) {
        val firstDayOfMonth = month.atDay(1)
        val firstDayOfWeek = firstDayOfMonth.dayOfWeek.value // Mon=1..Sun=7
        val offset = if (firstDayOfWeek == 7) 0 else firstDayOfWeek
        val daysInMonth = month.lengthOfMonth()
        val total = offset + daysInMonth
        val r = (total + 6) / 7
        Triple(r, total, offset)
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = DayByDaySurface,
        border = BorderStroke(1.dp, DayByDayNeutralBorder)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Weekday Header (S M T W T F S)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                weekdays.forEach { dayLetter ->
                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = dayLetter,
                            style = MaterialTheme.typography.bodyMedium,
                            color = DayByDaySecondaryText
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Calendar Days Grid
            for (row in 0 until rows) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    for (col in 0 until 7) {
                        val cellIndex = row * 7 + col
                        if (cellIndex < emptyOffset || cellIndex >= totalCells) {
                            Box(modifier = Modifier.weight(1f))
                        } else {
                            val dayNumber = cellIndex - emptyOffset + 1
                            val date = month.atDay(dayNumber)
                            val isSelected = (date == selectedDate)
                            val isToday = (date == todayDate)
                            val hasImportant = hasImportantTask(date)

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(1f)
                                    .padding(2.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                DayCell(
                                    dayNumber = dayNumber,
                                    isSelected = isSelected,
                                    isToday = isToday,
                                    hasImportant = hasImportant,
                                    onClick = { onDateSelected(date) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    dayNumber: Int,
    isSelected: Boolean,
    isToday: Boolean,
    hasImportant: Boolean,
    onClick: () -> Unit
) {
    val backgroundColor = when {
        isSelected -> DayByDayAccent
        else -> Color.Transparent
    }

    val textColor = when {
        isSelected -> Color.White
        isToday -> DayByDayAccent
        else -> DayByDayPrimaryText
    }

    val modifier = Modifier
        .size(36.dp)
        .clip(CircleShape)
        .background(backgroundColor)
        .then(
            if (isToday && !isSelected) {
                Modifier.border(1.dp, DayByDayAccent, CircleShape)
            } else {
                Modifier
            }
        )
        .clickable(onClick = onClick)

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = dayNumber.toString(),
            style = MaterialTheme.typography.bodyMedium,
            color = textColor
        )

        if (hasImportant) {
            val dotColor = if (isSelected) Color.White else DayByDayAccent
            Box(
                modifier = Modifier
                    .size(4.dp)
                    .clip(CircleShape)
                    .background(dotColor)
            )
        }
    }
}
