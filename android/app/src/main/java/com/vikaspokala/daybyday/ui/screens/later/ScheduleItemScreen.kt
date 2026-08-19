package com.vikaspokala.daybyday.ui.screens.later

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vikaspokala.daybyday.ui.components.ChooseDateScreen
import com.vikaspokala.daybyday.ui.components.ChooseReminderModal
import com.vikaspokala.daybyday.ui.components.ChooseTimeModal
import com.vikaspokala.daybyday.ui.theme.DayByDayAccent
import com.vikaspokala.daybyday.ui.theme.DayByDayBackground
import com.vikaspokala.daybyday.ui.theme.DayByDayFontFamily
import com.vikaspokala.daybyday.ui.theme.DayByDayNeutralBorder
import com.vikaspokala.daybyday.ui.theme.DayByDayPrimaryText
import com.vikaspokala.daybyday.ui.theme.DayByDaySecondaryText
import com.vikaspokala.daybyday.ui.theme.DayByDayStrongAccent
import com.vikaspokala.daybyday.ui.theme.DayByDaySurface
import com.vikaspokala.daybyday.ui.components.ChooseRepeatModal
import com.vikaspokala.daybyday.ui.models.toDisplayString

@Composable
fun ScheduleItemScreen(
    initialTitle: String,
    initialNote: String? = null,
    initialIsImportant: Boolean = false,
    initialRecurrence: com.vikaspokala.daybyday.ui.models.Recurrence? = null,
    onNavigateBack: () -> Unit,
    onSchedule: (title: String, note: String?, date: LocalDate, time: String?, reminder: String?, recurrence: com.vikaspokala.daybyday.ui.models.Recurrence?, isImportant: Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    var titleText by remember(initialTitle) { mutableStateOf(initialTitle) }
    var noteText by remember(initialNote) { mutableStateOf(initialNote.orEmpty()) }
    var isImportant by remember(initialIsImportant) { mutableStateOf(initialIsImportant) }

    var selectedDate by remember { mutableStateOf<LocalDate?>(null) }
    var timeString by remember { mutableStateOf<String?>(null) }
    var reminderString by remember { mutableStateOf<String?>(null) }
    var recurrence by remember { mutableStateOf<com.vikaspokala.daybyday.ui.models.Recurrence?>(initialRecurrence) }

    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePickerModal by remember { mutableStateOf(false) }
    var showReminderModal by remember { mutableStateOf(false) }
    var showRepeatModal by remember { mutableStateOf(false) }
    var showNoTimeWarning by remember { mutableStateOf(false) }
    var showNoDateWarning by remember { mutableStateOf(false) }
    var showPastDateWarning by remember { mutableStateOf(false) }
    var showEndDateWarning by remember { mutableStateOf(false) }

    val dateFormatter = remember { DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.getDefault()) }

    BackHandler(enabled = showDatePicker || showTimePickerModal || showReminderModal || showRepeatModal || showNoTimeWarning || showNoDateWarning || showPastDateWarning || showEndDateWarning) {
        when {
            showDatePicker -> showDatePicker = false
            showTimePickerModal -> showTimePickerModal = false
            showReminderModal -> showReminderModal = false
            showRepeatModal -> showRepeatModal = false
            showNoTimeWarning -> showNoTimeWarning = false
            showNoDateWarning -> showNoDateWarning = false
            showPastDateWarning -> showPastDateWarning = false
            showEndDateWarning -> showEndDateWarning = false
        }
    }

    val isScheduleEnabled = selectedDate != null && titleText.trim().isNotEmpty()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(DayByDayBackground)
    ) {
        if (showDatePicker) {
            ChooseDateScreen(
                initialDateString = selectedDate?.format(dateFormatter),
                onDateSelected = { date ->
                    selectedDate = date
                    showDatePicker = false
                },
                onNavigateBack = { showDatePicker = false }
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 32.dp, vertical = 48.dp)
            ) {
                // Header (‹  Schedule item)
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
                        text = "Schedule item",
                        style = TextStyle(
                            fontFamily = DayByDayFontFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 22.sp,
                            lineHeight = 29.sp,
                            color = DayByDayPrimaryText
                        ),
                        modifier = Modifier.align(Alignment.Center)
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // 1. TITLE Field
                StandardField(label = "TITLE") {
                    BasicTextField(
                        value = titleText,
                        onValueChange = { titleText = it },
                        singleLine = true,
                        textStyle = TextStyle(
                            fontFamily = DayByDayFontFamily,
                            fontWeight = FontWeight.W400,
                            fontSize = 14.sp,
                            lineHeight = 18.sp,
                            color = DayByDayPrimaryText
                        ),
                        decorationBox = { innerTextField ->
                            Box(modifier = Modifier.fillMaxWidth()) {
                                if (titleText.isEmpty()) {
                                    Text(
                                        text = "Enter task title",
                                        style = TextStyle(
                                            fontFamily = DayByDayFontFamily,
                                            fontWeight = FontWeight.W400,
                                            fontSize = 14.sp,
                                            lineHeight = 18.sp,
                                            color = DayByDaySecondaryText
                                        )
                                    )
                                }
                                innerTextField()
                            }
                        }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 2. NOTE Field
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(116.dp),
                    shape = RoundedCornerShape(18.dp),
                    color = DayByDaySurface,
                    border = BorderStroke(1.dp, DayByDayNeutralBorder)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp, vertical = 11.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "NOTE",
                                style = TextStyle(
                                    fontFamily = DayByDayFontFamily,
                                    fontWeight = FontWeight.W400,
                                    fontSize = 11.sp,
                                    color = DayByDaySecondaryText
                                )
                            )
                            Spacer(modifier = Modifier.weight(1f))
                            Text(
                                text = "${noteText.length} / 500",
                                style = TextStyle(
                                    fontFamily = DayByDayFontFamily,
                                    fontWeight = FontWeight.W400,
                                    fontSize = 11.sp,
                                    color = DayByDaySecondaryText
                                )
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        BasicTextField(
                            value = noteText,
                            onValueChange = { if (it.length <= 500) noteText = it },
                            minLines = 3,
                            maxLines = 4,
                            textStyle = TextStyle(
                                fontFamily = DayByDayFontFamily,
                                fontWeight = FontWeight.W400,
                                fontSize = 14.sp,
                                lineHeight = 18.sp,
                                color = DayByDayPrimaryText
                            ),
                            decorationBox = { innerTextField ->
                                Box(modifier = Modifier.fillMaxWidth()) {
                                    if (noteText.isEmpty()) {
                                        Text(
                                            text = "Add a note...",
                                            style = TextStyle(
                                                fontFamily = DayByDayFontFamily,
                                                fontWeight = FontWeight.W400,
                                                fontSize = 14.sp,
                                                lineHeight = 18.sp,
                                                color = DayByDaySecondaryText
                                            )
                                        )
                                    }
                                    innerTextField()
                                }
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 3. DATE Field (Required)
                StandardFieldRow(
                    label = "DATE",
                    valueText = selectedDate?.format(dateFormatter),
                    actionText = if (selectedDate != null) "Change" else "Add date",
                    onClick = { showDatePicker = true }
                )

                Spacer(modifier = Modifier.height(16.dp))

                // 4. TIME Field (Optional)
                StandardFieldRow(
                    label = "TIME",
                    valueText = timeString ?: "Anytime",
                    actionText = "Optional",
                    onClick = { showTimePickerModal = true }
                )

                Spacer(modifier = Modifier.height(16.dp))

                // 5. REMINDER Field (Functional)
                StandardFieldRow(
                    label = "REMINDER",
                    valueText = if (timeString != null) (reminderString ?: "No reminder") else "No reminder",
                    actionText = "Optional",
                    onClick = {
                        if (timeString == null) {
                            showNoTimeWarning = true
                        } else {
                            showReminderModal = true
                        }
                    }
                )

                Spacer(modifier = Modifier.height(16.dp))

                // 6. REPEAT Field (Functional)
                StandardFieldRow(
                    label = "REPEAT",
                    valueText = if (selectedDate != null) recurrence.toDisplayString(selectedDate) else "Does not repeat",
                    actionText = "›",
                    onClick = {
                        if (selectedDate == null) {
                            showNoDateWarning = true
                        } else if (selectedDate?.isBefore(java.time.LocalDate.now()) == true) {
                            showPastDateWarning = true
                        } else {
                            showRepeatModal = true
                        }
                    }
                )

                Spacer(modifier = Modifier.height(16.dp))

                // 7. Important Toggle
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp),
                    shape = RoundedCornerShape(18.dp),
                    color = DayByDaySurface,
                    border = BorderStroke(1.dp, DayByDayNeutralBorder)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Important",
                            style = TextStyle(
                                fontFamily = DayByDayFontFamily,
                                fontWeight = FontWeight.W400,
                                fontSize = 14.sp,
                                lineHeight = 18.sp,
                                color = DayByDayPrimaryText
                            ),
                            modifier = Modifier.weight(1f)
                        )

                        Surface(
                            modifier = Modifier
                                .size(width = 60.dp, height = 30.dp)
                                .clickable { isImportant = !isImportant },
                            shape = RoundedCornerShape(15.dp),
                            color = if (isImportant) Color(0xFFF2E0D0) else Color(0xFFF4F0EC)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = if (isImportant) "★ On" else "☆ Off",
                                    style = TextStyle(
                                        fontFamily = DayByDayFontFamily,
                                        fontWeight = FontWeight.W400,
                                        fontSize = 12.5.sp,
                                        color = if (isImportant) DayByDayStrongAccent else DayByDaySecondaryText
                                    )
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Primary Action: Schedule Button
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .then(
                            if (isScheduleEnabled) {
                                Modifier.clickable {
                                    selectedDate?.let { date ->
                                        val recEndDate = recurrence?.endDate
                                        val isEndDateInvalid = recEndDate != null && recEndDate.isBefore(date)
                                        if (isEndDateInvalid) {
                                            showEndDateWarning = true
                                        } else if (date.isBefore(java.time.LocalDate.now())) {
                                            showPastDateWarning = true
                                        } else {
                                            onSchedule(
                                                titleText,
                                                noteText,
                                                date,
                                                timeString,
                                                if (timeString != null) reminderString else null,
                                                recurrence,
                                                isImportant
                                            )
                                        }
                                    }
                                }
                            } else Modifier
                        ),
                    shape = RoundedCornerShape(18.dp),
                    color = if (isScheduleEnabled) DayByDayAccent else Color(0xFFF4F0EC),
                    border = BorderStroke(1.dp, if (isScheduleEnabled) DayByDayAccent else Color(0xFFDDD4CC))
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Schedule",
                            style = TextStyle(
                                fontFamily = DayByDayFontFamily,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.sp,
                                lineHeight = 20.sp,
                                color = if (isScheduleEnabled) Color.White else Color(0xFFA79B91),
                                textAlign = TextAlign.Center
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(36.dp))
            }
        }

        // Floating Choose Time Modal
        if (showTimePickerModal) {
            ChooseTimeModal(
                initialTimeString = timeString,
                onDismiss = { showTimePickerModal = false },
                onTimeSelected = { newTimeString ->
                    timeString = newTimeString
                    showTimePickerModal = false
                }
            )
        }

        // Floating Choose Reminder Modal
        if (showReminderModal && timeString != null) {
            ChooseReminderModal(
                currentReminder = reminderString,
                onDismiss = { showReminderModal = false },
                onReminderSelected = { newReminder ->
                    reminderString = newReminder
                    showReminderModal = false
                }
            )
        }

        // Floating Choose Repeat Modal
        if (showRepeatModal && selectedDate != null) {
            ChooseRepeatModal(
                currentRepeat = recurrence,
                anchorDate = selectedDate,
                onDismiss = { showRepeatModal = false },
                onRepeatSelected = { newRepeat ->
                    recurrence = newRepeat
                    showRepeatModal = false
                }
            )
        }

        // Warning when tapping Repeat without a Date
        if (showNoDateWarning) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.18f))
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) { showNoDateWarning = false },
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    modifier = Modifier
                        .padding(horizontal = 32.dp)
                        .fillMaxWidth()
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() }
                        ) { /* Consume clicks inside modal card */ },
                    shape = RoundedCornerShape(20.dp),
                    color = Color.White,
                    border = BorderStroke(1.dp, DayByDayNeutralBorder)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Add a date before setting a repeat.",
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
                                .clickable { showNoDateWarning = false },
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

        // Warning when scheduling in the past
        if (showPastDateWarning) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.18f))
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) { showPastDateWarning = false },
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
                            text = "Tasks cannot be scheduled in the past.",
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
                                .clickable { showPastDateWarning = false },
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

        // Warning when End Date is before Start Date
        if (showEndDateWarning) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.18f))
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

        // Warning when tapping Reminder without a Time
        if (showNoTimeWarning) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.18f))
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) { showNoTimeWarning = false },
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    modifier = Modifier
                        .padding(horizontal = 32.dp)
                        .fillMaxWidth()
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() }
                        ) { /* Consume clicks inside modal card */ },
                    shape = RoundedCornerShape(20.dp),
                    color = Color.White,
                    border = BorderStroke(1.dp, DayByDayNeutralBorder)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Add a time before setting a reminder.",
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
                                .clickable { showNoTimeWarning = false },
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

