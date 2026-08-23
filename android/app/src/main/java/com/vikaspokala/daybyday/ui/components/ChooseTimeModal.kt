package com.vikaspokala.daybyday.ui.components

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vikaspokala.daybyday.ui.theme.DayByDayAccent
import com.vikaspokala.daybyday.ui.theme.DayByDayDestructive
import com.vikaspokala.daybyday.ui.theme.DayByDayFontFamily
import com.vikaspokala.daybyday.ui.theme.DayByDayNeutralBorder
import com.vikaspokala.daybyday.ui.theme.DayByDayPrimaryText
import com.vikaspokala.daybyday.ui.theme.DayByDaySecondaryText
import com.vikaspokala.daybyday.ui.theme.DayByDayStrongAccent

@Composable
fun ChooseTimeModal(
    initialTimeString: String?,
    onDismiss: () -> Unit,
    onTimeSelected: (String) -> Unit,
    onClearTime: (() -> Unit)? = null
) {
    val initialHour = parseHour(initialTimeString)
    val initialMinute = parseMinute(initialTimeString)

    var hourField by remember(initialTimeString) {
        val str = initialHour.toString()
        mutableStateOf(TextFieldValue(text = str, selection = TextRange(str.length)))
    }
    var minuteField by remember(initialTimeString) {
        val str = "%02d".format(initialMinute)
        mutableStateOf(TextFieldValue(text = str, selection = TextRange(str.length)))
    }
    var amPm by remember(initialTimeString) { mutableStateOf(parseAmPm(initialTimeString)) }

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
                .padding(horizontal = 32.dp)
                .fillMaxWidth()
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) { /* Consume clicks */ },
            shape = RoundedCornerShape(24.dp),
            color = Color.White,
            border = BorderStroke(1.dp, DayByDayNeutralBorder)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                // Header Row (Title & Close ×)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Choose time",
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

                Spacer(modifier = Modifier.height(20.dp))

                // Time Pickers Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    // Hour Box with Up/Down buttons
                    TimeNumberBox(
                        value = hourField,
                        onValueChange = { newValue ->
                            val digits = newValue.text.filter { it.isDigit() }
                            if (digits.length <= 2) {
                                val intVal = digits.toIntOrNull()
                                if (digits.isEmpty() || (intVal != null && intVal in 0..12)) {
                                    hourField = newValue.copy(text = digits)
                                }
                            }
                        },
                        onIncrement = {
                            val curr = hourField.text.toIntOrNull() ?: initialHour
                            val next = if (curr >= 12) 1 else (if (curr <= 0) 1 else curr + 1)
                            val str = next.toString()
                            hourField = TextFieldValue(text = str, selection = TextRange(str.length))
                        },
                        onDecrement = {
                            val curr = hourField.text.toIntOrNull() ?: initialHour
                            val next = if (curr <= 1) 12 else curr - 1
                            val str = next.toString()
                            hourField = TextFieldValue(text = str, selection = TextRange(str.length))
                        },
                        onFocusLost = {
                            val normalized = normalizeHour(hourField.text, fallback = initialHour)
                            val str = normalized.toString()
                            hourField = TextFieldValue(text = str, selection = TextRange(str.length))
                        }
                    )

                    Text(
                        text = ":",
                        style = TextStyle(
                            fontFamily = DayByDayFontFamily,
                            fontWeight = FontWeight.W600,
                            fontSize = 28.sp,
                            color = DayByDayPrimaryText
                        ),
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )

                    // Minute Box with Up/Down buttons
                    TimeNumberBox(
                        value = minuteField,
                        onValueChange = { newValue ->
                            val digits = newValue.text.filter { it.isDigit() }
                            if (digits.length <= 2) {
                                val intVal = digits.toIntOrNull()
                                if (digits.isEmpty() || (intVal != null && intVal in 0..59)) {
                                    minuteField = newValue.copy(text = digits)
                                }
                            }
                        },
                        onIncrement = {
                            val curr = minuteField.text.toIntOrNull() ?: initialMinute
                            val next = (curr + 5) % 60
                            val str = "%02d".format(next)
                            minuteField = TextFieldValue(text = str, selection = TextRange(str.length))
                        },
                        onDecrement = {
                            val curr = minuteField.text.toIntOrNull() ?: initialMinute
                            val next = if (curr < 5) 55 else curr - 5
                            val str = "%02d".format(next)
                            minuteField = TextFieldValue(text = str, selection = TextRange(str.length))
                        },
                        onFocusLost = {
                            val normalized = normalizeMinute(minuteField.text, fallback = initialMinute)
                            val str = "%02d".format(normalized)
                            minuteField = TextFieldValue(text = str, selection = TextRange(str.length))
                        }
                    )

                    Spacer(modifier = Modifier.width(16.dp))

                    // AM / PM Segmented Controls
                    Column(
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        AmPmPill(
                            label = "AM",
                            isSelected = amPm == "AM",
                            onClick = { amPm = "AM" }
                        )
                        AmPmPill(
                            label = "PM",
                            isSelected = amPm == "PM",
                            onClick = { amPm = "PM" }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

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
                                val finalHour = normalizeHour(hourField.text, fallback = initialHour)
                                val finalMinute = normalizeMinute(minuteField.text, fallback = initialMinute)
                                onTimeSelected(formatTimeSelection(finalHour, finalMinute, amPm))
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

                if (!initialTimeString.isNullOrEmpty() && onClearTime != null) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onClearTime() }
                            .padding(vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Remove time",
                            style = TextStyle(
                                fontFamily = DayByDayFontFamily,
                                fontWeight = FontWeight.Medium,
                                fontSize = 14.sp,
                                color = DayByDayDestructive
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TimeNumberBox(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit,
    onFocusLost: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "▲",
            style = TextStyle(fontSize = 12.sp, color = DayByDaySecondaryText),
            modifier = Modifier
                .clickable { onIncrement() }
                .padding(vertical = 4.dp)
        )

        Surface(
            modifier = Modifier.size(60.dp),
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFFF4F0EC),
            border = BorderStroke(1.dp, DayByDayNeutralBorder)
        ) {
            Box(contentAlignment = Alignment.Center) {
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.onFocusChanged { focusState ->
                        if (!focusState.isFocused) {
                            onFocusLost()
                        }
                    },
                    textStyle = TextStyle(
                        fontFamily = DayByDayFontFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 24.sp,
                        color = DayByDayPrimaryText,
                        textAlign = TextAlign.Center
                    )
                )
            }
        }

        Text(
            text = "▼",
            style = TextStyle(fontSize = 12.sp, color = DayByDaySecondaryText),
            modifier = Modifier
                .clickable { onDecrement() }
                .padding(vertical = 4.dp)
        )
    }
}

