package com.vikaspokala.daybyday.ui.screens.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vikaspokala.daybyday.ui.navigation.ProfileFieldType
import com.vikaspokala.daybyday.ui.theme.DayByDayAccent
import com.vikaspokala.daybyday.ui.theme.DayByDayBackground
import com.vikaspokala.daybyday.ui.theme.DayByDayFontFamily
import com.vikaspokala.daybyday.ui.theme.DayByDayNeutralBorder
import com.vikaspokala.daybyday.ui.theme.DayByDayPrimaryText
import com.vikaspokala.daybyday.ui.theme.DayByDaySecondaryText
import com.vikaspokala.daybyday.ui.theme.DayByDaySurface

import com.vikaspokala.daybyday.ui.theme.DayByDayDestructive

@Composable
fun EditProfileFieldScreen(
    fieldType: ProfileFieldType,
    initialValue: String,
    isLoading: Boolean = false,
    errorMessage: String? = null,
    onNavigateBack: () -> Unit,
    onSave: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var textValue by remember(initialValue) { mutableStateOf(initialValue) }

    val (titleText, labelText, placeholderText) = when (fieldType) {
        ProfileFieldType.FIRST_NAME -> Triple("Edit first name", "FIRST NAME", "Enter first name")
        ProfileFieldType.LAST_NAME -> Triple("Edit last name", "LAST NAME", "Enter last name")
        ProfileFieldType.NICKNAME -> Triple("Nickname (Optional)", "NICKNAME (Optional)", "Enter nickname")
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
            // Header (40dp height)
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

                // Title
                Text(
                    text = titleText,
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

            // Single Input Field
            CustomInputField(
                label = labelText,
                value = textValue,
                onValueChange = { textValue = it },
                placeholder = placeholderText,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(
                    onDone = {
                        if (!isLoading) {
                            onSave(textValue)
                        }
                    }
                )
            )

            // Inline Error Message
            if (!errorMessage.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = errorMessage,
                    style = TextStyle(
                        fontFamily = DayByDayFontFamily,
                        fontWeight = FontWeight.Normal,
                        fontSize = 13.sp,
                        lineHeight = 17.sp,
                        color = DayByDayDestructive,
                        textAlign = TextAlign.Center
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Save button
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .then(
                        if (!isLoading) {
                            Modifier.clickable { onSave(textValue) }
                        } else {
                            Modifier
                        }
                    ),
                shape = RoundedCornerShape(18.dp),
                color = if (!isLoading) DayByDayAccent else DayByDayAccent.copy(alpha = 0.6f)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (isLoading) "Saving..." else "Save",
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

            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}

@Composable
private fun CustomInputField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(72.dp),
        shape = RoundedCornerShape(18.dp),
        color = DayByDaySurface,
        border = BorderStroke(1.dp, DayByDayNeutralBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 11.dp)
        ) {
            Text(
                text = label,
                style = TextStyle(
                    fontFamily = DayByDayFontFamily,
                    fontWeight = FontWeight.Medium,
                    fontSize = 11.sp,
                    lineHeight = 14.sp,
                    color = DayByDaySecondaryText
                )
            )
            Spacer(modifier = Modifier.height(6.dp))
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                keyboardOptions = keyboardOptions,
                keyboardActions = keyboardActions,
                textStyle = TextStyle(
                    fontFamily = DayByDayFontFamily,
                    fontWeight = FontWeight.Normal,
                    fontSize = 14.sp,
                    lineHeight = 18.sp,
                    color = DayByDayPrimaryText
                ),
                decorationBox = { innerTextField ->
                    Box(modifier = Modifier.fillMaxWidth()) {
                        if (value.isEmpty()) {
                            Text(
                                text = placeholder,
                                style = TextStyle(
                                    fontFamily = DayByDayFontFamily,
                                    fontWeight = FontWeight.Normal,
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
}
