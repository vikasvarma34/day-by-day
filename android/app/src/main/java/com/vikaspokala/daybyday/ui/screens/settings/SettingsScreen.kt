package com.vikaspokala.daybyday.ui.screens.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vikaspokala.daybyday.ui.navigation.ProfileFieldType
import com.vikaspokala.daybyday.ui.theme.DayByDayAccent
import com.vikaspokala.daybyday.ui.theme.DayByDayBackground
import com.vikaspokala.daybyday.ui.theme.DayByDayFontFamily
import com.vikaspokala.daybyday.ui.theme.DayByDayNeutralBorder
import com.vikaspokala.daybyday.ui.theme.DayByDayPrimaryText
import com.vikaspokala.daybyday.ui.theme.DayByDaySecondaryText
import com.vikaspokala.daybyday.ui.theme.DayByDayStrongAccent
import com.vikaspokala.daybyday.ui.theme.DayByDaySurface

@Composable
fun SettingsScreen(
    firstName: String = "Vikas",
    lastName: String = "Varma",
    nickname: String = "Vicky",
    onNavigateBack: () -> Unit,
    onChangePasswordClick: () -> Unit = {},
    onEditFieldClick: (ProfileFieldType) -> Unit = {},
    refreshState: RefreshUiState = RefreshUiState.Idle,
    onRefreshClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val initialLetter = if (firstName.isNotEmpty()) firstName.take(1).uppercase() else "V"

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
                                fontWeight = FontWeight.W500, // 500
                                fontSize = 24.sp,
                                lineHeight = 31.sp,
                                color = DayByDayPrimaryText
                            )
                        )
                    }
                }

                // Title ("Settings")
                Text(
                    text = "Settings",
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

            // Profile Summary Card (348dp x 94dp)
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(94.dp),
                shape = RoundedCornerShape(22.dp),
                color = DayByDaySurface,
                border = BorderStroke(1.dp, DayByDayNeutralBorder)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 19.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Avatar (56dp x 56dp)
                    Surface(
                        modifier = Modifier.size(56.dp),
                        shape = CircleShape,
                        color = DayByDayAccent
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = initialLetter,
                                style = TextStyle(
                                    fontFamily = DayByDayFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 24.sp,
                                    color = Color.White
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    // Profile Text
                    Column(
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = "$firstName $lastName",
                            style = TextStyle(
                                fontFamily = DayByDayFontFamily,
                                fontWeight = FontWeight.W500, // 500
                                fontSize = 17.sp,
                                lineHeight = 22.sp,
                                color = DayByDayPrimaryText
                            )
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Nickname: $nickname",
                            style = TextStyle(
                                fontFamily = DayByDayFontFamily,
                                fontWeight = FontWeight.W400, // 400
                                fontSize = 12.5.sp,
                                lineHeight = 16.sp,
                                color = DayByDaySecondaryText
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Profile Section
            SectionLabel(text = "Profile")
            Spacer(modifier = Modifier.height(8.dp))
            StandardSettingsRow(
                title = "First name",
                subtitle = firstName,
                onClick = { onEditFieldClick(ProfileFieldType.FIRST_NAME) }
            )
            Spacer(modifier = Modifier.height(8.dp))
            StandardSettingsRow(
                title = "Last name",
                subtitle = lastName,
                onClick = { onEditFieldClick(ProfileFieldType.LAST_NAME) }
            )
            Spacer(modifier = Modifier.height(8.dp))
            StandardSettingsRow(
                title = "Nickname",
                subtitle = nickname,
                onClick = { onEditFieldClick(ProfileFieldType.NICKNAME) }
            )

            Spacer(modifier = Modifier.height(18.dp))

            // Account Section
            SectionLabel(text = "Account")
            Spacer(modifier = Modifier.height(8.dp))
            StandardSettingsRow(
                title = "Refresh planner data",
                subtitle = when (refreshState) {
                    is RefreshUiState.Error -> "Couldn’t refresh right now."
                    is RefreshUiState.Refreshing -> "Refreshing..."
                    is RefreshUiState.Idle -> null
                },
                statusText = when (refreshState) {
                    is RefreshUiState.Refreshing -> "Refreshing..."
                    is RefreshUiState.Error -> "Retry"
                    is RefreshUiState.Idle -> null
                },
                onClick = if (refreshState !is RefreshUiState.Refreshing) onRefreshClick else null
            )
            Spacer(modifier = Modifier.height(8.dp))
            StandardSettingsRow(
                title = "Change password",
                onClick = onChangePasswordClick
            )
            Spacer(modifier = Modifier.height(8.dp))
            StandardSettingsRow(
                title = "Logout"
            )

            Spacer(modifier = Modifier.height(18.dp))

            // Notifications Section
            SectionLabel(text = "Notifications")
            Spacer(modifier = Modifier.height(8.dp))
            StandardSettingsRow(
                title = "Notification permission",
                subtitle = "Open device settings",
                statusText = "Allowed",
                showChevron = false
            )
            Spacer(modifier = Modifier.height(8.dp))
            StandardSettingsRow(
                title = "Test notification",
                subtitle = "Send one sample reminder"
            )

            Spacer(modifier = Modifier.height(18.dp))

            // About Section
            SectionLabel(text = "About")
            Spacer(modifier = Modifier.height(8.dp))
            StandardSettingsRow(
                title = "Day by Day",
                subtitle = "Version 1.0",
                showChevron = false
            )

            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}

@Composable
private fun SectionLabel(
    text: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = text,
        style = TextStyle(
            fontFamily = DayByDayFontFamily,
            fontWeight = FontWeight.W600, // 600
            fontSize = 12.5.sp,
            lineHeight = 16.sp,
            color = DayByDaySecondaryText
        ),
        modifier = modifier
    )
}

@Composable
private fun StandardSettingsRow(
    title: String,
    subtitle: String? = null,
    statusText: String? = null,
    showChevron: Boolean = true,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp)
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier),
        shape = RoundedCornerShape(18.dp),
        color = DayByDaySurface,
        border = BorderStroke(1.dp, DayByDayNeutralBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 16.dp, end = 14.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = title,
                    style = TextStyle(
                        fontFamily = DayByDayFontFamily,
                        fontWeight = FontWeight.W400, // 400
                        fontSize = 14.sp,
                        lineHeight = 18.sp,
                        color = DayByDayPrimaryText
                    )
                )
                if (!subtitle.isNullOrEmpty()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        style = TextStyle(
                            fontFamily = DayByDayFontFamily,
                            fontWeight = FontWeight.W400, // 400
                            fontSize = 11.sp,
                            lineHeight = 14.sp,
                            color = DayByDaySecondaryText
                        )
                    )
                }
            }

            if (!statusText.isNullOrEmpty()) {
                Text(
                    text = statusText,
                    style = TextStyle(
                        fontFamily = DayByDayFontFamily,
                        fontWeight = FontWeight.W400, // 400
                        fontSize = 14.sp,
                        lineHeight = 18.sp,
                        color = DayByDayStrongAccent // #B75B2A
                    )
                )
                if (showChevron) {
                    Spacer(modifier = Modifier.width(8.dp))
                }
            }

            if (showChevron) {
                Text(
                    text = "›",
                    style = TextStyle(
                        fontFamily = DayByDayFontFamily,
                        fontWeight = FontWeight.W400, // 400
                        fontSize = 14.sp,
                        lineHeight = 18.sp,
                        color = DayByDaySecondaryText
                    )
                )
            }
        }
    }
}
