package com.organizador.app.ui.tasks

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.organizador.app.ui.common.Chip
import com.organizador.app.ui.common.ColorDot
import com.organizador.app.ui.common.Formats
import com.organizador.app.ui.common.PriorityFlag
import com.organizador.app.ui.common.TagChip
import com.organizador.app.ui.common.TaskCheckCircle
import com.organizador.app.ui.theme.LocalAppSettings
import com.organizador.app.ui.theme.LocalSpacing
import com.organizador.domain.model.TaskListItem
import java.time.LocalDate

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun TaskRow(
    item: TaskListItem,
    today: LocalDate,
    onToggle: () -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    showProject: Boolean = true,
    showDate: Boolean = true,
) {
    val task = item.task
    val spacing = LocalSpacing.current
    val settings = LocalAppSettings.current
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = onClick, onLongClick = onLongClick)
                .padding(horizontal = spacing.screen, vertical = spacing.itemVertical),
            verticalAlignment = Alignment.Top,
        ) {
            TaskCheckCircle(task.isCompleted, task.priority, onToggle, Modifier.padding(top = 1.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    task.title,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textDecoration = if (task.isCompleted) TextDecoration.LineThrough else null,
                    color = if (task.isCompleted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                )
                if (task.description.isNotBlank()) {
                    Text(
                        task.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 2.dp)) {
                    val due = task.dueDate
                    if (showDate && due != null) {
                        val overdue = !task.isCompleted && due.isBefore(today)
                        val color = when {
                            overdue -> MaterialTheme.colorScheme.error
                            due == today -> Color(0xFF058527)
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                        val label = Formats.relativeDate(due, today) + (task.dueTime?.let { " " + Formats.time(it, settings.timeFormat) } ?: "")
                        Chip(label, color, Icons.Outlined.CalendarToday)
                    } else if (!showDate && task.dueTime != null) {
                        Chip(Formats.time(task.dueTime!!, settings.timeFormat), MaterialTheme.colorScheme.onSurfaceVariant, Icons.Outlined.CalendarToday)
                    }
                    if (item.isRecurring) Chip("", MaterialTheme.colorScheme.onSurfaceVariant, Icons.Outlined.Repeat)
                    if (item.hasReminder) Chip("", MaterialTheme.colorScheme.onSurfaceVariant, Icons.Outlined.Alarm)
                    if (item.subtaskTotal > 0) Chip("${item.subtaskDone}/${item.subtaskTotal}", MaterialTheme.colorScheme.onSurfaceVariant, Icons.Outlined.Checklist)
                    item.tags.forEach { TagChip(it.name, it.color) }
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                PriorityFlag(task.priority)
                if (showProject && item.projectName != null) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                        Text(item.projectName!!, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                        Spacer(Modifier.width(4.dp))
                        ColorDot(item.projectColor ?: 0xFF9E9E9E, size = 8)
                    }
                }
            }
        }
        HorizontalDivider(Modifier.padding(start = spacing.screen + 36.dp), color = MaterialTheme.colorScheme.outlineVariant)
    }
}
