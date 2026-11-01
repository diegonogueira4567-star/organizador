package com.organizador.app.ui.dashboard

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.EventNote
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.organizador.app.ui.LocalAppActions
import com.organizador.app.ui.QuickAddPrefill
import com.organizador.app.ui.checklists.ChecklistCard
import com.organizador.app.ui.common.CardSurface
import com.organizador.app.ui.common.Formats
import com.organizador.app.ui.navigation.Routes
import com.organizador.app.ui.tasks.TaskActions
import com.organizador.app.ui.tasks.TaskRow
import com.organizador.app.ui.theme.LocalAppSettings
import com.organizador.app.ui.theme.LocalSpacing
import com.organizador.domain.model.ChecklistKind
import com.organizador.domain.model.ChecklistSummary
import com.organizador.domain.model.DashboardCard
import com.organizador.domain.model.NoteSummary
import com.organizador.domain.model.TaskListItem
import com.organizador.domain.model.TaskQuery
import com.organizador.domain.model.TaskView
import com.organizador.domain.model.TimeProvider
import com.organizador.domain.repository.ChecklistRepository
import com.organizador.domain.repository.NoteRepository
import com.organizador.domain.repository.TaskRepository
import com.organizador.domain.usecase.CompletionResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

@HiltViewModel
class DashboardViewModel @Inject constructor(
    tasks: TaskRepository,
    checklists: ChecklistRepository,
    notes: NoteRepository,
    private val actions: TaskActions,
    time: TimeProvider,
) : ViewModel() {
    private fun <T> state(flow: kotlinx.coroutines.flow.Flow<T>, initial: T): StateFlow<T> = flow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), initial)

    val today: StateFlow<LocalDate> = time.todayFlow().stateIn(viewModelScope, SharingStarted.Eagerly, time.today())
    val todayTasks = state(tasks.observeTasks(TaskQuery(TaskView.Today)), emptyList<TaskListItem>())
    val upcoming = state(tasks.observeTasks(TaskQuery(TaskView.Upcoming(7))), emptyList<TaskListItem>())
    val completed = state(tasks.observeTasks(TaskQuery(TaskView.Completed)), emptyList<TaskListItem>())
    val checklistSummaries = state(checklists.observeSummaries(routines = false), emptyList<ChecklistSummary>())
    val routineSummaries = state(checklists.observeSummaries(routines = true), emptyList<ChecklistSummary>())
    val recentNotes = state(notes.observeRecent(5), emptyList<NoteSummary>())

    fun toggle(item: TaskListItem, onDone: (CompletionResult) -> Unit) = viewModelScope.launch {
        actions.toggle(item.task.id, item.task.isCompleted)?.let(onDone)
    }

    fun undo(result: CompletionResult) = viewModelScope.launch { actions.undo(result) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen() {
    val vm: DashboardViewModel = hiltViewModel()
    val actions = LocalAppActions.current
    val settings = LocalAppSettings.current
    val spacing = LocalSpacing.current
    val context = LocalContext.current
    val today by vm.today.collectAsStateWithLifecycle()
    val todayTasks by vm.todayTasks.collectAsStateWithLifecycle()
    val upcoming by vm.upcoming.collectAsStateWithLifecycle()
    val completed by vm.completed.collectAsStateWithLifecycle()
    val checklists by vm.checklistSummaries.collectAsStateWithLifecycle()
    val routines by vm.routineSummaries.collectAsStateWithLifecycle()
    val notes by vm.recentNotes.collectAsStateWithLifecycle()
    val zone = ZoneId.systemDefault()

    var notificationsGranted by remember {
        mutableStateOf(Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { notificationsGranted = it }

    val overdue = todayTasks.filter { it.task.dueDate?.isBefore(today) == true }
    val dueToday = todayTasks.filter { it.task.dueDate == today }
    val completedToday = completed.count { it.task.completedAt?.atZone(zone)?.toLocalDate() == today }
    val nextDays = upcoming.filter { it.task.dueDate?.isAfter(today) == true }
    val activeChecklists = checklists.filter { it.current != null && it.template.kind != ChecklistKind.TEMPLATE }

    fun toggle(item: TaskListItem) = vm.toggle(item) { r -> actions.showMessage("Tarefa concluída", "Desfazer") { vm.undo(r) } }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Column {
                    Text(greeting(), style = MaterialTheme.typography.titleLarge)
                    Text(Formats.longDate(today), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            navigationIcon = { IconButton(onClick = actions.openDrawer) { Icon(Icons.Default.Menu, "Menu") } },
            actions = { IconButton(onClick = { actions.navigate(Routes.settings("dashboard")) }) { Icon(Icons.Outlined.Settings, "Personalizar painel") } },
        )
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = spacing.screen), verticalArrangement = Arrangement.spacedBy(spacing.gap)) {
            if (!notificationsGranted) {
                item {
                    CardSurface {
                        Row(Modifier.padding(spacing.card), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.NotificationsActive, null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(12.dp))
                            Text("Permita notificações para receber lembretes de tarefas e rotinas.", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                            TextButton(onClick = { if (Build.VERSION.SDK_INT >= 33) permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }) { Text("Permitir") }
                        }
                    }
                }
            }
            settings.dashboardCards.filter { it.visible }.forEach { config ->
                when (config.card) {
                    DashboardCard.DAILY_PROGRESS -> item(key = "progress") {
                        val total = dueToday.size + completedToday
                        ProgressCard(completedToday, total, overdue.size)
                    }
                    DashboardCard.TODAY_TASKS -> item(key = "today") {
                        TaskCard("Hoje", dueToday, today, "Nada para hoje.", { toggle(it) }, { actions.navigate(Routes.task(it.task.id)) }) { actions.navigate(Routes.tasks("today")) }
                    }
                    DashboardCard.OVERDUE -> if (overdue.isNotEmpty()) item(key = "overdue") {
                        TaskCard("Atrasadas", overdue, today, "", { toggle(it) }, { actions.navigate(Routes.task(it.task.id)) }, titleColor = MaterialTheme.colorScheme.error) { actions.navigate(Routes.tasks("overdue")) }
                    }
                    DashboardCard.UPCOMING -> item(key = "upcoming") {
                        TaskCard("Próximos dias", nextDays, today, "Nenhuma tarefa nos próximos 7 dias.", { toggle(it) }, { actions.navigate(Routes.task(it.task.id)) }) { actions.navigate(Routes.tasks("upcoming")) }
                    }
                    DashboardCard.CHECKLISTS_TODAY -> item(key = "checklists") {
                        SummaryList("Checklists", activeChecklists, today, "Nenhum checklist em andamento.", { actions.navigate(Routes.CHECKLISTS) }) { actions.navigate(Routes.checklistRun(it)) }
                    }
                    DashboardCard.ROUTINES -> item(key = "routines") {
                        SummaryList("Rotinas", routines.filter { it.current != null }, today, "Nenhuma rotina em andamento.", { actions.navigate(Routes.ROUTINES) }) { actions.navigate(Routes.checklistRun(it)) }
                    }
                    DashboardCard.RECENT_NOTES -> item(key = "notes") {
                        CardSurface {
                            Column(Modifier.padding(spacing.card)) {
                                CardTitle("Notas recentes") { actions.navigate(Routes.notes()) }
                                if (notes.isEmpty()) Text("Nenhuma nota ainda.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                notes.forEach { n ->
                                    Row(Modifier.fillMaxWidth().clickable { actions.navigate(Routes.note(n.id)) }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.AutoMirrored.Outlined.EventNote, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Spacer(Modifier.width(8.dp))
                                        Text(n.title.ifBlank { n.preview.ifBlank { "Sem título" } }, maxLines = 1, modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    }
                    DashboardCard.WEEKLY_SUMMARY -> item(key = "weekly") { WeeklyCard(completed, today, zone) }
                    DashboardCard.SHORTCUTS -> item(key = "shortcuts") {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            Shortcut(Icons.Outlined.TaskAlt, "Tarefa", Modifier.weight(1f)) { actions.quickAddTask(QuickAddPrefill(date = today)) }
                            Shortcut(Icons.AutoMirrored.Outlined.EventNote, "Nota", Modifier.weight(1f)) { actions.navigate(Routes.note("new")) }
                            Shortcut(Icons.Outlined.Checklist, "Checklist", Modifier.weight(1f)) { actions.navigate(Routes.checklistEdit()) }
                            Shortcut(Icons.Outlined.CalendarMonth, "Agenda", Modifier.weight(1f)) { actions.navigate(Routes.CALENDAR) }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(80.dp)) }
        }
    }
}

private fun greeting(): String = when (java.time.LocalTime.now().hour) {
    in 5..11 -> "Bom dia"
    in 12..17 -> "Boa tarde"
    else -> "Boa noite"
}

@Composable
private fun CardTitle(title: String, color: Color = MaterialTheme.colorScheme.onSurface, onMore: (() -> Unit)?) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = color, modifier = Modifier.weight(1f))
        onMore?.let { TextButton(onClick = it) { Text("Ver tudo") } }
    }
}

@Composable
private fun ProgressCard(done: Int, total: Int, overdue: Int) {
    val spacing = LocalSpacing.current
    CardSurface {
        Row(Modifier.padding(spacing.card), verticalAlignment = Alignment.CenterVertically) {
            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(progress = { if (total == 0) 0f else done.toFloat() / total }, modifier = Modifier.width(56.dp).height(56.dp), strokeWidth = 6.dp)
                Text(if (total == 0) "–" else "${done * 100 / total}%", style = MaterialTheme.typography.labelLarge)
            }
            Spacer(Modifier.width(16.dp))
            Column {
                Text("Progresso de hoje", style = MaterialTheme.typography.titleMedium)
                Text("$done de $total tarefas concluídas", color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (overdue > 0) Text("$overdue atrasada(s)", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun TaskCard(
    title: String,
    items: List<TaskListItem>,
    today: LocalDate,
    empty: String,
    onToggle: (TaskListItem) -> Unit,
    onOpen: (TaskListItem) -> Unit,
    titleColor: Color = MaterialTheme.colorScheme.onSurface,
    onMore: () -> Unit,
) {
    CardSurface {
        Column(Modifier.padding(vertical = 8.dp)) {
            Box(Modifier.padding(horizontal = 14.dp)) { CardTitle("$title (${items.size})", titleColor, onMore) }
            if (items.isEmpty() && empty.isNotEmpty()) Text(empty, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp))
            items.take(5).forEach { item -> TaskRow(item, today, onToggle = { onToggle(item) }, onClick = { onOpen(item) }, onLongClick = { onOpen(item) }) }
            if (items.size > 5) TextButton(onClick = onMore, modifier = Modifier.padding(horizontal = 6.dp)) { Text("+ ${items.size - 5} tarefas") }
        }
    }
}

@Composable
private fun SummaryList(title: String, items: List<ChecklistSummary>, today: LocalDate, empty: String, onMore: () -> Unit, onOpen: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        CardTitle(title, onMore = onMore)
        if (items.isEmpty()) Text(empty, color = MaterialTheme.colorScheme.onSurfaceVariant)
        items.take(4).forEach { s -> ChecklistCard(s, today, onClick = { onOpen(s.template.id) }, onLongClick = { onOpen(s.template.id) }) }
    }
}

@Composable
private fun WeeklyCard(completed: List<TaskListItem>, today: LocalDate, zone: ZoneId) {
    val spacing = LocalSpacing.current
    val days = (6 downTo 0).map { today.minusDays(it.toLong()) }
    val counts = days.map { d -> completed.count { it.task.completedAt?.atZone(zone)?.toLocalDate() == d } }
    val max = (counts.maxOrNull() ?: 0).coerceAtLeast(1)
    CardSurface {
        Column(Modifier.padding(spacing.card)) {
            CardTitle("Resumo da semana", onMore = null)
            Text("${counts.sum()} tarefas concluídas nos últimos 7 dias", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            Row(Modifier.fillMaxWidth().height(110.dp).padding(top = 12.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.Bottom) {
                days.zip(counts).forEach { (day, count) ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxHeight(), verticalArrangement = Arrangement.Bottom) {
                        Text("$count", style = MaterialTheme.typography.labelSmall)
                        Box(
                            Modifier.width(18.dp).height((70f * count / max).dp.coerceAtLeast(2.dp))
                                .background(if (day == today) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.45f), RoundedCornerShape(4.dp)),
                        )
                        Text(Formats.weekdayLetter(day.dayOfWeek).take(1), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun Shortcut(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, modifier: Modifier, onClick: () -> Unit) {
    FilledTonalButton(onClick = onClick, modifier = modifier, contentPadding = androidx.compose.foundation.layout.PaddingValues(4.dp)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null)
            Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1)
        }
    }
}
