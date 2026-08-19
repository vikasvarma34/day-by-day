package com.vikaspokala.daybyday.ui.screens.task

import java.time.LocalDate
import java.time.YearMonth
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
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vikaspokala.daybyday.ui.components.MonthCalendar
import com.vikaspokala.daybyday.ui.theme.DayByDayAccent
import com.vikaspokala.daybyday.ui.theme.DayByDayBackground
import com.vikaspokala.daybyday.ui.theme.DayByDayFontFamily
import com.vikaspokala.daybyday.ui.theme.DayByDayNeutralBorder
import com.vikaspokala.daybyday.ui.theme.DayByDayPrimaryText
import com.vikaspokala.daybyday.ui.theme.DayByDaySecondaryText
import com.vikaspokala.daybyday.ui.theme.DayByDayStrongAccent
import com.vikaspokala.daybyday.ui.theme.DayByDaySurface

@Composable
fun TaskScreen(
    isCreateMode: Boolean,
    initialTitle: String = "",
    initialNote: String? = null,
    initialIsImportant: Boolean = false,
    dateString: String? = null,
    timeString: String? = null,
    isLaterTask: Boolean = false,
    onNavigateBack: () -> Unit,
    onSaveTask: (title: String, note: String?, dateString: String?, timeString: String?, isImportant: Boolean) -> Unit = { _, _, _, _, _ -> },
    onScheduleThisClick: () -> Unit = {},
    onConfirmDelete: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var titleText by remember(initialTitle) { mutableStateOf(initialTitle) }
    var noteText by remember(initialNote) { mutableStateOf(initialNote.orEmpty()) }
    var isImportant by remember(initialIsImportant) { mutableStateOf(initialIsImportant) }
    var currentDateString by remember(dateString) { mutableStateOf(dateString) }
    var currentTimeString by remember(timeString) { mutableStateOf(timeString) }

    var showDeleteConfirmation by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePickerModal by remember { mutableStateOf(false) }

    // Intercept system Back button when overlays/screens are open
    BackHandler(enabled = showDeleteConfirmation || showDatePicker || showTimePickerModal) {
        when {
            showDeleteConfirmation -> showDeleteConfirmation = false
            showDatePicker -> showDatePicker = false
            showTimePickerModal -> showTimePickerModal = false
        }
    }

    // Determine dirty state by comparing current form state against initial state
    val isSaveEnabled = if (isCreateMode) {
        titleText.trim().isNotEmpty()
    } else {
        titleText.trim().isNotEmpty() && (
            titleText != initialTitle ||
            noteText != initialNote.orEmpty() ||
            isImportant != initialIsImportant ||
            currentDateString != dateString ||
            currentTimeString != timeString
        )
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
                                        onSaveTask(
                                            titleText,
                                            noteText,
                                            currentDateString,
                                            currentTimeString,
                                            isImportant
                                        )
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

                    // TIME Field (72dp height)
                    StandardFieldRow(
                        label = "TIME",
                        valueText = currentTimeString ?: "Anytime",
                        actionText = "Optional",
                        onClick = { showTimePickerModal = true }
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // REMINDER Field (72dp height)
                    StandardFieldRow(
                        label = "REMINDER",
                        valueText = "None",
                        actionText = "Optional"
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // REPEAT Field (72dp height)
                    StandardFieldRow(
                        label = "REPEAT",
                        valueText = "Does not repeat",
                        actionText = "›"
                    )

                    Spacer(modifier = Modifier.height(16.dp))
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

                // LATER-SPECIFIC "Schedule this" Action (Existing Later tasks only)
                if (isLaterTask && !isCreateMode) {
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
private fun ChooseDateScreen(
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

@Composable
private fun ChooseTimeModal(
    initialTimeString: String?,
    onDismiss: () -> Unit,
    onTimeSelected: (String) -> Unit
) {
    var hour by remember(initialTimeString) { mutableIntStateOf(parseHour(initialTimeString)) }
    var minute by remember(initialTimeString) { mutableIntStateOf(parseMinute(initialTimeString)) }
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
                        value = hour,
                        onIncrement = { hour = if (hour >= 12) 1 else hour + 1 },
                        onDecrement = { hour = if (hour <= 1) 12 else hour - 1 },
                        onValueChange = { input ->
                            val parsed = input.toIntOrNull()
                            if (parsed != null && parsed in 1..12) {
                                hour = parsed
                            }
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
                        value = minute,
                        formatTwoDigits = true,
                        onIncrement = { minute = (minute + 5) % 60 },
                        onDecrement = { minute = if (minute < 5) 55 else minute - 5 },
                        onValueChange = { input ->
                            val parsed = input.toIntOrNull()
                            if (parsed != null && parsed in 0..59) {
                                minute = parsed
                            }
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
                                val formattedMinutes = "%02d".format(minute)
                                onTimeSelected("$hour:$formattedMinutes $amPm")
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

@Composable
private fun TimeNumberBox(
    value: Int,
    formatTwoDigits: Boolean = false,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit,
    onValueChange: (String) -> Unit
) {
    val displayString = if (formatTwoDigits) "%02d".format(value) else value.toString()
    var textInput by remember(value) { mutableStateOf(displayString) }

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
                    value = textInput,
                    onValueChange = {
                        textInput = it
                        onValueChange(it)
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
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
                        color = if (valueText != null) DayByDayPrimaryText else DayByDaySecondaryText
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

private fun parseHour(timeStr: String?): Int {
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

private fun parseMinute(timeStr: String?): Int {
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

private fun parseAmPm(timeStr: String?): String {
    if (timeStr.isNullOrEmpty()) return "AM"
    return try {
        val parts = timeStr.trim().split(" ")
        if (parts.size > 1 && parts[1].equals("PM", ignoreCase = true)) "PM" else "AM"
    } catch (e: Exception) {
        "AM"
    }
}