@Composable
private fun AmPmPill(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .size(width = 54.dp, height = 30.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) Color(0xFFF2E0D0) else Color(0xFFF4F0EC),
        border = BorderStroke(1.dp, if (isSelected) DayByDayStrongAccent else DayByDayNeutralBorder)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = label,
                style = TextStyle(
                    fontFamily = DayByDayFontFamily,
                    fontWeight = FontWeight.Medium,
                    fontSize = 12.5.sp,
                    color = if (isSelected) DayByDayStrongAccent else DayByDaySecondaryText
                )
            )
        }
    }
}

fun normalizeHour(input: String, fallback: Int = 10): Int {
    val parsed = input.trim().toIntOrNull()
    return if (parsed != null && parsed in 1..12) parsed else fallback
}

fun normalizeMinute(input: String, fallback: Int = 0): Int {
    val parsed = input.trim().toIntOrNull()
    return if (parsed != null && parsed in 0..59) parsed else fallback
}

fun formatTimeSelection(hour: Int, minute: Int, amPm: String): String {
    val normalizedHour = normalizeHour(hour.toString())
    val normalizedMinute = normalizeMinute(minute.toString())
    val formattedMinute = "%02d".format(normalizedMinute)
    val normalizedAmPm = if (amPm.equals("PM", ignoreCase = true)) "PM" else "AM"
    return "$normalizedHour:$formattedMinute $normalizedAmPm"
}

fun parseHour(timeStr: String?): Int {
    if (timeStr.isNullOrEmpty()) return 10
    return try {
        val parts = timeStr.trim().split(" ")
        val hourMin = parts[0].split(":")
        val h = hourMin[0].toInt()
        if (h in 1..12) h else 10
    } catch (e: Exception) {
        10
    }
}

fun parseMinute(timeStr: String?): Int {
    if (timeStr.isNullOrEmpty()) return 30
    return try {
        val parts = timeStr.trim().split(" ")
        val hourMin = parts[0].split(":")
        val m = hourMin[1].toInt()
        if (m in 0..59) m else 30
    } catch (e: Exception) {
        30
    }
}

fun parseAmPm(timeStr: String?): String {
    if (timeStr.isNullOrEmpty()) return "AM"
    return try {
        val parts = timeStr.trim().split(" ")
        if (parts.size > 1 && parts[1].equals("PM", ignoreCase = true)) "PM" else "AM"
    } catch (e: Exception) {
        "AM"
    }
}
