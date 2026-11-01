package com.organizador.app.ui.statistics

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.organizador.app.ui.LocalAppActions
import com.organizador.app.ui.common.CardSurface
import com.organizador.app.ui.common.Formats
import com.organizador.app.ui.common.SectionHeader
import com.organizador.domain.model.ActivityEntry
import com.organizador.domain.model.ActivityType
import com.organizador.domain.model.Project
import com.organizador.domain.model.Statistics
import com.organizador.domain.model.StatsPeriod
import com.organizador.domain.model.TimeProvider
import com.organizador.domain.repository.ActivityRepository
import com.organizador.domain.repository.ChecklistRepository
import com.organizador.domain.repository.ProjectRepository
import com.organizador.domain.repository.StatisticsRepository
import com.organizador.domain.repository.TaskRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import java.time.ZoneId
import javax.inject.Inject

enum class PeriodChoice(val label: String, val days: Long) { WEEK("7 dias", 7), MONTH("30 dias", 30), QUARTER("90 dias", 90), YEAR("12 meses", 365) }

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@HiltViewModel
class StatisticsViewModel @Inject constructor(
    stats: StatisticsRepository,
    tasks: TaskRepository,
    checklists: ChecklistRepository,
    projects: ProjectRepository,
    activity: ActivityRepository,
    private val time: TimeProvider,
) : ViewModel() {
    val period = MutableStateFlow(PeriodChoice.WEEK)
    val projectId = MutableStateFlow<String?>(null)
    val projects: StateFlow<List<Project>> = projects.observeProjects(includeArchived = true).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val recent: StateFlow<List<ActivityEntry>> = activity.observeRecent(40).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Recalcula sempre que tarefas ou checklists mudam: os números refletem o banco em tempo real.
    val statistics: StateFlow<Statistics?> = combine(period, projectId, tasks.observeCounts(), checklists.observeSummaries(null)) { p, project, _, _ -> p to project }
        .mapLatest { (p, project) ->
            val today = time.today()
            stats.compute(StatsPeriod(today.minusDays(p.days - 1), today), project, today)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatisticsScreen() {
    val vm: StatisticsViewModel = hiltViewModel()
    val actions = LocalAppActions.current
    val period by vm.period.collectAsStateWithLifecycle()
    val projectId by vm.projectId.collectAsStateWithLifecycle()
    val projects by vm.projects.collectAsStateWithLifecycle()
    val stats by vm.statistics.collectAsStateWithLifecycle()
    val recent by vm.recent.collectAsStateWithLifecycle()
    var projectMenu by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(title = { Text("Estatísticas") }, navigationIcon = { IconButton(onClick = actions.openDrawer) { Icon(Icons.Default.Menu, "Menu") } })
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PeriodChoice.entries.forEach { p -> FilterChip(period == p, { vm.period.value = p }, { Text(p.label) }) }
            Column {
                AssistChip(onClick = { projectMenu = true }, label = { Text(projects.firstOrNull { it.id == projectId }?.name ?: "Todos os projetos") })
                DropdownMenu(projectMenu, { projectMenu = false }) {
                    DropdownMenuItem(text = { Text("Todos os projetos") }, onClick = { vm.projectId.value = null; projectMenu = false })
                    projects.forEach { p -> DropdownMenuItem(text = { Text(p.name) }, onClick = { vm.projectId.value = p.id; projectMenu = false }) }
                }
            }
        }
        val s = stats ?: return@Column
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                    Metric("Concluídas", s.completedTasks.toString(), Modifier.weight(1f))
                    Metric("Pendentes", s.pendingTasks.toString(), Modifier.weight(1f))
                    Metric("Atrasadas", s.overdueTasks.toString(), Modifier.weight(1f), error = s.overdueTasks > 0)
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Metric("Criadas", s.createdTasks.toString(), Modifier.weight(1f))
                    Metric("Hoje", "${s.completedToday}/${s.completedToday + s.dueToday}", Modifier.weight(1f))
                    Metric("Conclusão", "${(s.completionRate * 100).toInt()}%", Modifier.weight(1f))
                }
            }
            item {
                CardSurface {
                    Column(Modifier.padding(14.dp)) {
                        Text("Tarefas concluídas por dia", style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(8.dp))
                        BarChart(s.daily.map { it.completed }, Modifier.fillMaxWidth().height(140.dp))
                        Row(Modifier.fillMaxWidth()) {
                            Text(Formats.shortDate(s.period.from, s.period.to), style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
                            Text(Formats.shortDate(s.period.to, s.period.to), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
            item {
                CardSurface {
                    Column(Modifier.padding(14.dp)) {
                        Text("Execuções de checklists e rotinas", style = MaterialTheme.typography.titleSmall)
                        Text("${s.checklistRuns} execuções · ${(s.checklistRate * 100).toInt()}% completas", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(top = 8.dp)) {
                            Text("Completas ${s.checklistCompleted}")
                            Text("Parciais ${s.checklistPartial}")
                            Text("Perdidas ${s.checklistMissed}", color = if (s.checklistMissed > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            }
            if (s.routines.isNotEmpty()) {
                item { SectionHeader("Taxa de conclusão por rotina/checklist") }
                items(s.routines, key = { it.templateId }) { r ->
                    Column {
                        Row { Text(r.title, modifier = Modifier.weight(1f)); Text("${(r.rate * 100).toInt()}% (${r.completed}/${r.total})") }
                        LinearProgressIndicator(progress = { r.rate }, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp))
                    }
                }
            }
            item { SectionHeader("Atividades recentes") }
            if (recent.isEmpty()) item { Text("Nenhuma atividade registrada.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            items(recent, key = { it.id }) { e ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                    Text(activityLabel(e.type), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(0.4f))
                    Text(e.title, style = MaterialTheme.typography.bodySmall, maxLines = 1, modifier = Modifier.weight(0.6f))
                }
                Text(
                    Formats.relativeDate(e.createdAt.atZone(ZoneId.systemDefault()).toLocalDate(), java.time.LocalDate.now()) + " " +
                        Formats.time(e.createdAt.atZone(ZoneId.systemDefault()).toLocalTime()),
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item { Spacer(Modifier.height(80.dp)) }
        }
    }
}

private fun activityLabel(type: ActivityType): String = when (type) {
    ActivityType.TASK_CREATED -> "Tarefa criada"
    ActivityType.TASK_UPDATED -> "Tarefa editada"
    ActivityType.TASK_COMPLETED -> "Tarefa concluída"
    ActivityType.TASK_REOPENED -> "Tarefa reaberta"
    ActivityType.TASK_DELETED -> "Tarefa excluída"
    ActivityType.TASK_RESTORED -> "Tarefa restaurada"
    ActivityType.TASK_ARCHIVED -> "Tarefa arquivada"
    ActivityType.CHECKLIST_CREATED -> "Checklist criado"
    ActivityType.CHECKLIST_ITEM_CHECKED -> "Item marcado"
    ActivityType.CHECKLIST_COMPLETED -> "Checklist completo"
    ActivityType.CHECKLIST_MISSED -> "Execução perdida"
    ActivityType.NOTE_CREATED -> "Nota criada"
    ActivityType.NOTE_UPDATED -> "Nota editada"
    ActivityType.NOTE_DELETED -> "Nota excluída"
    ActivityType.PROJECT_CREATED -> "Projeto criado"
    ActivityType.BACKUP_EXPORTED -> "Backup exportado"
    ActivityType.BACKUP_RESTORED -> "Backup restaurado"
}

@Composable
private fun Metric(label: String, value: String, modifier: Modifier, error: Boolean = false) {
    CardSurface(modifier) {
        Column(Modifier.padding(12.dp)) {
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun BarChart(values: List<Int>, modifier: Modifier) {
    val color = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.surfaceVariant
    Canvas(modifier) {
        if (values.isEmpty()) return@Canvas
        val max = (values.maxOrNull() ?: 0).coerceAtLeast(1)
        val slot = size.width / values.size
        val barWidth = (slot * 0.7f).coerceAtLeast(1f)
        values.forEachIndexed { i, v ->
            val x = i * slot + (slot - barWidth) / 2
            drawRoundRect(track, Offset(x, 0f), Size(barWidth, size.height), CornerRadius(3f, 3f))
            val h = size.height * v / max
            if (v > 0) drawRoundRect(color, Offset(x, size.height - h), Size(barWidth, h), CornerRadius(3f, 3f))
        }
    }
}
