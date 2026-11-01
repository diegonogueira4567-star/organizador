package com.organizador.app.ui.tasks

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.DriveFileMove
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material.icons.outlined.Unarchive
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.organizador.app.ui.LocalAppActions
import com.organizador.app.ui.common.ColorDot
import com.organizador.app.ui.common.ConfirmDialog
import com.organizador.app.ui.common.EmptyState
import com.organizador.app.ui.common.Formats
import com.organizador.app.ui.common.SectionHeader
import com.organizador.app.ui.common.priorityColor
import com.organizador.app.ui.common.priorityLabel
import com.organizador.app.ui.navigation.Routes
import com.organizador.app.ui.theme.LocalAppSettings
import com.organizador.app.ui.theme.LocalSpacing
import com.organizador.domain.model.Category
import com.organizador.domain.model.Priority
import com.organizador.domain.model.Project
import com.organizador.domain.model.Tag
import com.organizador.domain.model.TaskFilter
import com.organizador.domain.model.TaskListItem
import com.organizador.domain.model.TaskQuery
import com.organizador.domain.model.TaskSort
import com.organizador.domain.model.TaskView
import com.organizador.domain.model.TimeProvider
import com.organizador.domain.repository.LabelRepository
import com.organizador.domain.repository.ProjectRepository
import com.organizador.domain.repository.SettingsRepository
import com.organizador.domain.repository.TaskRepository
import com.organizador.domain.usecase.CompletionResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TaskListViewModel @Inject constructor(
    private val tasks: TaskRepository,
    projects: ProjectRepository,
    labels: LabelRepository,
    private val settings: SettingsRepository,
    private val actions: TaskActions,
    time: TimeProvider,
) : ViewModel() {
    private val view = MutableStateFlow<TaskView?>(null)
    val filter = MutableStateFlow(TaskFilter())
    val sort = MutableStateFlow<TaskSort?>(null)
    /** Quantidade carregada nas visões históricas (concluídas, arquivadas, lixeira). */
    val limit = MutableStateFlow(TaskQuery.PAGE_SIZE)

    val today: StateFlow<LocalDate> = time.todayFlow().stateIn(viewModelScope, SharingStarted.Eagerly, time.today())
    val projects: StateFlow<List<Project>> = projects.observeProjects().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val categories: StateFlow<List<Category>> = labels.observeCategories().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val tags: StateFlow<List<Tag>> = labels.observeTags().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val items: StateFlow<List<TaskListItem>?> = combine(view.filterNotNull(), filter, sort, settings.settings, limit) { v, f, s, prefs, max ->
        TaskQuery(v, f, s ?: prefs.defaultTaskSort).let { q -> if (q.isHistory) q.copy(limit = max) else q }
    }.flatMapLatest { tasks.observeTasks(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun setView(v: TaskView) {
        if (view.value != v) {
            view.value = v
            limit.value = TaskQuery.PAGE_SIZE
        }
    }

    fun loadMore() {
        limit.value += TaskQuery.PAGE_SIZE
    }

    fun setSort(s: TaskSort) {
        sort.value = s
        viewModelScope.launch { settings.update { it.copy(defaultTaskSort = s) } }
    }

    fun toggle(item: TaskListItem, onCompleted: (CompletionResult) -> Unit) = viewModelScope.launch {
        actions.toggle(item.task.id, item.task.isCompleted)?.let(onCompleted)
    }

    fun undo(result: CompletionResult) = viewModelScope.launch { actions.undo(result) }
    fun delete(id: String) = viewModelScope.launch { actions.delete(id) }
    fun restore(id: String) = viewModelScope.launch { actions.restore(id) }
    fun deleteForever(id: String) = viewModelScope.launch { actions.deleteForever(id) }
    fun duplicate(id: String) = viewModelScope.launch { actions.duplicate(id) }
    fun archive(id: String, archived: Boolean) = viewModelScope.launch { actions.archive(id, archived) }
    fun move(id: String, projectId: String?) = viewModelScope.launch { actions.move(id, projectId) }
    fun emptyTrash() = viewModelScope.launch { actions.emptyTrash() }
    suspend fun confirmDeletion(): Boolean = settings.settings.first().confirmDeletion
}

private fun TaskView.key(): String = toString()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskListScreen(viewKey: String, viewId: String?) {
    val view = remember(viewKey, viewId) { Routes.taskView(viewKey, viewId) }
    val vm: TaskListViewModel = hiltViewModel(key = "tasks-${view.key()}")
    LaunchedEffect(view) { vm.setView(view) }
    val actions = LocalAppActions.current
    val projects by vm.projects.collectAsStateWithLifecycle()
    val categories by vm.categories.collectAsStateWithLifecycle()
    val tags by vm.tags.collectAsStateWithLifecycle()
    val filter by vm.filter.collectAsStateWithLifecycle()
    val sort by vm.sort.collectAsStateWithLifecycle()
    val today by vm.today.collectAsStateWithLifecycle()
    val settings = LocalAppSettings.current
    var searching by remember { mutableStateOf(false) }
    var sortMenu by remember { mutableStateOf(false) }
    var filterMenu by remember { mutableStateOf(false) }
    var moreMenu by remember { mutableStateOf(false) }
    var confirmEmpty by remember { mutableStateOf(false) }

    val title = when (view) {
        TaskView.Inbox -> "Caixa de entrada"
        TaskView.Today -> "Hoje"
        is TaskView.Upcoming -> "Próximos dias"
        TaskView.Overdue -> "Atrasadas"
        TaskView.Completed -> "Concluídas"
        TaskView.Archived -> "Arquivadas"
        TaskView.Trash -> "Lixeira"
        TaskView.All -> "Todas as tarefas"
        is TaskView.ByProject -> projects.firstOrNull { it.id == view.projectId }?.name ?: "Projeto"
        is TaskView.ByCategory -> categories.firstOrNull { it.id == view.categoryId }?.name ?: "Categoria"
        is TaskView.ByTag -> "#" + (tags.firstOrNull { it.id == view.tagId }?.name ?: "etiqueta")
        is TaskView.ByPriority -> priorityLabel(view.priority)
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                if (searching) {
                    TextField(
                        value = filter.text,
                        onValueChange = { vm.filter.value = filter.copy(text = it) },
                        placeholder = { Text("Filtrar tarefas") },
                        singleLine = true,
                        colors = TextFieldDefaults.colors(focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent),
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    Column {
                        Text(title, maxLines = 1)
                        if (view == TaskView.Today) Text(Formats.longDate(today), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            },
            navigationIcon = {
                IconButton(onClick = actions.openDrawer) { Icon(Icons.Default.Menu, "Menu") }
            },
            actions = {
                IconButton(onClick = {
                    if (searching) vm.filter.value = filter.copy(text = "")
                    searching = !searching
                }) { Icon(if (searching) Icons.Default.Close else Icons.Default.Search, "Filtrar") }
                IconButton(onClick = { filterMenu = true }) {
                    Icon(Icons.Default.FilterList, "Filtros", tint = if (filter.priorities.isNotEmpty() || filter.tagIds.isNotEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                }
                DropdownMenu(filterMenu, { filterMenu = false }) {
                    Priority.entries.forEach { p ->
                        DropdownMenuItem(
                            text = { Text(priorityLabel(p)) },
                            leadingIcon = { Checkbox(checked = p in filter.priorities, onCheckedChange = null) },
                            trailingIcon = { Icon(Icons.Default.Flag, null, tint = priorityColor(p)) },
                            onClick = { vm.filter.value = filter.copy(priorities = if (p in filter.priorities) filter.priorities - p else filter.priorities + p) },
                        )
                    }
                    tags.forEach { tag ->
                        DropdownMenuItem(
                            text = { Text("#${tag.name}") },
                            leadingIcon = { Checkbox(checked = tag.id in filter.tagIds, onCheckedChange = null) },
                            onClick = { vm.filter.value = filter.copy(tagIds = if (tag.id in filter.tagIds) filter.tagIds - tag.id else filter.tagIds + tag.id) },
                        )
                    }
                    if (view != TaskView.Completed && view != TaskView.Trash) {
                        DropdownMenuItem(
                            text = { Text("Mostrar concluídas") },
                            leadingIcon = { Checkbox(checked = filter.includeCompleted, onCheckedChange = null) },
                            onClick = { vm.filter.value = filter.copy(includeCompleted = !filter.includeCompleted) },
                        )
                    }
                    DropdownMenuItem(text = { Text("Limpar filtros") }, onClick = { vm.filter.value = TaskFilter(); filterMenu = false })
                }
                IconButton(onClick = { sortMenu = true }) { Icon(Icons.AutoMirrored.Filled.Sort, "Ordenar") }
                DropdownMenu(sortMenu, { sortMenu = false }) {
                    listOf(
                        TaskSort.DUE_DATE to "Prazo", TaskSort.PRIORITY to "Prioridade", TaskSort.MANUAL to "Manual",
                        TaskSort.CREATED to "Criação", TaskSort.UPDATED to "Modificação", TaskSort.TITLE to "Título",
                    ).forEach { (s, label) ->
                        DropdownMenuItem(
                            text = { Text(label) },
                            leadingIcon = { androidx.compose.material3.RadioButton(selected = (sort ?: settings.defaultTaskSort) == s, onClick = null) },
                            onClick = { vm.setSort(s); sortMenu = false },
                        )
                    }
                }
                IconButton(onClick = { moreMenu = true }) { Icon(Icons.Default.MoreVert, "Mais") }
                DropdownMenu(moreMenu, { moreMenu = false }) {
                    DropdownMenuItem(text = { Text("Concluídas") }, leadingIcon = { Icon(Icons.Outlined.TaskAlt, null) }, onClick = { moreMenu = false; actions.navigate(Routes.tasks("completed")) })
                    DropdownMenuItem(text = { Text("Arquivadas") }, leadingIcon = { Icon(Icons.Outlined.Archive, null) }, onClick = { moreMenu = false; actions.navigate(Routes.tasks("archived")) })
                    DropdownMenuItem(text = { Text("Lixeira") }, leadingIcon = { Icon(Icons.Outlined.Delete, null) }, onClick = { moreMenu = false; actions.navigate(Routes.tasks("trash")) })
                    if (view == TaskView.Trash) {
                        DropdownMenuItem(text = { Text("Esvaziar lixeira") }, leadingIcon = { Icon(Icons.Outlined.DeleteForever, null) }, onClick = { moreMenu = false; confirmEmpty = true })
                    }
                }
            },
        )
        TaskListBody(view = view, vm = vm)
    }

    if (confirmEmpty) {
        ConfirmDialog(
            title = "Esvaziar lixeira?",
            text = "As tarefas da lixeira serão excluídas permanentemente, junto com subtarefas e anexos. Esta ação não pode ser desfeita.",
            confirmLabel = "Excluir tudo", destructive = true,
            onConfirm = { vm.emptyTrash() }, onDismiss = { confirmEmpty = false },
        )
    }
}

/** Corpo da lista reutilizável (também usado na tela de projeto). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskListBody(view: TaskView, vm: TaskListViewModel = hiltViewModel(key = "body-${view.key()}")) {
    LaunchedEffect(view) { vm.setView(view) }
    val actions = LocalAppActions.current
    val items by vm.items.collectAsStateWithLifecycle()
    val today by vm.today.collectAsStateWithLifecycle()
    val projects by vm.projects.collectAsStateWithLifecycle()
    val limit by vm.limit.collectAsStateWithLifecycle()
    val spacing = LocalSpacing.current
    var menuFor by remember { mutableStateOf<TaskListItem?>(null) }
    var moveFor by remember { mutableStateOf<TaskListItem?>(null) }
    var confirmDelete by remember { mutableStateOf<TaskListItem?>(null) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    fun toggle(item: TaskListItem) = vm.toggle(item) { result ->
        actions.showMessage(
            if (result.nextDueDate != null) "Concluída. Próxima: ${Formats.relativeDate(result.nextDueDate!!, today)}" else "Tarefa concluída",
            "Desfazer",
        ) { vm.undo(result) }
    }

    fun delete(item: TaskListItem) {
        vm.delete(item.task.id)
        actions.showMessage("Movida para a lixeira", "Desfazer") { vm.restore(item.task.id) }
    }

    val list = items
    when {
        list == null -> Unit
        list.isEmpty() -> EmptyState(
            Icons.Outlined.Inbox,
            when (view) {
                TaskView.Today -> "Tudo em dia!"
                TaskView.Trash -> "Lixeira vazia"
                TaskView.Completed -> "Nenhuma tarefa concluída"
                else -> "Nenhuma tarefa aqui"
            },
            when (view) {
                TaskView.Trash -> "Tarefas excluídas aparecem aqui e podem ser recuperadas."
                TaskView.Inbox -> "Use o botão + para capturar ideias e organizá-las depois."
                else -> "Toque em + para adicionar uma tarefa."
            },
        )
        else -> LazyColumn(Modifier.fillMaxSize()) {
            val groups: List<Pair<String?, List<TaskListItem>>> = when (view) {
                TaskView.Today -> listOf(
                    "Atrasadas" to list.filter { it.task.dueDate?.isBefore(today) == true && !it.task.isCompleted },
                    "Hoje" to list.filter { !(it.task.dueDate?.isBefore(today) == true && !it.task.isCompleted) },
                ).filter { it.second.isNotEmpty() }
                is TaskView.Upcoming -> list.groupBy { it.task.dueDate }.toSortedMap(compareBy { it }).map { (date, l) ->
                    (date?.let { Formats.relativeDate(it, today) + " · " + Formats.shortDate(it, today) } ?: "Sem data") to l
                }
                else -> listOf(null to list)
            }
            groups.forEach { (header, groupItems) ->
                if (header != null) {
                    item(key = "h-$header") { SectionHeader("$header (${groupItems.size})", Modifier.padding(horizontal = spacing.screen).animateItem()) }
                }
                items(groupItems, key = { it.task.id }) { item ->
                    // Entrada, saída e reordenação animadas de forma discreta.
                    Box(Modifier.animateItem()) {
                        TaskRow(
                            item = item,
                            today = today,
                            onToggle = { if (view != TaskView.Trash) toggle(item) },
                            onClick = { actions.navigate(Routes.task(item.task.id)) },
                            onLongClick = { menuFor = item },
                            showProject = view !is TaskView.ByProject,
                            showDate = view !is TaskView.Upcoming,
                        )
                    }
                }
            }
            // Paginação: o banco devolveu uma página cheia, então pode haver mais registros.
            if (TaskQuery(view).isHistory && list.size >= limit) {
                item(key = "load-more") {
                    TextButton(onClick = { vm.loadMore() }, modifier = Modifier.fillMaxWidth().padding(8.dp)) { Text("Carregar mais") }
                }
            }
            item { androidx.compose.foundation.layout.Spacer(Modifier.padding(40.dp)) }
        }
    }

    menuFor?.let { item ->
        ModalBottomSheet(onDismissRequest = { menuFor = null }) {
            Text(item.task.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp), maxLines = 2)
            if (item.task.isDeleted) {
                SheetAction(Icons.Outlined.Restore, "Restaurar") { vm.restore(item.task.id); menuFor = null }
                SheetAction(Icons.Outlined.DeleteForever, "Excluir permanentemente") { confirmDelete = item; menuFor = null }
            } else {
                SheetAction(Icons.Outlined.Edit, "Editar") { menuFor = null; actions.navigate(Routes.task(item.task.id)) }
                SheetAction(Icons.Outlined.TaskAlt, if (item.task.isCompleted) "Reabrir" else "Concluir") { toggle(item); menuFor = null }
                SheetAction(Icons.Outlined.ContentCopy, "Duplicar") { vm.duplicate(item.task.id); menuFor = null; actions.showMessage("Tarefa duplicada", null, null) }
                SheetAction(Icons.Outlined.DriveFileMove, "Mover para projeto") { moveFor = item; menuFor = null }
                if (item.task.archived) {
                    SheetAction(Icons.Outlined.Unarchive, "Desarquivar") { vm.archive(item.task.id, false); menuFor = null }
                } else {
                    SheetAction(Icons.Outlined.Archive, "Arquivar") { vm.archive(item.task.id, true); menuFor = null }
                }
                SheetAction(Icons.Outlined.Delete, "Excluir") {
                    menuFor = null
                    scope.launch { if (vm.confirmDeletion()) confirmDelete = item else delete(item) }
                }
            }
            androidx.compose.foundation.layout.Spacer(Modifier.padding(16.dp))
        }
    }

    moveFor?.let { item ->
        ModalBottomSheet(onDismissRequest = { moveFor = null }) {
            Text("Mover para", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
            ListItem(
                headlineContent = { Text("Caixa de entrada") },
                leadingContent = { Icon(Icons.Outlined.Inbox, null) },
                modifier = Modifier.clickableRow { vm.move(item.task.id, null); moveFor = null },
            )
            projects.forEach { p ->
                ListItem(
                    headlineContent = { Text(p.name) },
                    leadingContent = { ColorDot(p.color, size = 14) },
                    modifier = Modifier.clickableRow { vm.move(item.task.id, p.id); moveFor = null },
                )
            }
            androidx.compose.foundation.layout.Spacer(Modifier.padding(16.dp))
        }
    }

    confirmDelete?.let { item ->
        val permanent = item.task.isDeleted
        ConfirmDialog(
            title = if (permanent) "Excluir permanentemente?" else "Excluir tarefa?",
            text = if (permanent) "\"${item.task.title}\" será apagada definitivamente, com subtarefas e anexos." else "\"${item.task.title}\" irá para a lixeira e poderá ser recuperada.",
            confirmLabel = "Excluir", destructive = true,
            onConfirm = { if (permanent) vm.deleteForever(item.task.id) else delete(item) },
            onDismiss = { confirmDelete = null },
        )
    }
}

@Composable
fun SheetAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    ListItem(headlineContent = { Text(label) }, leadingContent = { Icon(icon, null) }, modifier = Modifier.clickableRow(onClick))
}

fun Modifier.clickableRow(onClick: () -> Unit): Modifier = fillMaxWidth().clickable(onClick = onClick)
