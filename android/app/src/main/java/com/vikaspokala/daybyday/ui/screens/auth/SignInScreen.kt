package com.vikaspokala.daybyday.ui.screens.auth

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vikaspokala.daybyday.R
import com.vikaspokala.daybyday.ui.theme.DayByDayAccent
import com.vikaspokala.daybyday.ui.theme.DayByDayBackground
import com.vikaspokala.daybyday.ui.theme.DayByDayFontFamily
import com.vikaspokala.daybyday.ui.theme.DayByDayNeutralBorder
import com.vikaspokala.daybyday.ui.theme.DayByDayPrimaryText
import com.vikaspokala.daybyday.ui.theme.DayByDaySecondaryText
import com.vikaspokala.daybyday.ui.theme.DayByDaySurface

@Composable
fun SignInScreen(
    onSignInSuccess: () -> Unit,
    modifier: Modifier = Modifier
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(DayByDayBackground)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Logo (top: 123dp)
            Spacer(modifier = Modifier.height(123.dp))
            Image(
                painter = painterResource(id = R.drawable.day_by_day_logo),
                contentDescription = "Day by Day Logo",
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(width = 120.dp, height = 99.37.dp)
            )

            // Brand Name (top: 250dp -> delta from 123 + 99.37 = 222.37dp, spacer = 27.63dp)
            Spacer(modifier = Modifier.height(27.63.dp))
            Text(
                text = "Day by Day",
                style = TextStyle(
                    fontFamily = DayByDayFontFamily,
                    fontWeight = FontWeight.SemiBold, // 600
                    fontSize = 24.sp,
                    lineHeight = 31.sp,
                    color = DayByDayPrimaryText,
                    textAlign = TextAlign.Center
                )
            )

            // Subtitle (top: 291dp -> delta from 250 + 31 = 281dp, spacer = 10.dp)
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "Welcome back. Sign in to continue.",
                style = TextStyle(
                    fontFamily = DayByDayFontFamily,
                    fontWeight = FontWeight.Normal, // 400
                    fontSize = 13.sp,
                    lineHeight = 17.sp,
                    color = DayByDaySecondaryText,
                    textAlign = TextAlign.Center
                )
            )

            // Email Field (top: 352dp -> delta from 291 + 17 = 308dp, spacer = 44.dp)
            Spacer(modifier = Modifier.height(44.dp))
            CustomInputField(
                label = "EMAIL",
                value = email,
                onValueChange = { email = it },
                placeholder = "you@example.com",
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Next
                ),
                modifier = Modifier.padding(horizontal = 32.dp)
            )

            // Password Field (top: 442dp -> delta from 352 + 72 = 424dp, spacer = 18.dp)
            Spacer(modifier = Modifier.height(18.dp))
            CustomInputField(
                label = "PASSWORD",
                value = password,
                onValueChange = { password = it },
                placeholder = "Enter password",
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = { onSignInSuccess() }
                ),
                modifier = Modifier.padding(horizontal = 32.dp)
            )

            // Sign In Button (top: 542dp -> delta from 442 + 72 = 514dp, spacer = 28.dp)
            Spacer(modifier = Modifier.height(28.dp))
            Surface(
                modifier = Modifier
                    .padding(horizontal = 32.dp)
                    .fillMaxWidth()
                    .height(54.dp)
                    .clickable { onSignInSuccess() },
                shape = RoundedCornerShape(18.dp),
                color = DayByDayAccent
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Sign in",
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

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun CustomInputField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    visualTransformation: VisualTransformation = VisualTransformation.None,
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
                visualTransformation = visualTransformation,
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
    }
}
