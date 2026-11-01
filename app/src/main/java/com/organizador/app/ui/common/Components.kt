package com.organizador.app.ui.common

import androidx.compose.animation.scaleOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.Work
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.organizador.app.ui.theme.LocalAppSettings
import com.organizador.app.ui.theme.PriorityColors
import com.organizador.domain.model.Palette
import com.organizador.domain.model.Priority
import com.organizador.domain.model.TimeFormatPref
import java.time.LocalDate
import java.time.LocalTime

fun priorityColor(priority: Priority): Color = when (priority) {
    Priority.P1 -> PriorityColors.p1
    Priority.P2 -> PriorityColors.p2
    Priority.P3 -> PriorityColors.p3
    Priority.P4 -> PriorityColors.p4
}

fun priorityLabel(priority: Priority): String = when (priority) {
    Priority.P1 -> "Prioridade 1"
    Priority.P2 -> "Prioridade 2"
    Priority.P3 -> "Prioridade 3"
    Priority.P4 -> "Sem prioridade"
}

val projectIcons: Map<String, ImageVector> = linkedMapOf(
    "folder" to Icons.Outlined.Folder,
    "home" to Icons.Outlined.Home,
    "work" to Icons.Outlined.Work,
    "school" to Icons.Outlined.School,
    "cart" to Icons.Outlined.ShoppingCart,
    "star" to Icons.Outlined.Star,
    "heart" to Icons.Outlined.Favorite,
    "book" to Icons.Outlined.Book,
    "idea" to Icons.Outlined.Lightbulb,
    "tools" to Icons.Outlined.Build,
)

fun projectIcon(name: String): ImageVector = projectIcons[name] ?: Icons.Outlined.Folder

@Composable
fun PriorityFlag(priority: Priority, modifier: Modifier = Modifier) {
    if (priority == Priority.P4) return
    Icon(Icons.Filled.Flag, contentDescription = priorityLabel(priority), tint = priorityColor(priority), modifier = modifier.size(16.dp))
}

@Composable
fun ColorDot(color: Long, modifier: Modifier = Modifier, size: Int = 10) {
    Box(modifier.size(size.dp).clip(CircleShape).background(Color(color)))
}

/** Círculo de conclusão no estilo de listas de tarefas, colorido pela prioridade. */
@Composable
fun TaskCheckCircle(checked: Boolean, priority: Priority, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    val color = priorityColor(priority)
    val fill by animateColorAsState(if (checked) color else color.copy(alpha = if (priority == Priority.P4) 0f else 0.10f), label = "conclusão")
    Box(
        modifier
            .size(24.dp)
            .clip(CircleShape)
            .background(fill)
            .border(1.6.dp, color, CircleShape)
            .clickable(onClickLabel = if (checked) "Reabrir" else "Concluir", onClick = onToggle),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedVisibility(visible = checked, enter = scaleIn() + fadeIn(), exit = scaleOut() + fadeOut()) {
            Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
fun EmptyState(icon: ImageVector, title: String, message: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Column(
        modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f), modifier = Modifier.size(56.dp))
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        action?.let {
            Spacer(Modifier.height(16.dp))
            it()
        }
    }
}

@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier, trailing: (@Composable () -> Unit)? = null) {
    Row(modifier.fillMaxWidth().padding(top = 16.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        trailing?.invoke()
    }
}

@Composable
fun Chip(text: String, color: Color = MaterialTheme.colorScheme.onSurfaceVariant, icon: ImageVector? = null, modifier: Modifier = Modifier) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
        icon?.let { Icon(it, null, tint = color, modifier = Modifier.size(13.dp)); Spacer(Modifier.width(3.dp)) }
        Text(text, style = MaterialTheme.typography.labelSmall, color = color, maxLines = 1)
    }
}

@Composable
fun TagChip(name: String, color: Long) = Chip(name, Color(color), Icons.AutoMirrored.Filled.Label)

@Composable
fun ConfirmDialog(
    title: String,
    text: String,
    confirmLabel: String = "Confirmar",
    destructive: Boolean = false,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            TextButton(onClick = { onConfirm(); onDismiss() }) {
                Text(confirmLabel, color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
fun TextInputDialog(
    title: String,
    initial: String = "",
    label: String = "Nome",
    confirmLabel: String = "Salvar",
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var value by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value, onValueChange = { value = it }, label = { Text(label) }, singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            )
        },
        confirmButton = {
            TextButton(enabled = value.isNotBlank(), onClick = { onConfirm(value.trim()); onDismiss() }) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatePickerModal(initial: LocalDate?, onPick: (LocalDate?) -> Unit, onDismiss: () -> Unit, allowClear: Boolean = true) {
    val state = rememberDatePickerState(initialSelectedDateMillis = initial?.let { it.toEpochDay() * DAY_MS })
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let { onPick(LocalDate.ofEpochDay(Math.floorDiv(it, DAY_MS))) }
                onDismiss()
            }) { Text("OK") }
        },
        dismissButton = {
            Row {
                if (allowClear) TextButton(onClick = { onPick(null); onDismiss() }) { Text("Sem data") }
                TextButton(onClick = onDismiss) { Text("Cancelar") }
            }
        },
    ) { DatePicker(state = state) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimePickerModal(initial: LocalTime?, onPick: (LocalTime?) -> Unit, onDismiss: () -> Unit, allowClear: Boolean = true) {
    val settings = LocalAppSettings.current
    val start = initial ?: LocalTime.of(9, 0)
    val state = rememberTimePickerState(start.hour, start.minute, is24Hour = settings.timeFormat == TimeFormatPref.H24)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Horário") },
        text = { TimePicker(state = state) },
        confirmButton = { TextButton(onClick = { onPick(LocalTime.of(state.hour, state.minute)); onDismiss() }) { Text("OK") } },
        dismissButton = {
            Row {
                if (allowClear) TextButton(onClick = { onPick(null); onDismiss() }) { Text("Sem horário") }
                TextButton(onClick = onDismiss) { Text("Cancelar") }
            }
        },
    )
}

@Composable
fun ColorPickerRow(selected: Long, onSelect: (Long) -> Unit, colors: List<Long> = Palette.colors) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items(colors) { color ->
            Box(
                Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(Color(color))
                    .border(if (color == selected) 3.dp else 0.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                    .clickable { onSelect(color) },
            )
        }
    }
}

@Composable
fun CardSurface(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable () -> Unit) {
    val base = modifier.fillMaxWidth()
    if (onClick != null) {
        Surface(onClick = onClick, modifier = base, shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLowest, tonalElevation = 0.dp, shadowElevation = 0.5.dp) { content() }
    } else {
        Surface(modifier = base, shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLowest, shadowElevation = 0.5.dp) { content() }
    }
}

private const val DAY_MS = 86_400_000L
