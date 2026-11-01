package com.organizador.app.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.organizador.app.ui.LocalAppActions
import com.organizador.app.ui.QuickAddPrefill
import com.organizador.app.ui.common.Formats
import com.organizador.app.ui.common.SectionHeader
import com.organizador.app.ui.common.orderedWeek
import com.organizador.app.ui.common.priorityColor
import com.organizador.app.ui.navigation.Routes
import com.organizador.app.ui.theme.LocalAppSettings
import com.organizador.domain.model.ChecklistKind
import com.organizador.domain.model.OccurrenceStatus
import com.organizador.domain.model.TimeProvider
import com.organizador.domain.recurrence.RecurrenceEngine
import com.organizador.domain.repository.ChecklistRepository
import com.organizador.domain.repository.TaskRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import javax.inject.Inject

enum class CalendarMode(val label: String) { MONTH("Mês"), WEEK("Semana"), DAY("Dia"), AGENDA("Agenda") }
enum class EntryKind { TASK, CHECKLIST, ROUTINE }

/** Item exibido no calendário. [projected] = ocorrência futura calculada pela regra (ainda não materializada). */
data class CalendarEntry(
    val date: LocalDate,
    val time: LocalTime?,
    val title: String,
    val kind: EntryKind,
    val done: Boolean,
    val route: String,
    val color: Color,
    val projected: Boolean = false,
    val status: OccurrenceStatus? = null,
)