@Composable
private fun StandardField(
    label: String,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp),
        shape = RoundedCornerShape(18.dp),
        color = DayByDaySurface,
        border = BorderStroke(1.dp, DayByDayNeutralBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 16.dp, end = 16.dp, top = 11.dp, bottom = 10.dp)
        ) {
            Text(
                text = label,
                style = TextStyle(
                    fontFamily = DayByDayFontFamily,
                    fontWeight = FontWeight.W400,
                    fontSize = 11.sp,
                    color = DayByDaySecondaryText
                )
            )
            Spacer(modifier = Modifier.height(6.dp))
            content()
        }
    }
}

@Composable
private fun StandardFieldRow(
    label: String,
    valueText: String?,
    actionText: String,
    onClick: (() -> Unit)? = null
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
            .then(
                if (onClick != null) Modifier.clickable { onClick() } else Modifier
            ),
        shape = RoundedCornerShape(18.dp),
        color = DayByDaySurface,
        border = BorderStroke(1.dp, DayByDayNeutralBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 16.dp, end = 16.dp, top = 11.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = label,
                    style = TextStyle(
                        fontFamily = DayByDayFontFamily,
                        fontWeight = FontWeight.W400,
                        fontSize = 11.sp,
                        color = DayByDaySecondaryText
                    )
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = valueText ?: "Choose date",
                    style = TextStyle(
                        fontFamily = DayByDayFontFamily,
                        fontWeight = FontWeight.W400,
                        fontSize = 14.sp,
                        lineHeight = 18.sp,
                        color = if (valueText != null && valueText != "No reminder" && valueText != "Does not repeat") DayByDayPrimaryText else DayByDaySecondaryText
                    )
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = actionText,
                style = TextStyle(
                    fontFamily = DayByDayFontFamily,
                    fontWeight = FontWeight.W400,
                    fontSize = 12.5.sp,
                    color = DayByDaySecondaryText
                )
            )
        }
    }
}
