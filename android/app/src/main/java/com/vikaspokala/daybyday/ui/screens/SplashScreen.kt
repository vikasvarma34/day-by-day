package com.vikaspokala.daybyday.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.vikaspokala.daybyday.R
import com.vikaspokala.daybyday.ui.theme.DayByDayBackground

@Composable
fun SplashScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DayByDayBackground),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.day_by_day_logo),
            contentDescription = "Day by Day Logo",
            contentScale = ContentScale.Fit,
            modifier = Modifier.width(180.dp)
        )
    }
}
