package com.vikaspokala.daybyday.ui.screens.history

import java.time.format.DateTimeFormatter
import java.util.Locale
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vikaspokala.daybyday.ui.theme.DayByDayAccent
import com.vikaspokala.daybyday.ui.theme.DayByDayBackground
import com.vikaspokala.daybyday.ui.theme.DayByDayNeutralBorder
import com.vikaspokala.daybyday.ui.theme.DayByDayPrimaryText
import com.vikaspokala.daybyday.ui.theme.DayByDaySecondaryText
import com.vikaspokala.daybyday.ui.theme.DayByDaySurface

@Composable
fun HistoryScreen(
    modifier: Modifier = Modifier,
    viewModel: HistoryViewModel = viewModel()
) {
    val tasks by viewModel.tasks.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()

    val filteredGroupedHistory = remember(tasks, searchQuery) {
        viewModel.getFilteredGroupedHistory(searchQuery, tasks)
    }

    val dateFormatter = remember { DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(DayByDayBackground)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 120.dp)
        ) {
            // Header Section
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp)
                ) {
                    Text(
                        text = "History",
                        style = MaterialTheme.typography.displayLarge,
                        color = DayByDayPrimaryText
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Only the important things you wanted to remember.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = DayByDaySecondaryText
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    // Search Input Field
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { viewModel.updateSearchQuery(it) },
                        placeholder = {
                            Text(
                                text = "Search important history",
                                color = DayByDaySecondaryText,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = null,
                                tint = DayByDaySecondaryText
                            )
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { viewModel.updateSearchQuery("") }) {
                                    Icon(
                                        imageVector = Icons.Default.Clear,
                                        contentDescription = "Clear search",
                                        tint = DayByDaySecondaryText
                                    )
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = DayByDaySurface,
                            unfocusedContainerColor = DayByDaySurface,
                            disabledContainerColor = DayByDaySurface,
                            focusedBorderColor = DayByDayAccent,
                            unfocusedBorderColor = DayByDayNeutralBorder,
                            focusedTextColor = DayByDayPrimaryText,
                            unfocusedTextColor = DayByDayPrimaryText
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // Empty States & Grouped History Entries
            if (filteredGroupedHistory.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (searchQuery.trim().isNotEmpty()) {
                                "No matching important history."
                            } else {
                                "No important history yet."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = DayByDaySecondaryText
                        )
                    }
                }
            } else {
                filteredGroupedHistory.forEach { (date, itemsForDate) ->
                    item(key = "header_${date}") {
                        val dateHeaderStr = date.format(dateFormatter)

                        Text(
                            text = dateHeaderStr,
                            style = MaterialTheme.typography.titleMedium,
                            color = DayByDayPrimaryText,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
                        )
                    }

                    items(itemsForDate.size, key = { index -> itemsForDate[index].id }) { index ->
                        val task = itemsForDate[index]
                        Box(
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
                        ) {
                            HistoryCard(task = task)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryCard(
    task: HistoryTaskItem,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = DayByDaySurface,
        border = BorderStroke(1.dp, DayByDayNeutralBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Star,
                contentDescription = null,
                tint = DayByDayAccent,
                modifier = Modifier.size(20.dp)
            )

            Spacer(modifier = Modifier.width(16.dp))

            Text(
                text = task.title,
                style = MaterialTheme.typography.bodyLarge,
                color = DayByDayPrimaryText,
                modifier = Modifier.weight(1f)
            )
        }
    }
}
