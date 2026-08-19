package com.vikaspokala.daybyday.ui.components

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vikaspokala.daybyday.ui.models.Recurrence
import com.vikaspokala.daybyday.ui.theme.DayByDayAccent
import com.vikaspokala.daybyday.ui.theme.DayByDayFontFamily
import com.vikaspokala.daybyday.ui.theme.DayByDayNeutralBorder
import com.vikaspokala.daybyday.ui.theme.DayByDayPrimaryText
import com.vikaspokala.daybyday.ui.theme.DayByDaySecondaryText

@Composable
fun ChooseRepeatModal(
    currentRepeat: Recurrence?,
    anchorDate: LocalDate? = null,
    anchorDayOfWeek: DayOfWeek? = anchorDate?.dayOfWeek,
    onDismiss: () -> Unit,
    onRepeatSelected: (Recurrence?) -> Unit
) {
    val OPTION_ONCE = "Does not repeat"
    val OPTION_DAILY = "Every day"
    val OPTION_INTERVAL = "Every N days"
    val OPTION_WEEKDAYS = "Selected weekdays"
    
    val ALL_OPTIONS = listOf(OPTION_ONCE, OPTION_DAILY, OPTION_INTERVAL, OPTION_WEEKDAYS)
    
    val initialBaseOption = when (currentRepeat) {
        null, is Recurrence.Once -> OPTION_ONCE
        is Recurrence.IntervalDays -> {
            if (currentRepeat.intervalDays == 1) OPTION_DAILY else OPTION_INTERVAL
        }
        is Recurrence.Weekdays -> OPTION_WEEKDAYS
    }

    var selectedOption by remember(currentRepeat) { mutableStateOf(initialBaseOption) }
    
    val initialInterval = (currentRepeat as? Recurrence.IntervalDays)?.intervalDays?.coerceAtLeast(2) ?: 2
    var intervalDays by remember(currentRepeat) { mutableIntStateOf(initialInterval) }

    val initialCustomDays = if (currentRepeat is Recurrence.Weekdays) {
        currentRepeat.days.map { DayOfWeek.of(it) }.toSet()
    } else {
        anchorDayOfWeek?.let { setOf(it) } ?: setOf(DayOfWeek.MONDAY)
    }
    var customDays by remember(currentRepeat) { mutableStateOf(initialCustomDays) }

    var endDate by remember(currentRepeat) { mutableStateOf(currentRepeat?.endDate) }
    var showEndDatePicker by remember { mutableStateOf(false) }
    var showEndDateWarning by remember { mutableStateOf(false) }

    if (showEndDatePicker) {
        ChooseDateScreen(
            initialDateString = (endDate ?: anchorDate ?: LocalDate.now()).toString(),
            onDateSelected = { pickedDate ->
                if (anchorDate != null && pickedDate.isBefore(anchorDate)) {
                    showEndDateWarning = true
                } else {
                    endDate = pickedDate
                }
                showEndDatePicker = false
            },
            onNavigateBack = { showEndDatePicker = false }
        )
        return
    }

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
                .padding(horizontal = 24.dp, vertical = 24.dp)
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
                    .padding(20.dp)
            ) {
                // Header (Title & Close ×)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Repeat",
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
                        .heightIn(max = 400.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    ALL_OPTIONS.forEach { option ->
                        val isSelected = option == selectedOption
                        val optionLabel = if (option == OPTION_INTERVAL) {
                            if (isSelected) "Every $intervalDays days" else "Every N days"
                        } else {
                            option
                        }

                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedOption = option },
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) Color(0xFFFBF7F2) else Color.Transparent
                        ) {
                            Column {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = optionLabel,
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
                                        shape = CircleShape,
                                        color = if (isSelected) DayByDayAccent else Color.Transparent,
                                        border = BorderStroke(1.5.dp, if (isSelected) DayByDayAccent else Color(0xFFCCC0B5))
                                    ) {
                                        if (isSelected) {
                                            Box(
                                                modifier = Modifier.fillMaxSize(),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(6.dp)
                                                        .background(Color.White, CircleShape)
                                                )
                                            }
                                        }
                                    }
                                }

                                // Stepper for Every N Days (Single horizontal line)
                                if (isSelected && option == OPTION_INTERVAL) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Repeat every",
                                            style = TextStyle(
                                                fontFamily = DayByDayFontFamily,
                                                fontWeight = FontWeight.Normal,
                                                fontSize = 13.sp,
                                                color = DayByDaySecondaryText
                                            )
                                        )

                                        Spacer(modifier = Modifier.width(6.dp))

                                        // Decrement Button with 44dp touch target
                                        Box(
                                            modifier = Modifier
                                                .size(44.dp)
                                                .clickable(
                                                    enabled = intervalDays > 2,
                                                    indication = null,
                                                    interactionSource = remember { MutableInteractionSource() }
                                                ) {
                                                    if (intervalDays > 2) intervalDays--
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Surface(
                                                modifier = Modifier.size(28.dp),
                                                shape = CircleShape,
                                                color = if (intervalDays > 2) Color(0xFFF4F0EC) else Color(0xFFFAF7F4),
                                                border = BorderStroke(1.dp, DayByDayNeutralBorder)
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Text(
                                                        text = "−",
                                                        style = TextStyle(
                                                            fontFamily = DayByDayFontFamily,
                                                            fontWeight = FontWeight.SemiBold,
                                                            fontSize = 15.sp,
                                                            color = if (intervalDays > 2) DayByDayPrimaryText else DayByDaySecondaryText.copy(alpha = 0.35f)
                                                        )
                                                    )
                                                }
                                            }
                                        }

                                        Text(
                                            text = "$intervalDays",
                                            style = TextStyle(
                                                fontFamily = DayByDayFontFamily,
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 14.5.sp,
                                                color = DayByDayPrimaryText,
                                                textAlign = TextAlign.Center
                                            ),
                                            modifier = Modifier.width(20.dp)
                                        )

                                        // Increment Button with 44dp touch target
                                        Box(
                                            modifier = Modifier
                                                .size(44.dp)
                                                .clickable(
                                                    indication = null,
                                                    interactionSource = remember { MutableInteractionSource() }
                                                ) { intervalDays++ },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Surface(
                                                modifier = Modifier.size(28.dp),
                                                shape = CircleShape,
                                                color = Color(0xFFF4F0EC),
                                                border = BorderStroke(1.dp, DayByDayNeutralBorder)
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Text(
                                                        text = "+",
                                                        style = TextStyle(
                                                            fontFamily = DayByDayFontFamily,
                                                            fontWeight = FontWeight.SemiBold,
                                                            fontSize = 15.sp,
                                                            color = DayByDayPrimaryText
                                                        )
                                                    )
                                                }
                                            }
                                        }

                                        Spacer(modifier = Modifier.width(6.dp))

                                        Text(
                                            text = "days",
                                            style = TextStyle(
                                                fontFamily = DayByDayFontFamily,
                                                fontWeight = FontWeight.Normal,
                                                fontSize = 13.sp,
                                                color = DayByDaySecondaryText
                                            )
                                        )
                                    }
                                }

                                // Day chips for Selected Weekdays
                                if (isSelected && option == OPTION_WEEKDAYS) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 8.dp, vertical = 8.dp)
                                            .padding(bottom = 8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        val daysOrder = listOf(
                                            DayOfWeek.SUNDAY,
                                            DayOfWeek.MONDAY,
                                            DayOfWeek.TUESDAY,
                                            DayOfWeek.WEDNESDAY,
                                            DayOfWeek.THURSDAY,
                                            DayOfWeek.FRIDAY,
                                            DayOfWeek.SATURDAY
                                        )
                                        
                                        daysOrder.forEach { day ->
                                            val isDaySelected = customDays.contains(day)
                                            Surface(
                                                modifier = Modifier
                                                    .size(32.dp)
                                                    .clickable {
                                                        customDays = if (isDaySelected) {
                                                            customDays - day
                                                        } else {
                                                            customDays + day
                                                        }
                                                    },
                                                shape = CircleShape,
                                                color = if (isDaySelected) DayByDayAccent else Color(0xFFF4F0EC),
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Text(
                                                        text = day.name.take(1),
                                                        style = TextStyle(
                                                            fontFamily = DayByDayFontFamily,
                                                            fontWeight = if (isDaySelected) FontWeight.Medium else FontWeight.Normal,
                                                            fontSize = 13.sp,
                                                            color = if (isDaySelected) Color.White else DayByDaySecondaryText,
                                                            textAlign = TextAlign.Center
                                                        )
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Optional End Date section (Stacked clean layout)
                    if (selectedOption != OPTION_ONCE) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            color = Color(0xFFFBF7F2),
                            border = BorderStroke(1.dp, DayByDayNeutralBorder)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "END DATE",
                                        style = TextStyle(
                                            fontFamily = DayByDayFontFamily,
                                            fontWeight = FontWeight.Medium,
                                            fontSize = 11.sp,
                                            color = DayByDaySecondaryText
                                        )
                                    )
                                    if (endDate == null) {
                                        Text(
                                            text = "+ Add",
                                            style = TextStyle(
                                                fontFamily = DayByDayFontFamily,
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 13.sp,
                                                color = DayByDayAccent
                                            ),
                                            modifier = Modifier
                                                .clickable { showEndDatePicker = true }
                                                .padding(vertical = 2.dp, horizontal = 4.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(4.dp))

                                Text(
                                    text = if (endDate != null) {
                                        val endFormatter = DateTimeFormatter.ofPattern("EEE, d MMM yyyy", Locale.getDefault())
                                        endDate?.format(endFormatter).orEmpty()
                                    } else {
                                        "No end date"
                                    },
                                    style = TextStyle(
                                        fontFamily = DayByDayFontFamily,
                                        fontWeight = FontWeight.Medium,
                                        fontSize = 14.sp,
                                        color = DayByDayPrimaryText
                                    )
                                )

                                if (endDate != null) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Remove",
                                            style = TextStyle(
                                                fontFamily = DayByDayFontFamily,
                                                fontWeight = FontWeight.Medium,
                                                fontSize = 13.sp,
                                                color = Color(0xFFC04B4B)
                                            ),
                                            modifier = Modifier
                                                .clickable { endDate = null }
                                                .padding(vertical = 4.dp, horizontal = 2.dp)
                                        )

                                        Text(
                                            text = "Change",
                                            style = TextStyle(
                                                fontFamily = DayByDayFontFamily,
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 13.sp,
                                                color = DayByDayAccent
                                            ),
                                            modifier = Modifier
                                                .clickable { showEndDatePicker = true }
                                                .padding(vertical = 4.dp, horizontal = 2.dp)
                                        )
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
                    
                    val isDoneEnabled = when (selectedOption) {
                        OPTION_WEEKDAYS -> customDays.isNotEmpty()
                        OPTION_INTERVAL -> intervalDays >= 2
                        else -> true
                    }

                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                            .then(
                                if (isDoneEnabled) Modifier.clickable {
                                    val validEndDate = if (selectedOption == OPTION_ONCE) null else endDate?.takeIf { anchorDate == null || !it.isBefore(anchorDate) }
                                    val result: Recurrence = when (selectedOption) {
                                        OPTION_ONCE -> Recurrence.Once
                                        OPTION_DAILY -> Recurrence.IntervalDays(1, validEndDate)
                                        OPTION_INTERVAL -> Recurrence.IntervalDays(intervalDays.coerceAtLeast(2), validEndDate)
                                        OPTION_WEEKDAYS -> Recurrence.Weekdays(customDays.map { it.value }.toSet(), validEndDate)
                                        else -> Recurrence.Once
                                    }
                                    onRepeatSelected(result)
                                } else Modifier
                            ),
                        shape = RoundedCornerShape(18.dp),
                        color = if (isDoneEnabled) DayByDayAccent else Color(0xFFF4F0EC)
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
                                    color = if (isDoneEnabled) Color.White else Color(0xFFA79B91)
                                )
                            )
                        }
                    }
                }
            }
        }

        // Warning when choosing End Date before Start Date
        if (showEndDateWarning) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.25f))
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) { showEndDateWarning = false },
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
                    shape = RoundedCornerShape(20.dp),
                    color = Color.White,
                    border = BorderStroke(1.dp, DayByDayNeutralBorder)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "End date cannot be before start date.",
                            style = TextStyle(
                                fontFamily = DayByDayFontFamily,
                                fontWeight = FontWeight.Medium,
                                fontSize = 15.sp,
                                lineHeight = 20.sp,
                                color = DayByDayPrimaryText,
                                textAlign = TextAlign.Center
                            )
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Surface(
                            modifier = Modifier
                                .height(44.dp)
                                .fillMaxWidth()
                                .clickable { showEndDateWarning = false },
                            shape = RoundedCornerShape(14.dp),
                            color = DayByDayAccent
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = "OK",
                                    style = TextStyle(
                                        fontFamily = DayByDayFontFamily,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 14.sp,
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
}
