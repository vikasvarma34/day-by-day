package com.vikaspokala.daybyday.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.vikaspokala.daybyday.ui.theme.DayByDayAccent
import com.vikaspokala.daybyday.ui.theme.DayByDayNeutralBorder
import com.vikaspokala.daybyday.ui.theme.DayByDayPrimaryText
import com.vikaspokala.daybyday.ui.theme.DayByDaySecondaryText
import com.vikaspokala.daybyday.ui.theme.DayByDaySurface

@Composable
fun TaskCard(
    title: String,
    subtitle: String? = null,
    isImportant: Boolean,
    isCompleted: Boolean,
    onToggleCompletion: () -> Unit,
    onToggleImportant: () -> Unit,
    modifier: Modifier = Modifier
) {
    val borderColor = if (isImportant && !isCompleted) DayByDayAccent else DayByDayNeutralBorder

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = DayByDaySurface,
        border = BorderStroke(1.dp, borderColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Completion circle control
            val checkCircleBorderColor = if (isCompleted) DayByDaySecondaryText else DayByDayNeutralBorder
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .border(1.5.dp, checkCircleBorderColor, CircleShape)
                    .clickable(
                        onClick = onToggleCompletion,
                        onClickLabel = if (isCompleted) "Mark task incomplete" else "Mark task completed"
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (isCompleted) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = DayByDaySecondaryText
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Task Title & Subtitle Column
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (isCompleted) DayByDaySecondaryText else DayByDayPrimaryText,
                    textDecoration = if (isCompleted) TextDecoration.LineThrough else TextDecoration.None
                )
                if (!subtitle.isNullOrEmpty()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = DayByDaySecondaryText
                    )
                }
            }

            // Star / Important toggle button
            val starTint = if (isImportant) DayByDayAccent else DayByDayNeutralBorder
            IconButton(
                onClick = onToggleImportant
            ) {
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = if (isImportant) "Mark task as unimportant" else "Mark task as important",
                    tint = starTint
                )
            }
        }
    }
}