data class Range(val from: LocalDate, val to: LocalDate)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class CalendarViewModel @Inject constructor(
    tasks: TaskRepository,
    checklists: ChecklistRepository,
    private val time: TimeProvider,
) : ViewModel() {
    val selected = MutableStateFlow(time.today())
    val mode = MutableStateFlow(CalendarMode.MONTH)
    val today: LocalDate get() = time.today()

    private val range: StateFlow<Range> = combine(selected, mode) { d, m ->
        when (m) {
            CalendarMode.MONTH -> YearMonth.from(d).let { Range(it.atDay(1).minusDays(7), it.atEndOfMonth().plusDays(14)) }
            CalendarMode.WEEK -> Range(d.minusDays(7), d.plusDays(7))
            CalendarMode.DAY -> Range(d, d)
            CalendarMode.AGENDA -> Range(d, d.plusDays(30))
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, Range(time.today(), time.today()))

    val entries: StateFlow<Map<LocalDate, List<CalendarEntry>>> = range.flatMapLatest { r ->
        combine(
            tasks.observeTasksDueBetween(r.from, r.to),
            checklists.observeOccurrencesBetween(r.from, r.to),
            checklists.observeSummaries(routines = null),
            tasks.observeRecurringOpenTasks(),
        ) { taskItems, occurrences, summaries, recurringTasks ->
            val list = ArrayList<CalendarEntry>()
            taskItems.forEach { item ->
                val t = item.task
                list += CalendarEntry(t.dueDate!!, t.dueTime, t.title, EntryKind.TASK, t.isCompleted, Routes.task(t.id), priorityColor(t.priority))
            }
            val stored = HashSet<Pair<String, LocalDate>>()
            occurrences.forEach { (template, occ) ->
                stored += template.id to occ.date
                list += CalendarEntry(
                    occ.date, occ.dueAt?.atZone(ZoneId.systemDefault())?.toLocalTime(), template.title,
                    if (template.isRoutine) EntryKind.ROUTINE else EntryKind.CHECKLIST,
                    occ.status == OccurrenceStatus.COMPLETED,
                    if (occ.isOpen) Routes.checklistRun(template.id) else Routes.occurrence(occ.id),
                    Color(template.color), status = occ.status,
                )
            }
            // Instâncias futuras de tarefas recorrentes: só a próxima existe no banco; as demais são previstas.
            recurringTasks.forEach { (t, rule) ->
                RecurrenceEngine.projectedAfter(rule, t.dueDate!!, r.from, r.to).forEach { date ->
                    list += CalendarEntry(date, t.dueTime, t.title, EntryKind.TASK, false, Routes.task(t.id), priorityColor(t.priority), projected = true)
                }
            }
            // Ocorrências futuras previstas pela regra (sem gerar registros no banco).
            val today = time.today()
            summaries.filter { it.template.kind == ChecklistKind.RECURRING && it.rule?.enabled == true && !it.template.archived }.forEach { s ->
                val rule = s.rule!!
                RecurrenceEngine.occurrencesBetween(rule, maxOf(r.from, today), r.to, limit = 62)
                    .filter { (s.template.id to it) !in stored }
                    .forEach { date ->
                        list += CalendarEntry(
                            date, rule.timeOfDay, s.template.title, if (s.template.isRoutine) EntryKind.ROUTINE else EntryKind.CHECKLIST,
                            false, Routes.checklistRun(s.template.id), Color(s.template.color), projected = true,
                        )
                    }
            }
            list.sortedWith(compareBy<CalendarEntry>({ it.date }, { it.time == null }, { it.time }, { it.title })).groupBy { it.date }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    fun shift(direction: Int) {
        val d = selected.value
        selected.value = when (mode.value) {
            CalendarMode.MONTH -> d.plusMonths(direction.toLong())
            CalendarMode.WEEK -> d.plusWeeks(direction.toLong())
            CalendarMode.DAY -> d.plusDays(direction.toLong())
            CalendarMode.AGENDA -> d.plusDays(30L * direction)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen() {
    val vm: CalendarViewModel = hiltViewModel()
    val actions = LocalAppActions.current
    val settings = LocalAppSettings.current
    val selected by vm.selected.collectAsStateWithLifecycle()
    val mode by vm.mode.collectAsStateWithLifecycle()
    val entries by vm.entries.collectAsStateWithLifecycle()
    var addMenu by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(Formats.monthYear(selected)) },
            navigationIcon = { IconButton(onClick = actions.openDrawer) { Icon(Icons.Default.Menu, "Menu") } },
            actions = {
                IconButton(onClick = { vm.shift(-1) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Anterior") }
                IconButton(onClick = { vm.selected.value = vm.today }) { Icon(Icons.Outlined.Today, "Hoje") }
                IconButton(onClick = { vm.shift(1) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Próximo") }
                IconButton(onClick = { addMenu = true }) { Icon(Icons.Default.Add, "Criar neste dia") }
                DropdownMenu(addMenu, { addMenu = false }) {
                    DropdownMenuItem(text = { Text("Tarefa em ${Formats.shortDate(selected, vm.today)}") }, onClick = { addMenu = false; actions.quickAddTask(QuickAddPrefill(date = selected)) })
                    DropdownMenuItem(text = { Text("Checklist a partir deste dia") }, onClick = { addMenu = false; actions.navigate(Routes.checklistEdit(kind = "RECURRING", date = selected.toString())) })
                    DropdownMenuItem(text = { Text("Rotina a partir deste dia") }, onClick = { addMenu = false; actions.navigate(Routes.checklistEdit(kind = "RECURRING", routine = true, date = selected.toString())) })
                }
            },
        )
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            CalendarMode.entries.forEachIndexed { i, m ->
                SegmentedButton(selected = mode == m, onClick = { vm.mode.value = m }, shape = SegmentedButtonDefaults.itemShape(i, CalendarMode.entries.size)) { Text(m.label) }
            }
        }
        when (mode) {
            CalendarMode.MONTH -> {
                MonthGrid(selected, vm.today, settings.firstDayOfWeek, entries) { vm.selected.value = it }
                DayList(selected, entries[selected].orEmpty(), vm.today) { actions.navigate(it) }
            }
            CalendarMode.WEEK -> {
                WeekStrip(selected, vm.today, settings.firstDayOfWeek, entries) { vm.selected.value = it }
                val start = selected.with(TemporalAdjusters.previousOrSame(settings.firstDayOfWeek))
                LazyColumn(Modifier.fillMaxSize()) {
                    (0L until 7L).map { start.plusDays(it) }.forEach { day ->
                        item(key = day.toString()) { SectionHeader(Formats.longDate(day), Modifier.padding(horizontal = 16.dp)) }
                        val dayEntries = entries[day].orEmpty()
                        if (dayEntries.isEmpty()) item(key = "$day-empty") { Text("—", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp)) }
                        items(dayEntries, key = { "$day-${it.route}-${it.title}-${it.projected}" }) { EntryRow(it) { actions.navigate(it.route) } }
                    }
                }
            }
            CalendarMode.DAY -> DayList(selected, entries[selected].orEmpty(), vm.today, timeline = true) { actions.navigate(it) }
            CalendarMode.AGENDA -> LazyColumn(Modifier.fillMaxSize()) {
                val days = entries.keys.filter { !it.isBefore(selected) }.sorted()
                if (days.isEmpty()) item { Text("Nada agendado nos próximos 30 dias.", modifier = Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                days.forEach { day ->
                    item(key = "a-$day") { SectionHeader(Formats.relativeDate(day, vm.today) + " · " + Formats.longDate(day), Modifier.padding(horizontal = 16.dp)) }
                    items(entries[day].orEmpty(), key = { "a-$day-${it.route}-${it.title}-${it.projected}" }) { EntryRow(it) { actions.navigate(it.route) } }
                }
            }
        }
    }
}

@Composable
private fun MonthGrid(selected: LocalDate, today: LocalDate, firstDay: DayOfWeek, entries: Map<LocalDate, List<CalendarEntry>>, onSelect: (LocalDate) -> Unit) {
    val month = YearMonth.from(selected)
    val start = month.atDay(1).with(TemporalAdjusters.previousOrSame(firstDay))
    Column(Modifier.padding(horizontal = 8.dp, vertical = 8.dp)) {
        Row(Modifier.fillMaxWidth()) {
            orderedWeek(firstDay).forEach { d ->
                Text(Formats.weekdayLetter(d), style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
        }
        (0 until 6).forEach { week ->
            Row(Modifier.fillMaxWidth()) {
                (0 until 7).forEach { dayIndex ->
                    val date = start.plusDays((week * 7 + dayIndex).toLong())
                    DayCell(date, date == selected, date == today, YearMonth.from(date) == month, entries[date].orEmpty(), Modifier.weight(1f)) { onSelect(date) }
                }
            }
        }
    }
}

@Composable
private fun WeekStrip(selected: LocalDate, today: LocalDate, firstDay: DayOfWeek, entries: Map<LocalDate, List<CalendarEntry>>, onSelect: (LocalDate) -> Unit) {
    val start = selected.with(TemporalAdjusters.previousOrSame(firstDay))
    Row(Modifier.fillMaxWidth().padding(8.dp)) {
        (0L until 7L).map { start.plusDays(it) }.forEach { date ->
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(Formats.weekdayLetter(date.dayOfWeek), style = MaterialTheme.typography.labelSmall)
                DayCell(date, date == selected, date == today, true, entries[date].orEmpty(), Modifier.fillMaxWidth()) { onSelect(date) }
            }
        }
    }
}

@Composable
private fun DayCell(date: LocalDate, selected: Boolean, isToday: Boolean, inMonth: Boolean, entries: List<CalendarEntry>, modifier: Modifier, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(modifier.aspectRatio(1f).padding(2.dp).clip(MaterialTheme.shapes.small).clickable(onClick = onClick)
        .background(if (selected) colors.primaryContainer else Color.Transparent), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Box(Modifier.size(26.dp).clip(CircleShape).background(if (isToday) colors.primary else Color.Transparent), contentAlignment = Alignment.Center) {
            Text(
                "${date.dayOfMonth}",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = if (isToday || selected) FontWeight.Bold else null,
                color = when {
                    isToday -> colors.onPrimary
                    !inMonth -> colors.onSurfaceVariant.copy(alpha = 0.5f)
                    else -> colors.onSurface
                },
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.height(6.dp)) {
            entries.distinctBy { it.kind to it.done }.take(3).forEach { e ->
                Box(Modifier.size(5.dp).clip(CircleShape).background(if (e.done) colors.outline else e.color.copy(alpha = if (e.projected) 0.4f else 1f)))
            }
        }
    }
}

@Composable
private fun DayList(date: LocalDate, entries: List<CalendarEntry>, today: LocalDate, timeline: Boolean = false, onOpen: (String) -> Unit) {
    LazyColumn(Modifier.fillMaxSize()) {
        item { SectionHeader(Formats.relativeDate(date, today) + " · " + Formats.longDate(date), Modifier.padding(horizontal = 16.dp)) }
        if (entries.isEmpty()) item { Text("Nenhuma atividade neste dia.", modifier = Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        if (timeline) {
            val allDay = entries.filter { it.time == null }
            if (allDay.isNotEmpty()) {
                item { Text("Dia inteiro", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) }
                items(allDay, key = { "d-${it.route}-${it.title}-${it.projected}" }) { EntryRow(it) { onOpen(it.route) } }
            }
            entries.filter { it.time != null }.groupBy { it.time!!.hour }.toSortedMap().forEach { (hour, list) ->
                item(key = "hour-$hour") { Text("%02d:00".format(java.util.Locale.ROOT, hour), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) }
                items(list, key = { "t-${it.route}-${it.title}-${it.projected}" }) { EntryRow(it) { onOpen(it.route) } }
            }
        } else {
            items(entries, key = { "${it.route}-${it.title}-${it.projected}" }) { EntryRow(it) { onOpen(it.route) } }
        }
        item { Spacer(Modifier.height(80.dp)) }
    }
}

@Composable
private fun EntryRow(entry: CalendarEntry, onClick: () -> Unit) {
    val settings = LocalAppSettings.current
    ListItem(
        headlineContent = {
            Text(
                entry.title,
                textDecoration = if (entry.done) TextDecoration.LineThrough else null,
                color = if (entry.projected) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            )
        },
        supportingContent = {
            val kind = when (entry.kind) { EntryKind.TASK -> "Tarefa"; EntryKind.CHECKLIST -> "Checklist"; EntryKind.ROUTINE -> "Rotina" }
            val status = when {
                entry.projected -> "prevista"
                entry.status == OccurrenceStatus.MISSED -> "perdida"
                entry.status == OccurrenceStatus.PARTIAL -> "parcial"
                entry.status == OccurrenceStatus.SKIPPED -> "pulada"
                entry.done -> "concluída"
                else -> null
            }
            Text(listOfNotNull(entry.time?.let { Formats.time(it, settings.timeFormat) }, kind, status).joinToString(" · "))
        },
        leadingContent = {
            Icon(
                when {
                    entry.done -> Icons.Outlined.CheckCircle
                    entry.kind == EntryKind.TASK -> Icons.Outlined.RadioButtonUnchecked
                    entry.kind == EntryKind.ROUTINE -> Icons.Outlined.Repeat
                    else -> Icons.Outlined.Checklist
                },
                null, tint = entry.color,
            )
        },
        modifier = Modifier.clickable(onClick = onClick),
    )
}
