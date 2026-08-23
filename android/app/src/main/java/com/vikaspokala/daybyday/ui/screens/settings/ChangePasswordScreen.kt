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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vikaspokala.daybyday.ui.theme.DayByDayAccent
import com.vikaspokala.daybyday.ui.theme.DayByDayBackground
import com.vikaspokala.daybyday.ui.theme.DayByDayDestructive
import com.vikaspokala.daybyday.ui.theme.DayByDayFontFamily
import com.vikaspokala.daybyday.ui.theme.DayByDayNeutralBorder
import com.vikaspokala.daybyday.ui.theme.DayByDayPrimaryText
import com.vikaspokala.daybyday.ui.theme.DayByDaySecondaryText
import com.vikaspokala.daybyday.ui.theme.DayByDaySurface

private val EyeIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "Eye",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(1f, 12f)
            curveTo(3.5f, 6.5f, 7.5f, 4f, 12f, 4f)
            curveTo(16.5f, 4f, 20.5f, 6.5f, 23f, 12f)
            curveTo(20.5f, 17.5f, 16.5f, 20f, 12f, 20f)
            curveTo(7.5f, 20f, 3.5f, 17.5f, 1f, 12f)
            close()
            moveTo(12f, 9f)
            curveTo(10.34f, 9f, 9f, 10.34f, 9f, 12f)
            curveTo(9f, 13.66f, 10.34f, 15f, 12f, 15f)
            curveTo(13.66f, 15f, 15f, 13.66f, 15f, 12f)
            curveTo(15f, 10.34f, 13.66f, 9f, 12f, 9f)
            close()
        }
    }.build()
}

private val EyeOffIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "EyeOff",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(1f, 12f)
            curveTo(3.5f, 6.5f, 7.5f, 4f, 12f, 4f)
            curveTo(14.2f, 4f, 16.3f, 4.6f, 18.2f, 5.8f)
            moveTo(21.5f, 9f)
            curveTo(22.2f, 10f, 22.7f, 11f, 23f, 12f)
            curveTo(20.5f, 17.5f, 16.5f, 20f, 12f, 20f)
            curveTo(9.3f, 20f, 6.9f, 18.9f, 4.8f, 17.1f)
            moveTo(2f, 2f)
            lineTo(22f, 22f)
        }
    }.build()
}

