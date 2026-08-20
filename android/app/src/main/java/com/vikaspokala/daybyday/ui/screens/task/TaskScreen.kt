package com.vikaspokala.daybyday.ui.screens.task

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
import androidx.compose.runtime.LaunchedEffect
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

fun parseTaskDate(dateStr: String?, referenceDate: java.time.LocalDate): java.time.LocalDate? {
    if (dateStr.isNullOrEmpty()) return null
    return try {
        val formatter = java.time.format.DateTimeFormatterBuilder()
            .appendPattern("EEEE, d MMMM")
            .parseDefaulting(java.time.temporal.ChronoField.YEAR, referenceDate.year.toLong())
            .toFormatter(Locale.getDefault())
        java.time.LocalDate.parse(dateStr, formatter)
    } catch (e: Exception) {
        try {
            val formatterWithYear = DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", Locale.getDefault())
            val withYearStr = if (!dateStr.contains(referenceDate.year.toString())) "$dateStr ${referenceDate.year}" else dateStr
            java.time.LocalDate.parse(withYearStr, formatterWithYear)
        } catch (e2: Exception) {
            try {
                java.time.LocalDate.parse(dateStr)
            } catch (e3: Exception) {
                null
            }
        }
    }
}

@Composable
fun TaskScreen(
    isCreateMode: Boolean,
    initialTitle: String = "",
    initialNote: String? = null,
    initialIsImportant: Boolean = false,
    isCompleted: Boolean = false,
    dateString: String? = null,
    timeString: String? = null,
    reminderString: String? = null,
    recurrence: com.vikaspokala.daybyday.ui.models.Recurrence? = null,
    isLaterTask: Boolean = false,
    plannerToday: java.time.LocalDate = java.time.LocalDate.now(),
    onNavigateBack: () -> Unit,
    onSaveTask: (title: String, note: String?, dateString: String?, timeString: String?, reminderString: String?, recurrence: com.vikaspokala.daybyday.ui.models.Recurrence?, isImportant: Boolean) -> Unit = { _, _, _, _, _, _, _ -> },
    onScheduleThisClick: () -> Unit = {},
    onConfirmDelete: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var titleText by remember(initialTitle) { mutableStateOf(initialTitle) }
    var noteText by remember(initialNote) { mutableStateOf(initialNote.orEmpty()) }
    var isImportant by remember(initialIsImportant) { mutableStateOf(initialIsImportant) }
    var currentDateString by remember(dateString) { mutableStateOf(dateString) }
    var currentTimeString by remember(timeString) { mutableStateOf(timeString) }
    var currentReminderString by remember(reminderString, timeString, dateString, plannerToday) {
        val pd = parseTaskDate(dateString, plannerToday)
        if (pd != null && pd.isBefore(plannerToday)) {
            mutableStateOf<String?>(null)
        } else {
            mutableStateOf(if (timeString != null) reminderString else null)
        }
    }
    var currentRecurrence by remember(recurrence, dateString, plannerToday) {
        val pd = parseTaskDate(dateString, plannerToday)
        if (pd != null && pd.isBefore(plannerToday)) {
            mutableStateOf<com.vikaspokala.daybyday.ui.models.Recurrence?>(null)
        } else {
            mutableStateOf(if (dateString != null) recurrence else null)
        }
    }

    var showDeleteConfirmation by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePickerModal by remember { mutableStateOf(false) }
    var showReminderModal by remember { mutableStateOf(false) }
    var showRepeatModal by remember { mutableStateOf(false) }
    var showNoTimeWarning by remember { mutableStateOf(false) }
    var showNoDateWarning by remember { mutableStateOf(false) }
    var showPastDateWarning by remember { mutableStateOf(false) }
    var showPastReminderWarning by remember { mutableStateOf(false) }
    var showPastRepeatWarning by remember { mutableStateOf(false) }
    var showEndDateWarning by remember { mutableStateOf(false) }

    // Auto-clear reminder & reset recurrence if currentDateString becomes historical
    LaunchedEffect(currentDateString, plannerToday) {
        val pd = parseTaskDate(currentDateString, plannerToday)
        if (pd != null && pd.isBefore(plannerToday)) {
            if (currentReminderString != null) {
                currentReminderString = null
            }
            if (currentRecurrence != null && currentRecurrence !is com.vikaspokala.daybyday.ui.models.Recurrence.Once) {
                currentRecurrence = null
            }
        }
    }

    // Intercept system Back button when overlays/screens are open
    BackHandler(enabled = showDeleteConfirmation || showDatePicker || showTimePickerModal || showReminderModal || showRepeatModal || showNoTimeWarning || showNoDateWarning || showPastDateWarning || showPastReminderWarning || showPastRepeatWarning || showEndDateWarning) {
        when {
            showDeleteConfirmation -> showDeleteConfirmation = false
            showDatePicker -> showDatePicker = false
            showTimePickerModal -> showTimePickerModal = false
            showReminderModal -> showReminderModal = false
            showRepeatModal -> showRepeatModal = false
            showNoTimeWarning -> showNoTimeWarning = false
            showNoDateWarning -> showNoDateWarning = false
            showPastDateWarning -> showPastDateWarning = false
            showPastReminderWarning -> showPastReminderWarning = false
            showPastRepeatWarning -> showPastRepeatWarning = false
            showEndDateWarning -> showEndDateWarning = false
        }
    }

    // Determine dirty state by comparing current form state against initial state
    val isTitleDirty = titleText != initialTitle
    val isNoteDirty = noteText != initialNote.orEmpty()
    val isImportantDirty = isImportant != initialIsImportant
    val isDateDirty = currentDateString != dateString
    val isTimeDirty = currentTimeString != timeString
    val isReminderDirty = currentReminderString != (if (timeString != null) reminderString else null)
    val isRecurrenceDirty = currentRecurrence != (if (dateString != null) recurrence else null)

    val isContentChanged = isTitleDirty || isNoteDirty || isImportantDirty
    val isScheduleChanged = isDateDirty || isTimeDirty || isReminderDirty || isRecurrenceDirty

    val isSaveEnabled = if (isCreateMode) {
        titleText.trim().isNotEmpty()
    } else {
        titleText.trim().isNotEmpty() && (isContentChanged || isScheduleChanged)
    }

    val dateFormatter = remember { DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.getDefault()) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(DayByDayBackground)
    ) {
        if (showDatePicker && !isLaterTask) {
            ChooseDateScreen(
                initialDateString = currentDateString,
                onDateSelected = { selectedDate ->
                    currentDateString = selectedDate.format(dateFormatter)
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
                // Header (348dp x 40dp)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp)
                ) {
                    // Back control (34dp x 34dp)
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

                    // Center Title ("Task")
                    Text(
                        text = "Task",
                        style = TextStyle(
                            fontFamily = DayByDayFontFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 22.sp,
                            lineHeight = 29.sp,
                            color = DayByDayPrimaryText
                        ),
                        modifier = Modifier.align(Alignment.Center)
                    )

                    // Right Save Control (56dp x 32dp, radius 16dp)
                    Surface(
                        modifier = Modifier
                            .size(width = 56.dp, height = 32.dp)
                            .align(Alignment.CenterEnd)
                            .then(
                                if (isSaveEnabled) {
                                    Modifier.clickable {
                                        val pd = parseTaskDate(currentDateString, plannerToday)
                                        val initialPd = parseTaskDate(dateString, plannerToday)
                                        val isHistoricalDate = pd != null && pd.isBefore(plannerToday)
                                        val isInitialHistorical = initialPd != null && initialPd.isBefore(plannerToday)

                                        val recEndDate = currentRecurrence?.endDate
                                        val isEndDateInvalid = pd != null && recEndDate != null && recEndDate.isBefore(pd)

                                        val isBlocked = if (isCreateMode) {
                                            isHistoricalDate && ((currentRecurrence != null && currentRecurrence !is com.vikaspokala.daybyday.ui.models.Recurrence.Once) || currentReminderString != null)
                                        } else {
                                            if (isInitialHistorical) {
                                                isHistoricalDate && isScheduleChanged
                                            } else {
                                                isHistoricalDate
                                            }
                                        }

                                        if (isEndDateInvalid) {
                                            showEndDateWarning = true
                                        } else if (isBlocked) {
                                            showPastDateWarning = true
                                        } else {
                                            val finalReminder = if (isHistoricalDate) null else currentReminderString
                                            val finalRecurrence = if (isHistoricalDate) null else currentRecurrence
                                            onSaveTask(
                                                titleText,
                                                noteText,
                                                currentDateString,
                                                currentTimeString,
                                                finalReminder,
                                                finalRecurrence,
                                                isImportant
                                            )
                                        }
                                    }
                                } else Modifier
                            ),
                        shape = RoundedCornerShape(16.dp),
                        color = if (isSaveEnabled) DayByDayAccent else Color(0xFFF4F0EC),
                        border = BorderStroke(1.dp, if (isSaveEnabled) DayByDayAccent else Color(0xFFDDD4CC))
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "Save",
                                style = TextStyle(
                                    fontFamily = DayByDayFontFamily,
                                    fontWeight = FontWeight.W400,
                                    fontSize = 12.5.sp,
                                    color = if (isSaveEnabled) Color.White else Color(0xFFA79B91)
                                )
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // TITLE Field (72dp height)
                StandardField(
                    label = "TITLE"
                ) {
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

                // NOTE Field (116dp height)
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

                // DATE, TIME, REMINDER, REPEAT fields ONLY shown if NOT a Later task
                if (!isLaterTask) {
                    // DATE Field (72dp height)
                    StandardFieldRow(
                        label = "DATE",
                        valueText = currentDateString,
                        actionText = if (!currentDateString.isNullOrEmpty()) "Change" else "Add date",
                        onClick = { showDatePicker = true }
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    val parsedDate = parseTaskDate(currentDateString, plannerToday)
                    val isCurrentDateHistorical = parsedDate != null && parsedDate.isBefore(plannerToday)
                    val isHistoricalCreate = isCreateMode && isCurrentDateHistorical

                    // TIME Field (72dp height)
                    StandardFieldRow(
                        label = "TIME",
                        valueText = currentTimeString ?: "Anytime",
                        actionText = "Optional",
                        onClick = { showTimePickerModal = true }
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    if (!isHistoricalCreate) {
                        // REMINDER Field (72dp height)
                        StandardFieldRow(
                            label = "REMINDER",
                            valueText = when {
                                isCurrentDateHistorical -> "Unavailable for past tasks"
                                currentTimeString != null -> currentReminderString ?: "No reminder"
                                else -> "No reminder"
                            },
                            actionText = if (isCurrentDateHistorical) "" else "Optional",
                            onClick = {
                                if (isCurrentDateHistorical) {
                                    showPastReminderWarning = true
                                } else if (currentTimeString == null) {
                                    showNoTimeWarning = true
                                } else {
                                    showReminderModal = true
                                }
                            }
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // REPEAT Field (72dp height)
                        StandardFieldRow(
                            label = "REPEAT",
                            valueText = when {
                                isCurrentDateHistorical -> "Does not repeat"
                                currentDateString != null -> currentRecurrence.toDisplayString(parsedDate)
                                else -> "Does not repeat"
                            },
                            actionText = if (isCurrentDateHistorical) "" else "›",
                            onClick = {
                                if (isCurrentDateHistorical) {
                                    showPastRepeatWarning = true
                                } else if (currentDateString == null) {
                                    showNoDateWarning = true
                                } else {
                                    showRepeatModal = true
                                }
                            }
                        )

                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }

                // IMPORTANT Toggle Row (64dp height)
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

                // LATER-SPECIFIC "Schedule this" Action (Active Existing Later tasks only)
                if (isLaterTask && !isCreateMode && !isCompleted) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .clickable { onScheduleThisClick() },
                        shape = RoundedCornerShape(18.dp),
                        color = DayByDayAccent
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Schedule this",
                                style = TextStyle(
                                    fontFamily = DayByDayFontFamily,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 15.sp,
                                    lineHeight = 20.sp,
                                    color = Color.White,
                                    textAlign = TextAlign.Center
                                )
                            )
                        }
                    }
                }

                // DELETE TASK Button (Existing tasks only)
                if (!isCreateMode) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .clickable { showDeleteConfirmation = true },
                        shape = RoundedCornerShape(18.dp),
                        color = Color(0xFFD95C5C),
                        border = BorderStroke(1.dp, Color(0xFFD95C5C))
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Delete task",
                                style = TextStyle(
                                    fontFamily = DayByDayFontFamily,
                                    fontWeight = FontWeight.W400,
                                    fontSize = 13.5.sp,
                                    color = Color.White,
                                    textAlign = TextAlign.Center
                                )
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(36.dp))
            }
        }

        // Floating Choose Time Modal (ON TOP of Task screen)
        if (showTimePickerModal && !isLaterTask) {
            ChooseTimeModal(
                initialTimeString = currentTimeString,
                onDismiss = { showTimePickerModal = false },
                onTimeSelected = { newTimeString ->
                    currentTimeString = newTimeString
                    showTimePickerModal = false
                }
            )
        }

        // Floating Choose Reminder Modal (ON TOP of Task screen)
        if (showReminderModal && !isLaterTask && currentTimeString != null) {
            ChooseReminderModal(
                currentReminder = currentReminderString,
                onDismiss = { showReminderModal = false },
                onReminderSelected = { newReminder ->
                    currentReminderString = newReminder
                    showReminderModal = false
                }
            )
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

        // Floating Choose Repeat Modal (ON TOP of Task screen)
        if (showRepeatModal && !isLaterTask && currentDateString != null) {
            val parsedDate = parseTaskDate(currentDateString, plannerToday)

            ChooseRepeatModal(
                currentRepeat = currentRecurrence,
                anchorDate = parsedDate,
                onDismiss = { showRepeatModal = false },
                onRepeatSelected = { newRepeat ->
                    currentRecurrence = newRepeat
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

        // Warning when tapping Reminder on past date
        if (showPastReminderWarning) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.18f))
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) { showPastReminderWarning = false },
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
                            text = "Reminders are unavailable for past tasks.",
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
                                .clickable { showPastReminderWarning = false },
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

        // Warning when tapping Repeat on past date
        if (showPastRepeatWarning) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.18f))
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) { showPastRepeatWarning = false },
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
                            text = "Repeat is unavailable for past tasks.",
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
                                .clickable { showPastRepeatWarning = false },
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

        // Floating Delete Confirmation Overlay (ON TOP of Task screen)
        if (showDeleteConfirmation && !isCreateMode) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.18f))
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) {
                        showDeleteConfirmation = false
                    },
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    modifier = Modifier
                        .padding(horizontal = 32.dp)
                        .fillMaxWidth()
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() }
                        ) { /* Consume clicks inside card */ },
                    shape = RoundedCornerShape(24.dp),
                    color = Color.White,
                    border = BorderStroke(1.dp, DayByDayNeutralBorder)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp)
                    ) {
                        Text(
                            text = "Delete this task?",
                            style = TextStyle(
                                fontFamily = DayByDayFontFamily,
                                fontWeight = FontWeight.W600,
                                fontSize = 22.sp,
                                lineHeight = 29.sp,
                                color = DayByDayPrimaryText
                            )
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = "This task will be permanently deleted.",
                            style = TextStyle(
                                fontFamily = DayByDayFontFamily,
                                fontWeight = FontWeight.W400,
                                fontSize = 14.sp,
                                lineHeight = 18.sp,
                                color = DayByDaySecondaryText
                            )
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .clickable { showDeleteConfirmation = false },
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
                                        color = DayByDayPrimaryText,
                                        textAlign = TextAlign.Center
                                    )
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .clickable {
                                    showDeleteConfirmation = false
                                    onConfirmDelete()
                                },
                            shape = RoundedCornerShape(18.dp),
                            color = Color(0xFFD95C5C),
                            border = BorderStroke(1.dp, Color(0xFFD95C5C))
                        ) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Delete",
                                    style = TextStyle(
                                        fontFamily = DayByDayFontFamily,
                                        fontWeight = FontWeight.W400,
                                        fontSize = 13.5.sp,
                                        color = Color.White,
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
                    text = valueText ?: "Not set",
                    style = TextStyle(
                        fontFamily = DayByDayFontFamily,
                        fontWeight = FontWeight.W400,
                        fontSize = 14.sp,
                        lineHeight = 18.sp,
                        color = if (valueText != null && valueText != "No reminder" && valueText != "None" && valueText != "Does not repeat") DayByDayPrimaryText else DayByDaySecondaryText
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
