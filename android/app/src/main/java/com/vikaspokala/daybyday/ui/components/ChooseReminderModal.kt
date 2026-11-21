package com.vikaspokala.daybyday.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vikaspokala.daybyday.ui.planner.parseReminders
import com.vikaspokala.daybyday.ui.planner.toDisplayReminder
import com.vikaspokala.daybyday.ui.theme.DayByDayAccent
import com.vikaspokala.daybyday.ui.theme.DayByDayFontFamily
import com.vikaspokala.daybyday.ui.theme.DayByDayNeutralBorder
import com.vikaspokala.daybyday.ui.theme.DayByDayPrimaryText
import com.vikaspokala.daybyday.ui.theme.DayByDaySecondaryText

val REMINDER_OPTIONS = listOf(
    "No reminder",
    "At task time",
    "5 minutes before",
    "10 minutes before",
    "15 minutes before",
    "30 minutes before",
    "1 hour before",
    "1 day before",
    "2 days before"
)

@Composable
fun ChooseReminderModal(
    currentReminder: String?,
    onDismiss: () -> Unit,
    onReminderSelected: (String?) -> Unit
) {
    val initialSelection = parseReminders(currentReminder)
        .mapNotNull { it.toDisplayReminder() }.toSet().ifEmpty { setOf("No reminder") }
    var selectedOptions by remember(currentReminder) { mutableStateOf(initialSelection) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.18f))
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) { onDismiss() },
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .padding(horizontal = 32.dp, vertical = 24.dp)
                .fillMaxWidth()
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) { /* Consume clicks inside modal card */ },
            shape = RoundedCornerShape(24.dp),
            color = Color.White,
            border = BorderStroke(1.dp, DayByDayNeutralBorder)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                // Header (Title & Close ×)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Reminder",
                        style = TextStyle(
                            fontFamily = DayByDayFontFamily,
                            fontWeight = FontWeight.W600,
                            fontSize = 22.sp,
                            lineHeight = 29.sp,
                            color = DayByDayPrimaryText
                        )
                    )

                    Text(
                        text = "×",
                        style = TextStyle(
                            fontFamily = DayByDayFontFamily,
                            fontWeight = FontWeight.W400,
                            fontSize = 26.sp,
                            color = DayByDaySecondaryText
                        ),
                        modifier = Modifier.clickable { onDismiss() }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Scrollable Options List
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 340.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    REMINDER_OPTIONS.forEach { option ->
                        val isSelected = selectedOptions.contains(option)
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (option == "No reminder") {
                                        selectedOptions = setOf("No reminder")
                                    } else {
                                        val next = (selectedOptions - "No reminder").toMutableSet()
                                        if (next.contains(option)) {
                                            next.remove(option)
                                            if (next.isEmpty()) {
                                                next.add("No reminder")
                                            }
                                        } else {
                                            next.add(option)
                                        }
                                        selectedOptions = next
                                    }
                                },
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) Color(0xFFFBF7F2) else Color.Transparent
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = option,
                                    style = TextStyle(
                                        fontFamily = DayByDayFontFamily,
                                        fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
                                        fontSize = 14.sp,
                                        lineHeight = 18.sp,
                                        color = if (isSelected) DayByDayPrimaryText else DayByDaySecondaryText
                                    )
                                )

                                Surface(
                                    modifier = Modifier.size(20.dp),
                                    shape = RoundedCornerShape(4.dp),
                                    color = if (isSelected) DayByDayAccent else Color.Transparent,
                                    border = BorderStroke(1.5.dp, if (isSelected) DayByDayAccent else Color(0xFFCCC0B5))
                                ) {
                                    if (isSelected) {
                                        Box(
                                            modifier = Modifier.fillMaxSize(),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Canvas(modifier = Modifier.size(12.dp)) {
                                                val stroke = 2.dp.toPx()
                                                drawLine(Color.White, Offset(size.width * 0.12f, size.height * 0.52f), Offset(size.width * 0.40f, size.height * 0.78f), stroke, cap = StrokeCap.Round)
                                                drawLine(Color.White, Offset(size.width * 0.40f, size.height * 0.78f), Offset(size.width * 0.88f, size.height * 0.22f), stroke, cap = StrokeCap.Round)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Action Buttons (Cancel & Done)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                            .clickable { onDismiss() },
                        shape = RoundedCornerShape(18.dp),
                        color = Color.White,
                        border = BorderStroke(1.dp, DayByDayNeutralBorder)
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Cancel",
                                style = TextStyle(
                                    fontFamily = DayByDayFontFamily,
                                    fontWeight = FontWeight.W400,
                                    fontSize = 13.5.sp,
                                    color = DayByDayPrimaryText
                                )
                            )
                        }
                    }

                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                            .clickable {
                                val orderedOptions = REMINDER_OPTIONS.filter { it != "No reminder" && selectedOptions.contains(it) }
                                val result = if (orderedOptions.isEmpty() || selectedOptions.contains("No reminder")) null else orderedOptions.joinToString(", ")
                                onReminderSelected(result)
                            },
                        shape = RoundedCornerShape(18.dp),
                        color = DayByDayAccent
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Done",
                                style = TextStyle(
                                    fontFamily = DayByDayFontFamily,
                                    fontWeight = FontWeight.W600,
                                    fontSize = 13.5.sp,
                                    color = Color.White
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}
