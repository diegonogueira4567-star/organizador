package com.organizador.app.ui.checklists

import androidx.compose.foundation.layout.Box
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Unarchive
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.organizador.app.core.sync.SyncCoordinator
import com.organizador.app.ui.LocalAppActions
import com.organizador.app.ui.common.CardSurface
import com.organizador.app.ui.common.ConfirmDialog
import com.organizador.app.ui.common.EmptyState
import com.organizador.app.ui.common.Formats
import com.organizador.app.ui.common.SectionHeader
import com.organizador.app.ui.navigation.Routes
import com.organizador.app.ui.tasks.SheetAction
import com.organizador.app.ui.theme.LocalAppSettings
import com.organizador.app.ui.theme.LocalSpacing
import com.organizador.domain.model.ChecklistKind
import com.organizador.domain.model.ChecklistSummary
import com.organizador.domain.model.ChecklistTemplate
import com.organizador.domain.model.OccurrenceStatus
import com.organizador.domain.model.TimeProvider
import com.organizador.domain.repository.ChecklistRepository
import com.organizador.domain.usecase.InstantiateTemplateUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

enum class ChecklistFilter { ACTIVE, ARCHIVED, TRASH }

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ChecklistListViewModel @Inject constructor(
    private val checklists: ChecklistRepository,
    private val instantiate: InstantiateTemplateUseCase,
    private val sync: SyncCoordinator,
    private val time: TimeProvider,
) : ViewModel() {
    private val routines = MutableStateFlow<Boolean?>(null)
    val filter = MutableStateFlow(ChecklistFilter.ACTIVE)
    val today: StateFlow<LocalDate> = time.todayFlow().stateIn(viewModelScope, SharingStarted.Eagerly, time.today())

    val summaries: StateFlow<List<ChecklistSummary>?> = kotlinx.coroutines.flow.combine(routines, filter) { r, f -> r to f }
        .flatMapLatest { (r, f) -> checklists.observeSummaries(routines = r, includeArchived = f == ChecklistFilter.ARCHIVED) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val trash: StateFlow<List<ChecklistTemplate>> = checklists.observeTrash().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setRoutines(value: Boolean) {
        routines.value = value
        sync.requestSync() // garante a execução do dia materializada ao abrir a tela
    }

    fun archive(t: ChecklistTemplate) = viewModelScope.launch { checklists.setArchived(t.id, !t.archived) }
    fun delete(t: ChecklistTemplate) = viewModelScope.launch { checklists.softDelete(t.id, time.now()); sync.requestSync() }
    fun restore(t: ChecklistTemplate) = viewModelScope.launch { checklists.restore(t.id); sync.requestSync() }
    fun deleteForever(t: ChecklistTemplate) = viewModelScope.launch { checklists.deletePermanently(t.id) }
    fun useTemplate(t: ChecklistTemplate, then: (String) -> Unit) = viewModelScope.launch { instantiate(t.id)?.let(then) }
}

fun kindLabel(summary: ChecklistSummary): String = when (summary.template.kind) {
    ChecklistKind.SIMPLE -> "Checklist simples"
    ChecklistKind.PERMANENT -> "Lista permanente"
    ChecklistKind.TEMPLATE -> "Modelo reutilizável"
    ChecklistKind.RECURRING -> summary.rule?.let { Formats.recurrence(it) } ?: "Recorrente"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChecklistListScreen(routines: Boolean) {
    val vm: ChecklistListViewModel = hiltViewModel(key = "checklists-$routines")
    androidx.compose.runtime.LaunchedEffect(routines) { vm.setRoutines(routines) }
    val actions = LocalAppActions.current
    val summaries by vm.summaries.collectAsStateWithLifecycle()
    val trash by vm.trash.collectAsStateWithLifecycle()
    val filter by vm.filter.collectAsStateWithLifecycle()
    val today by vm.today.collectAsStateWithLifecycle()
    var menuFor by remember { mutableStateOf<ChecklistSummary?>(null) }
    var confirmDelete by remember { mutableStateOf<ChecklistTemplate?>(null) }
    var confirmForever by remember { mutableStateOf<ChecklistTemplate?>(null) }
    val spacing = LocalSpacing.current

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(if (routines) "Rotinas" else "Checklists") },
            navigationIcon = { IconButton(onClick = actions.openDrawer) { Icon(Icons.Default.Menu, "Menu") } },
        )
        Row(Modifier.padding(horizontal = spacing.screen), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(filter == ChecklistFilter.ACTIVE, { vm.filter.value = ChecklistFilter.ACTIVE }, { Text("Ativos") })
            FilterChip(filter == ChecklistFilter.ARCHIVED, { vm.filter.value = ChecklistFilter.ARCHIVED }, { Text("Arquivados") })
            FilterChip(filter == ChecklistFilter.TRASH, { vm.filter.value = ChecklistFilter.TRASH }, { Text("Lixeira") })
        }

        if (filter == ChecklistFilter.TRASH) {
            val list = trash.filter { it.isRoutine == routines }
            if (list.isEmpty()) {
                EmptyState(Icons.Outlined.Delete, "Lixeira vazia", "Checklists excluídos aparecem aqui com todo o histórico preservado.")
            } else {
                LazyColumn(Modifier.fillMaxSize().padding(horizontal = spacing.screen), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(list, key = { it.id }) { t ->
                        CardSurface {
                            Row(Modifier.padding(spacing.card), verticalAlignment = Alignment.CenterVertically) {
                                Text(t.title, modifier = Modifier.weight(1f))
                                IconButton(onClick = { vm.restore(t) }) { Icon(Icons.Outlined.Restore, "Restaurar") }
                                IconButton(onClick = { confirmForever = t }) { Icon(Icons.Outlined.DeleteForever, "Excluir permanentemente") }
                            }
                        }
                    }
                }
            }
        } else {
            val list = summaries?.filter { filter == ChecklistFilter.ACTIVE || it.template.archived } ?: return@Column
            if (list.isEmpty()) {
                EmptyState(
                    if (routines) Icons.Outlined.Repeat else Icons.Outlined.Checklist,
                    if (routines) "Nenhuma rotina" else "Nenhum checklist",
                    if (routines) "Crie rotinas recorrentes (diárias, semanais, de sábado...) com o botão +." else "Crie checklists simples, recorrentes, permanentes ou modelos com o botão +.",
                )
            } else {
                val groups = if (routines) {
                    listOf(
                        "Em andamento" to list.filter { it.current != null && it.current!!.status == OccurrenceStatus.OPEN },
                        "Concluídas" to list.filter { it.current?.status == OccurrenceStatus.COMPLETED },
                        "Próximas" to list.filter { it.current == null },
                    )
                } else {
                    listOf(
                        "Recorrentes" to list.filter { it.template.kind == ChecklistKind.RECURRING },
                        "Listas" to list.filter { it.template.kind == ChecklistKind.SIMPLE || it.template.kind == ChecklistKind.PERMANENT },
                        "Modelos" to list.filter { it.template.kind == ChecklistKind.TEMPLATE },
                    )
                }.filter { it.second.isNotEmpty() }
                LazyColumn(Modifier.fillMaxSize().padding(horizontal = spacing.screen), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    groups.forEach { (header, items) ->
                        item(key = "h-$header") { SectionHeader(header, Modifier.animateItem()) }
                        items(items, key = { it.template.id }) { s ->
                            Box(Modifier.animateItem()) {
                                ChecklistCard(s, today, onClick = {
                                    if (s.template.kind == ChecklistKind.TEMPLATE) actions.navigate(Routes.checklistEdit(s.template.id))
                                    else actions.navigate(Routes.checklistRun(s.template.id))
                                }, onLongClick = { menuFor = s })
                            }
                        }
                    }
                    item { Spacer(Modifier.height(80.dp)) }
                }
            }
        }
    }

    menuFor?.let { s ->
        val t = s.template
        ModalBottomSheet(onDismissRequest = { menuFor = null }) {
            Text(t.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
            SheetAction(Icons.Outlined.Edit, "Editar") { menuFor = null; actions.navigate(Routes.checklistEdit(t.id)) }
            if (t.kind == ChecklistKind.RECURRING) SheetAction(Icons.Outlined.History, "Histórico de execuções") { menuFor = null; actions.navigate(Routes.checklistHistory(t.id)) }
            SheetAction(Icons.Outlined.ContentCopy, if (t.kind == ChecklistKind.TEMPLATE) "Usar modelo" else "Criar cópia simples") {
                menuFor = null
                vm.useTemplate(t) { id -> actions.navigate(Routes.checklistRun(id)) }
            }
            SheetAction(if (t.archived) Icons.Outlined.Unarchive else Icons.Outlined.Archive, if (t.archived) "Desarquivar" else "Arquivar") { menuFor = null; vm.archive(t) }
            SheetAction(Icons.Outlined.Delete, "Excluir") { menuFor = null; confirmDelete = t }
            Spacer(Modifier.height(24.dp))
        }
    }
    confirmDelete?.let { t ->
        ConfirmDialog(
            "Excluir checklist?", "\"${t.title}\" irá para a lixeira. O histórico de execuções é mantido e pode ser recuperado.",
            "Excluir", destructive = true,
            onConfirm = {
                vm.delete(t)
                actions.showMessage("Checklist movido para a lixeira", "Desfazer") { vm.restore(t) }
            },
            onDismiss = { confirmDelete = null },
        )
    }
    confirmForever?.let { t ->
        ConfirmDialog(
            "Excluir permanentemente?", "\"${t.title}\" e TODO o histórico de execuções serão apagados definitivamente.",
            "Excluir", destructive = true, onConfirm = { vm.deleteForever(t) }, onDismiss = { confirmForever = null },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChecklistCard(s: ChecklistSummary, today: LocalDate, onClick: () -> Unit, onLongClick: () -> Unit) {
    val spacing = LocalSpacing.current
    val settings = LocalAppSettings.current
    val current = s.current
    CardSurface(Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick)) {
        Column(Modifier.padding(spacing.card)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (current?.status == OccurrenceStatus.COMPLETED) Icons.Outlined.CheckCircle else if (s.template.isRoutine) Icons.Outlined.Repeat else Icons.Outlined.Checklist,
                    null, tint = Color(s.template.color),
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(s.template.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(kindLabel(s), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
                if (current != null) Text("${current.checkedItems}/${current.totalItems}", style = MaterialTheme.typography.labelLarge)
            }
            if (current != null && current.totalItems > 0) {
                Spacer(Modifier.height(8.dp))
                val progress by animateFloatAsState(current.progress, label = "progresso")
                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth(), color = Color(s.template.color))
            }
            val info = when {
                current != null && s.template.kind == ChecklistKind.RECURRING ->
                    "Execução de ${Formats.relativeDate(current.date, today)}" + (current.dueAt?.let { " · " + Formats.time(it.atZone(java.time.ZoneId.systemDefault()).toLocalTime(), settings.timeFormat) } ?: "")
                current == null && s.nextDate != null -> "Próxima: ${Formats.relativeDate(s.nextDate!!, today)}"
                current == null && s.template.kind == ChecklistKind.RECURRING -> "Sem próximas execuções"
                else -> null
            }
            info?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp)) }
        }
    }
}
