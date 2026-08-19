package com.vikaspokala.daybyday.ui.screens.task

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
    onScheduleThisClick: () -> Unit = {},
    onConfirmDelete: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var titleText by remember(initialTitle) { mutableStateOf(initialTitle) }
    var noteText by remember(initialNote) { mutableStateOf(initialNote.orEmpty()) }
    var isImportant by remember(initialIsImportant) { mutableStateOf(initialIsImportant) }
    var showDeleteConfirmation by remember { mutableStateOf(false) }

    // Intercept system Back button when delete confirmation overlay is open
    BackHandler(enabled = showDeleteConfirmation) {
        showDeleteConfirmation = false
    }

    // Determine dirty state by comparing current form state against initial state
    val isSaveEnabled = if (isCreateMode) {
        titleText.isNotBlank()
    } else {
        titleText != initialTitle || noteText != initialNote.orEmpty() || isImportant != initialIsImportant
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(DayByDayBackground)
    ) {
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
                        .align(Alignment.CenterEnd),
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

            // DATE Field (72dp height)
            StandardFieldRow(
                label = "DATE",
                valueText = dateString,
                actionText = if (!dateString.isNullOrEmpty()) "Change" else "Add date"
            )

            Spacer(modifier = Modifier.height(16.dp))

            // TIME Field (72dp height)
            StandardFieldRow(
                label = "TIME",
                valueText = timeString ?: "Anytime",
                actionText = "Optional"
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

            // LATER-SPECIFIC "Schedule this" Action
            if (isLaterTask) {
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

        // Floating Delete Confirmation Overlay (ON TOP of existing Task screen)
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
                        // Title ("Delete this task?")
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

                        // Description ("This task will be permanently deleted.")
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

                        // Cancel Button
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

                        // Delete Button
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
    actionText: String
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp),
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
