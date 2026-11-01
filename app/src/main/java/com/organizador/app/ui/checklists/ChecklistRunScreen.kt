package com.organizador.app.ui.checklists

import androidx.compose.material3.TextButton
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.organizador.app.core.sync.SyncCoordinator
import com.organizador.app.ui.LocalAppActions
import com.organizador.app.ui.common.CardSurface
import com.organizador.app.ui.common.EmptyState
import com.organizador.app.ui.common.Formats
import com.organizador.app.ui.navigation.Routes
import com.organizador.app.ui.theme.LocalAppSettings
import com.organizador.domain.model.ChecklistDetails
import com.organizador.domain.model.ChecklistKind
import com.organizador.domain.model.ChecklistOccurrence
import com.organizador.domain.model.ChecklistItemState
import com.organizador.domain.model.OccurrenceDetails
import com.organizador.domain.model.OccurrenceStatus
import com.organizador.domain.model.TimeProvider
import com.organizador.domain.recurrence.RecurrenceEngine
import com.organizador.domain.repository.ChecklistRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ChecklistRunViewModel @Inject constructor(
    private val checklists: ChecklistRepository,
    private val sync: SyncCoordinator,
    private val time: TimeProvider,
) : ViewModel() {
    private val templateId = MutableStateFlow<String?>(null)
    private val occurrenceId = MutableStateFlow<String?>(null)
    val details: StateFlow<ChecklistDetails?> = templateId.filterNotNull().flatMapLatest { checklists.observeDetails(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val current: StateFlow<OccurrenceDetails?> = templateId.filterNotNull().flatMapLatest { checklists.observeCurrentOccurrence(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val occurrence: StateFlow<OccurrenceDetails?> = occurrenceId.filterNotNull().flatMapLatest { checklists.observeOccurrence(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    /** Paginação do histórico: carrega mais execuções sob demanda. */
    val historyLimit = MutableStateFlow(HISTORY_PAGE)
    val history: StateFlow<List<ChecklistOccurrence>> = kotlinx.coroutines.flow.combine(templateId.filterNotNull(), historyLimit) { id, max -> id to max }
        .flatMapLatest { (id, max) -> checklists.observeHistory(id, max) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val today: LocalDate get() = time.today()

    fun openTemplate(id: String) {
        if (templateId.value == id) return
        templateId.value = id
        sync.requestSync()
    }

    fun openOccurrence(id: String) { occurrenceId.value = id }

    fun loadMoreHistory() {
        historyLimit.value += HISTORY_PAGE
    }

    fun toggle(state: ChecklistItemState) = viewModelScope.launch { checklists.setItemChecked(state.id, !state.checked, time.now()) }

    fun checkAll(states: List<ChecklistItemState>, checked: Boolean) = viewModelScope.launch {
        states.filter { it.checked != checked }.forEach { checklists.setItemChecked(it.id, checked, time.now()) }
    }

    fun skip(occurrence: ChecklistOccurrence) = viewModelScope.launch { checklists.setOccurrenceStatus(occurrence.id, OccurrenceStatus.SKIPPED, time.now()) }
}

@Composable
fun StatusChip(status: OccurrenceStatus) {
    val (label, color) = when (status) {
        OccurrenceStatus.OPEN -> "Pendente" to MaterialTheme.colorScheme.primary
        OccurrenceStatus.COMPLETED -> "Concluída" to Color(0xFF2E7D32)
        OccurrenceStatus.PARTIAL -> "Parcial" to Color(0xFFEB8909)
        OccurrenceStatus.MISSED -> "Perdida" to MaterialTheme.colorScheme.error
        OccurrenceStatus.SKIPPED -> "Pulada" to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Text(label, color = color, style = MaterialTheme.typography.labelMedium)
}

@Composable
private fun StatesList(
    states: List<ChecklistItemState>,
    enabled: Boolean,
    onToggle: (ChecklistItemState) -> Unit,
    header: @Composable () -> Unit,
) {
    LazyColumn(Modifier.fillMaxSize()) {
        item { header() }
        items(states, key = { it.id }) { state ->
            ListItem(
                headlineContent = {
                    Text(
                        state.title,
                        textDecoration = if (state.checked) TextDecoration.LineThrough else null,
                        color = if (state.checked) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    )
                },
                leadingContent = { Checkbox(checked = state.checked, onCheckedChange = if (enabled) ({ onToggle(state) }) else null, enabled = enabled) },
                modifier = (if (enabled) Modifier.clickable { onToggle(state) } else Modifier).animateItem(),
            )
        }
        item { Spacer(Modifier.height(80.dp)) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChecklistRunScreen(templateId: String) {
    val vm: ChecklistRunViewModel = hiltViewModel(key = "run-$templateId")
    LaunchedEffect(templateId) { vm.openTemplate(templateId) }
    val actions = LocalAppActions.current
    val settings = LocalAppSettings.current
    val details by vm.details.collectAsStateWithLifecycle()
    val current by vm.current.collectAsStateWithLifecycle()
    var menu by remember { mutableStateOf(false) }
    val d = details

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(d?.template?.title ?: "Checklist", maxLines = 1) },
            navigationIcon = { IconButton(onClick = { actions.back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar") } },
            actions = {
                if (d?.template?.kind == ChecklistKind.RECURRING) {
                    IconButton(onClick = { actions.navigate(Routes.checklistHistory(templateId)) }) { Icon(Icons.Outlined.History, "Histórico") }
                }
                IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "Mais") }
                DropdownMenu(menu, { menu = false }) {
                    DropdownMenuItem(text = { Text("Editar") }, onClick = { menu = false; actions.navigate(Routes.checklistEdit(templateId)) })
                    current?.let { c ->
                        DropdownMenuItem(text = { Text("Marcar todos") }, onClick = { menu = false; vm.checkAll(c.states, true) })
                        DropdownMenuItem(text = { Text("Desmarcar todos") }, onClick = { menu = false; vm.checkAll(c.states, false) })
                        if (d?.template?.kind == ChecklistKind.RECURRING) {
                            DropdownMenuItem(text = { Text("Pular esta execução") }, onClick = { menu = false; vm.skip(c.occurrence) })
                        }
                    }
                }
            },
        )
        if (d == null) return@Column
        val c = current
        if (c == null) {
            val next = d.rule?.let { RecurrenceEngine.nextAfter(it, vm.today.minusDays(1)) }
            EmptyState(
                Icons.Outlined.History,
                if (d.template.kind == ChecklistKind.RECURRING) "Nenhuma execução aberta" else "Checklist sem execução",
                when {
                    d.rule?.enabled == false -> "A recorrência está pausada. Reative-a no editor."
                    next != null -> "Próxima execução: ${Formats.longDate(next)}. As execuções anteriores estão no histórico."
                    else -> "Não há próximas execuções para esta regra."
                },
            )
            return@Column
        }
        StatesList(c.states, enabled = c.occurrence.isOpen, onToggle = { vm.toggle(it) }) {
            CardSurface(Modifier.padding(16.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (d.template.description.isNotBlank()) Text(d.template.description, style = MaterialTheme.typography.bodyMedium)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (d.template.kind == ChecklistKind.RECURRING) "Execução de ${Formats.longDate(c.occurrence.date)}" else kindLabelFor(d.template.kind),
                            style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f),
                        )
                        StatusChip(c.occurrence.status)
                    }
                    d.rule?.let { Text(Formats.recurrence(it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    c.occurrence.dueAt?.let {
                        Text("Horário: " + Formats.time(it.atZone(ZoneId.systemDefault()).toLocalTime(), settings.timeFormat), style = MaterialTheme.typography.bodySmall)
                    }
                    val progress by animateFloatAsState(c.occurrence.progress, label = "progresso")
                    LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().padding(top = 4.dp), color = Color(d.template.color))
                    Text("${c.occurrence.checkedItems} de ${c.occurrence.totalItems} concluídos", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

private fun kindLabelFor(kind: ChecklistKind) = when (kind) {
    ChecklistKind.SIMPLE -> "Checklist simples"
    ChecklistKind.PERMANENT -> "Lista permanente"
    ChecklistKind.TEMPLATE -> "Modelo"
    ChecklistKind.RECURRING -> "Recorrente"
}

/** Execução do histórico: somente leitura, com edição explícita opcional. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OccurrenceScreen(occurrenceId: String) {
    val vm: ChecklistRunViewModel = hiltViewModel(key = "occ-$occurrenceId")
    LaunchedEffect(occurrenceId) { vm.openOccurrence(occurrenceId) }
    val actions = LocalAppActions.current
    val details by vm.occurrence.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf(false) }
    val d = details
    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(d?.template?.title ?: "Execução") },
            navigationIcon = { IconButton(onClick = { actions.back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar") } },
        )
        if (d == null) return@Column
        val editable = d.occurrence.isOpen || editing
        StatesList(d.states, enabled = editable, onToggle = { vm.toggle(it) }) {
            CardSurface(Modifier.padding(16.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(Formats.longDate(d.occurrence.date), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        StatusChip(d.occurrence.status)
                    }
                    Text("${d.occurrence.checkedItems} de ${d.occurrence.totalItems} concluídos")
                    if (d.states.isEmpty() && d.occurrence.status == OccurrenceStatus.MISSED) {
                        Text("Execução perdida: nenhum item foi registrado nesta data.", style = MaterialTheme.typography.bodySmall)
                    }
                    if (!d.occurrence.isOpen && d.states.isNotEmpty()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Editar esta execução do histórico", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                            Switch(checked = editing, onCheckedChange = { editing = it })
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChecklistHistoryScreen(templateId: String) {
    val vm: ChecklistRunViewModel = hiltViewModel(key = "history-$templateId")
    LaunchedEffect(templateId) { vm.openTemplate(templateId) }
    val actions = LocalAppActions.current
    val details by vm.details.collectAsStateWithLifecycle()
    val history by vm.history.collectAsStateWithLifecycle()
    val historyLimit by vm.historyLimit.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Histórico") },
            navigationIcon = { IconButton(onClick = { actions.back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar") } },
        )
        if (history.isEmpty()) {
            EmptyState(Icons.Outlined.History, "Sem execuções registradas", "As execuções aparecem aqui conforme as datas da recorrência chegam.")
            return@Column
        }
        val closed = history.filter { !it.isOpen }
        val completed = closed.count { it.status == OccurrenceStatus.COMPLETED }
        LazyColumn {
            item {
                CardSurface(Modifier.padding(16.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        Text(details?.template?.title.orEmpty(), style = MaterialTheme.typography.titleMedium)
                        Text("${history.size} execuções registradas · ${closed.size} encerradas")
                        if (closed.isNotEmpty()) Text("Taxa de conclusão: ${completed * 100 / closed.size}%", color = MaterialTheme.colorScheme.primary)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                            AssistChip({}, { Text("Concluídas $completed") })
                            AssistChip({}, { Text("Parciais ${closed.count { it.status == OccurrenceStatus.PARTIAL }}") })
                            AssistChip({}, { Text("Perdidas ${closed.count { it.status == OccurrenceStatus.MISSED }}") })
                        }
                    }
                }
            }
            items(history, key = { it.id }) { o ->
                ListItem(
                    headlineContent = { Text(Formats.longDate(o.date)) },
                    supportingContent = { Text("${o.checkedItems}/${o.totalItems} itens") },
                    trailingContent = { StatusChip(o.status) },
                    modifier = Modifier.clickable { actions.navigate(Routes.occurrence(o.id)) }.animateItem(),
                )
            }
            if (history.size >= historyLimit) {
                item(key = "load-more") {
                    TextButton(onClick = { vm.loadMoreHistory() }, modifier = Modifier.fillMaxWidth().padding(8.dp)) { Text("Carregar mais") }
                }
            }
        }
    }
}

private const val HISTORY_PAGE = 100