@Composable
fun ChangePasswordScreen(
    isLoading: Boolean = false,
    errorMessage: String? = null,
    onNavigateBack: () -> Unit,
    onChangePasswordSubmit: (currentPassword: String, newPassword: String, confirmPassword: String) -> Unit = { _, _, _ -> },
    modifier: Modifier = Modifier
) {
    var currentPassword by remember { mutableStateOf("") }
    var isCurrentPasswordVisible by remember { mutableStateOf(false) }

    var newPassword by remember { mutableStateOf("") }
    var isNewPasswordVisible by remember { mutableStateOf(false) }

    var confirmPassword by remember { mutableStateOf("") }
    var isConfirmPasswordVisible by remember { mutableStateOf(false) }

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
                                fontWeight = FontWeight.W500, // 500
                                fontSize = 24.sp,
                                lineHeight = 31.sp,
                                color = DayByDayPrimaryText
                            )
                        )
                    }
                }

                // Title ("Change password")
                Text(
                    text = "Change password",
                    style = TextStyle(
                        fontFamily = DayByDayFontFamily,
                        fontWeight = FontWeight.SemiBold, // 600
                        fontSize = 22.sp,
                        lineHeight = 29.sp,
                        color = DayByDayPrimaryText
                    ),
                    modifier = Modifier.align(Alignment.Center)
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Subtitle
            Text(
                text = "Update the password you use to sign in.",
                style = TextStyle(
                    fontFamily = DayByDayFontFamily,
                    fontWeight = FontWeight.Normal, // 400
                    fontSize = 13.sp,
                    lineHeight = 17.sp,
                    color = DayByDaySecondaryText
                )
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Field 1: Current Password
            CustomInputField(
                label = "CURRENT PASSWORD",
                value = currentPassword,
                onValueChange = { currentPassword = it },
                placeholder = "Enter current password",
                isPasswordToggle = true,
                isPasswordVisible = isCurrentPasswordVisible,
                onTogglePasswordVisibility = { isCurrentPasswordVisible = !isCurrentPasswordVisible },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Next
                )
            )

            Spacer(modifier = Modifier.height(18.dp))

            // Field 2: New Password
            CustomInputField(
                label = "NEW PASSWORD",
                value = newPassword,
                onValueChange = { newPassword = it },
                placeholder = "Enter new password",
                isPasswordToggle = true,
                isPasswordVisible = isNewPasswordVisible,
                onTogglePasswordVisibility = { isNewPasswordVisible = !isNewPasswordVisible },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Next
                )
            )

            Spacer(modifier = Modifier.height(18.dp))

            // Field 3: Confirm New Password
            CustomInputField(
                label = "CONFIRM NEW PASSWORD",
                value = confirmPassword,
                onValueChange = { confirmPassword = it },
                placeholder = "Enter new password again",
                isPasswordToggle = true,
                isPasswordVisible = isConfirmPasswordVisible,
                onTogglePasswordVisibility = { isConfirmPasswordVisible = !isConfirmPasswordVisible },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = {
                        if (!isLoading) {
                            onChangePasswordSubmit(currentPassword, newPassword, confirmPassword)
                        }
                    }
                )
            )

            Spacer(modifier = Modifier.height(18.dp))

            // Notice
            Text(
                text = "You’ll need to sign in again after changing your password.",
                style = TextStyle(
                    fontFamily = DayByDayFontFamily,
                    fontWeight = FontWeight.Normal, // 400
                    fontSize = 13.sp,
                    lineHeight = 17.sp,
                    color = DayByDaySecondaryText
                )
            )

            errorMessage?.let { error ->
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = error,
                    style = TextStyle(
                        fontFamily = DayByDayFontFamily,
                        fontWeight = FontWeight.Normal, // 400
                        fontSize = 13.sp,
                        lineHeight = 17.sp,
                        color = DayByDayDestructive
                    )
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Change password button
            val isButtonEnabled = !isLoading
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .clickable(enabled = isButtonEnabled) {
                        onChangePasswordSubmit(currentPassword, newPassword, confirmPassword)
                    },
                shape = RoundedCornerShape(18.dp),
                color = if (isButtonEnabled) DayByDayAccent else DayByDaySecondaryText.copy(alpha = 0.5f)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (isLoading) "Changing password..." else "Change password",
                        style = TextStyle(
                            fontFamily = DayByDayFontFamily,
                            fontWeight = FontWeight.SemiBold, // 600
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
    isPasswordToggle: Boolean = false,
    isPasswordVisible: Boolean = false,
    onTogglePasswordVisibility: (() -> Unit)? = null,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    modifier: Modifier = Modifier
) {
    val effectiveVisualTransformation = if (isPasswordToggle) {
        if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation()
    } else {
        visualTransformation
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(72.dp),
        shape = RoundedCornerShape(18.dp),
        color = DayByDaySurface,
        border = BorderStroke(1.dp, DayByDayNeutralBorder)
    ) {
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        start = 16.dp,
                        end = if (isPasswordToggle) 48.dp else 16.dp,
                        top = 11.dp,
                        bottom = 11.dp
                    )
            ) {
                Text(
                    text = label,
                    style = TextStyle(
                        fontFamily = DayByDayFontFamily,
                        fontWeight = FontWeight.Medium, // 500
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
                    visualTransformation = effectiveVisualTransformation,
                    keyboardOptions = keyboardOptions,
                    keyboardActions = keyboardActions,
                    textStyle = TextStyle(
                        fontFamily = DayByDayFontFamily,
                        fontWeight = FontWeight.Normal, // 400
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
                                        fontWeight = FontWeight.Normal, // 400
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

            if (isPasswordToggle && onTogglePasswordVisibility != null) {
                IconButton(
                    onClick = onTogglePasswordVisibility,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 4.dp)
                ) {
                    Icon(
                        imageVector = if (isPasswordVisible) EyeIcon else EyeOffIcon,
                        contentDescription = if (isPasswordVisible) "Hide password" else "Show password",
                        tint = if (isPasswordVisible) DayByDayAccent else DayByDaySecondaryText,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}
