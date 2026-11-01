package com.organizador.app.ui.projects

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.EventNote
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.organizador.app.ui.common.ColorPickerRow
import com.organizador.app.ui.common.ConfirmDialog
import com.organizador.app.ui.common.EmptyState
import com.organizador.app.ui.common.SectionHeader
import com.organizador.app.ui.common.projectIcon
import com.organizador.app.ui.common.projectIcons
import com.organizador.app.ui.navigation.Routes
import com.organizador.app.ui.tasks.TaskListBody
import com.organizador.domain.model.Category
import com.organizador.domain.model.ChecklistSummary
import com.organizador.domain.model.Ids
import com.organizador.domain.model.NoteSort
import com.organizador.domain.model.NoteSummary
import com.organizador.domain.model.NoteView
import com.organizador.domain.model.Palette
import com.organizador.domain.model.Project
import com.organizador.domain.model.Tag
import com.organizador.domain.model.TaskView
import com.organizador.domain.model.TimeProvider
import com.organizador.domain.repository.ChecklistRepository
import com.organizador.domain.repository.LabelRepository
import com.organizador.domain.repository.NoteRepository
import com.organizador.domain.repository.ProjectRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ProjectsViewModel @Inject constructor(
    private val projects: ProjectRepository,
    private val notes: NoteRepository,
    private val checklists: ChecklistRepository,
    private val time: TimeProvider,
) : ViewModel() {
    val all: StateFlow<List<Project>?> = projects.observeProjects(includeArchived = true).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    private val selected = MutableStateFlow<String?>(null)
    val project: StateFlow<Project?> = selected.filterNotNull().flatMapLatest { projects.observeProject(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val projectNotes: StateFlow<List<NoteSummary>> = selected.filterNotNull().flatMapLatest { notes.observeNotes(NoteView.ByProject(it), NoteSort.UPDATED) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val projectChecklists: StateFlow<List<ChecklistSummary>> = selected.filterNotNull().flatMapLatest { id ->
        checklists.observeSummaries(routines = null).map { list -> list.filter { it.template.projectId == id } }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun select(id: String) { selected.value = id }

    fun save(id: String?, name: String, description: String, color: Long, icon: String) = viewModelScope.launch {
        val now = time.now()
        val existing = id?.let { projects.getProject(it) }
        projects.save(
            existing?.copy(name = name, description = description, color = color, icon = icon, updatedAt = now)
                ?: Project(Ids.new(), name, description, color, icon, createdAt = now, updatedAt = now),
        )
    }

    fun archive(p: Project) = viewModelScope.launch { projects.setArchived(p.id, !p.archived) }
    fun delete(p: Project) = viewModelScope.launch { projects.delete(p.id) }
    fun move(list: List<Project>, index: Int, delta: Int) = viewModelScope.launch {
        val target = index + delta
        if (target !in list.indices) return@launch
        val reordered = list.toMutableList().apply { add(target, removeAt(index)) }
        projects.reorder(reordered.map { it.id })
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProjectDialog(initial: Project?, onSave: (String, String, Long, String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var description by remember { mutableStateOf(initial?.description.orEmpty()) }
    var color by remember { mutableStateOf(initial?.color ?: Palette.default) }
    var icon by remember { mutableStateOf(initial?.icon ?: "folder") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Novo projeto" else "Editar projeto") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Nome") }, singleLine = true)
                OutlinedTextField(description, { description = it }, label = { Text("Descrição") })
                Text("Cor", style = MaterialTheme.typography.labelLarge)
                ColorPickerRow(color, { color = it })
                Text("Ícone", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    projectIcons.forEach { (key, vector) ->
                        FilterChip(selected = key == icon, onClick = { icon = key }, label = { Icon(vector, key, tint = Color(color)) })
                    }
                }
            }
        },
        confirmButton = { TextButton(enabled = name.isNotBlank(), onClick = { onSave(name.trim(), description.trim(), color, icon); onDismiss() }) { Text("Salvar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectsScreen(startCreating: Boolean) {
    val vm: ProjectsViewModel = hiltViewModel()
    val actions = LocalAppActions.current
    val all by vm.all.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<Project?>(null) }
    var creating by remember { mutableStateOf(startCreating) }
    var deleting by remember { mutableStateOf<Project?>(null) }
    var menuFor by remember { mutableStateOf<Project?>(null) }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Projetos") },
            navigationIcon = { IconButton(onClick = actions.openDrawer) { Icon(Icons.Default.Menu, "Menu") } },
            actions = { IconButton(onClick = { creating = true }) { Icon(Icons.Default.Add, "Novo projeto") } },
        )
        val list = all ?: return@Column
        if (list.isEmpty()) {
            EmptyState(Icons.Outlined.Folder, "Nenhum projeto", "Projetos agrupam tarefas, notas e checklists relacionados.")
            return@Column
        }
        val active = list.filter { !it.archived }
        val archived = list.filter { it.archived }
        LazyColumn {
            items(active, key = { it.id }) { p ->
                val index = active.indexOf(p)
                ListItem(
                    headlineContent = { Text(p.name) },
                    supportingContent = { if (p.description.isNotBlank()) Text(p.description, maxLines = 1) },
                    leadingContent = { Icon(projectIcon(p.icon), null, tint = Color(p.color)) },
                    trailingContent = {
                        Column {
                            IconButton(onClick = { menuFor = p }) { Icon(Icons.Default.MoreVert, "Opções") }
                            DropdownMenu(menuFor == p, { menuFor = null }) {
                                DropdownMenuItem(text = { Text("Editar") }, onClick = { menuFor = null; editing = p })
                                DropdownMenuItem(text = { Text("Mover para cima") }, leadingIcon = { Icon(Icons.Outlined.KeyboardArrowUp, null) }, onClick = { menuFor = null; vm.move(active, index, -1) })
                                DropdownMenuItem(text = { Text("Mover para baixo") }, leadingIcon = { Icon(Icons.Outlined.KeyboardArrowDown, null) }, onClick = { menuFor = null; vm.move(active, index, 1) })
                                DropdownMenuItem(text = { Text("Arquivar") }, onClick = { menuFor = null; vm.archive(p) })
                                DropdownMenuItem(text = { Text("Excluir") }, onClick = { menuFor = null; deleting = p })
                            }
                        }
                    },
                    modifier = Modifier.clickable { actions.navigate(Routes.project(p.id)) },
                )
            }
            if (archived.isNotEmpty()) {
                item { SectionHeader("Arquivados", Modifier.padding(horizontal = 16.dp)) }
                items(archived, key = { it.id }) { p ->
                    ListItem(
                        headlineContent = { Text(p.name, color = MaterialTheme.colorScheme.onSurfaceVariant) },
                        leadingContent = { Icon(projectIcon(p.icon), null, tint = Color(p.color).copy(alpha = 0.5f)) },
                        trailingContent = { TextButton(onClick = { vm.archive(p) }) { Text("Restaurar") } },
                        modifier = Modifier.clickable { actions.navigate(Routes.project(p.id)) },
                    )
                }
            }
        }
    }
    if (creating) ProjectDialog(null, { n, d, c, i -> vm.save(null, n, d, c, i) }, { creating = false })
    editing?.let { p -> ProjectDialog(p, { n, d, c, i -> vm.save(p.id, n, d, c, i) }, { editing = null }) }
    deleting?.let { p ->
        ConfirmDialog(
            "Excluir projeto?",
            "\"${p.name}\" será excluído. Tarefas, notas e checklists do projeto NÃO são apagados: voltam para a Caixa de entrada / sem projeto.",
            "Excluir", destructive = true, onConfirm = { vm.delete(p) }, onDismiss = { deleting = null },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectScreen(projectId: String) {
    val vm: ProjectsViewModel = hiltViewModel(key = "project-$projectId")
    LaunchedEffect(projectId) { vm.select(projectId) }
    val actions = LocalAppActions.current
    val project by vm.project.collectAsStateWithLifecycle()
    val notes by vm.projectNotes.collectAsStateWithLifecycle()
    val checklists by vm.projectChecklists.collectAsStateWithLifecycle()
    var tab by remember { mutableIntStateOf(0) }
    var editing by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(project?.name ?: "Projeto") },
            navigationIcon = { IconButton(onClick = { actions.back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar") } },
            actions = {
                IconButton(onClick = { actions.quickAddTask(com.organizador.app.ui.QuickAddPrefill(projectId = projectId)) }) { Icon(Icons.Default.Add, "Nova tarefa") }
                IconButton(onClick = { editing = true }) { Icon(Icons.Default.MoreVert, "Editar projeto") }
            },
        )
        project?.description?.takeIf { it.isNotBlank() }?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp))
        }
        TabRow(selectedTabIndex = tab) {
            Tab(tab == 0, { tab = 0 }, text = { Text("Tarefas") })
            Tab(tab == 1, { tab = 1 }, text = { Text("Notas (${notes.size})") })
            Tab(tab == 2, { tab = 2 }, text = { Text("Checklists (${checklists.size})") })
        }
        when (tab) {
            0 -> TaskListBody(TaskView.ByProject(projectId))
            1 -> if (notes.isEmpty()) {
                EmptyState(Icons.AutoMirrored.Outlined.EventNote, "Sem notas", "Vincule notas a este projeto pelo menu da nota.")
            } else {
                LazyColumn {
                    items(notes, key = { it.id }) { n ->
                        ListItem(
                            headlineContent = { Text(n.title.ifBlank { "Sem título" }) },
                            supportingContent = { Text(n.preview, maxLines = 2) },
                            modifier = Modifier.clickable { actions.navigate(Routes.note(n.id)) },
                        )
                    }
                }
            }
            else -> if (checklists.isEmpty()) {
                EmptyState(Icons.Outlined.Checklist, "Sem checklists", "Associe checklists a este projeto no editor do checklist.")
            } else {
                LazyColumn {
                    items(checklists, key = { it.template.id }) { c ->
                        ListItem(
                            headlineContent = { Text(c.template.title) },
                            supportingContent = { c.current?.let { Text("${it.checkedItems}/${it.totalItems} concluídos") } },
                            modifier = Modifier.clickable { actions.navigate(Routes.checklistRun(c.template.id)) },
                        )
                    }
                }
            }
        }
    }
    if (editing) project?.let { p -> ProjectDialog(p, { n, d, c, i -> vm.save(p.id, n, d, c, i) }, { editing = false }) }
}

@HiltViewModel
class LabelsViewModel @Inject constructor(private val labels: LabelRepository, private val time: TimeProvider) : ViewModel() {
    val categories: StateFlow<List<Category>> = labels.observeCategories().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val tags: StateFlow<List<Tag>> = labels.observeTags().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val error = MutableStateFlow<String?>(null)

    fun saveCategory(existing: Category?, name: String, color: Long) = viewModelScope.launch {
        labels.saveCategory(existing?.copy(name = name, color = color) ?: Category(Ids.new(), name, color, categories.value.size, time.now()))
    }

    fun saveTag(existing: Tag?, name: String, color: Long) = viewModelScope.launch {
        try {
            labels.saveTag(existing?.copy(name = name, color = color) ?: Tag(Ids.new(), name, color, time.now()))
        } catch (e: IllegalArgumentException) {
            error.value = e.message
        }
    }

    fun deleteCategory(c: Category) = viewModelScope.launch { labels.deleteCategory(c.id) }
    fun deleteTag(t: Tag) = viewModelScope.launch { labels.deleteTag(t.id) }
    fun moveCategory(index: Int, delta: Int) = viewModelScope.launch {
        val list = categories.value
        val target = index + delta
        if (target !in list.indices) return@launch
        labels.reorderCategories(list.toMutableList().apply { add(target, removeAt(index)) }.map { it.id })
    }
}

@Composable
private fun LabelDialog(title: String, initialName: String, initialColor: Long, onSave: (String, Long) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(initialName) }
    var color by remember { mutableStateOf(initialColor) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Nome") }, singleLine = true)
                ColorPickerRow(color, { color = it })
            }
        },
        confirmButton = { TextButton(enabled = name.isNotBlank(), onClick = { onSave(name.trim(), color); onDismiss() }) { Text("Salvar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LabelsScreen() {
    val vm: LabelsViewModel = hiltViewModel()
    val actions = LocalAppActions.current
    val categories by vm.categories.collectAsStateWithLifecycle()
    val tags by vm.tags.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    var editCategory by remember { mutableStateOf<Category?>(null) }
    var newCategory by remember { mutableStateOf(false) }
    var editTag by remember { mutableStateOf<Tag?>(null) }
    var newTag by remember { mutableStateOf(false) }
    var deleteCategory by remember { mutableStateOf<Category?>(null) }
    var deleteTag by remember { mutableStateOf<Tag?>(null) }
    var newCategoryEdit by remember { mutableStateOf<Category?>(null) }
    var newTagEdit by remember { mutableStateOf<Tag?>(null) }
    LaunchedEffect(error) { error?.let { actions.showMessage(it, null, null); vm.error.value = null } }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Etiquetas e categorias") },
            navigationIcon = { IconButton(onClick = { actions.back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar") } },
        )
        LazyColumn(Modifier.fillMaxWidth()) {
            item { SectionHeader("Categorias", Modifier.padding(horizontal = 16.dp)) { TextButton(onClick = { newCategory = true }) { Text("Nova") } } }
            items(categories, key = { "c" + it.id }) { c ->
                val index = categories.indexOf(c)
                ListItem(
                    headlineContent = { Text(c.name) },
                    leadingContent = { Icon(Icons.Outlined.Category, null, tint = Color(c.color)) },
                    trailingContent = {
                        androidx.compose.foundation.layout.Row {
                            IconButton(onClick = { vm.moveCategory(index, -1) }) { Icon(Icons.Outlined.KeyboardArrowUp, "Subir") }
                            IconButton(onClick = { editCategory = c }) { Icon(Icons.Default.MoreVert, "Editar") }
                        }
                    },
                    modifier = Modifier.clickable { actions.navigate(Routes.tasks("category", c.id)) },
                )
            }
            item { SectionHeader("Etiquetas", Modifier.padding(horizontal = 16.dp)) { TextButton(onClick = { newTag = true }) { Text("Nova") } } }
            items(tags, key = { "t" + it.id }) { t ->
                ListItem(
                    headlineContent = { Text("#${t.name}") },
                    leadingContent = { Icon(Icons.AutoMirrored.Outlined.Label, null, tint = Color(t.color)) },
                    trailingContent = { IconButton(onClick = { editTag = t }) { Icon(Icons.Default.MoreVert, "Editar") } },
                    modifier = Modifier.clickable { actions.navigate(Routes.tasks("tag", t.id)) },
                )
            }
            item { SectionHeader("Prioridades", Modifier.padding(horizontal = 16.dp)) }
            items(com.organizador.domain.model.Priority.entries, key = { "p" + it.level }) { p ->
                ListItem(
                    headlineContent = { Text(com.organizador.app.ui.common.priorityLabel(p)) },
                    leadingContent = { Icon(Icons.Default.Flag, null, tint = com.organizador.app.ui.common.priorityColor(p)) },
                    modifier = Modifier.clickable { actions.navigate(Routes.tasks("priority", p.level.toString())) },
                )
            }
        }
    }
    if (newCategory) LabelDialog("Nova categoria", "", Palette.default, { n, c -> vm.saveCategory(null, n, c) }, { newCategory = false })
    if (newTag) LabelDialog("Nova etiqueta", "", Palette.default, { n, c -> vm.saveTag(null, n, c) }, { newTag = false })
    editCategory?.let { c ->
        AlertDialog(
            onDismissRequest = { editCategory = null },
            title = { Text(c.name) },
            confirmButton = { TextButton(onClick = { deleteCategory = c; editCategory = null }) { Text("Excluir", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { newCategoryEdit = c; editCategory = null }) { Text("Editar") } },
        )
    }
    newCategoryEdit?.let { c -> LabelDialog("Editar categoria", c.name, c.color, { n, col -> vm.saveCategory(c, n, col) }, { newCategoryEdit = null }) }
    editTag?.let { t ->
        AlertDialog(
            onDismissRequest = { editTag = null },
            title = { Text("#${t.name}") },
            confirmButton = { TextButton(onClick = { deleteTag = t; editTag = null }) { Text("Excluir", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { newTagEdit = t; editTag = null }) { Text("Editar") } },
        )
    }
    newTagEdit?.let { t -> LabelDialog("Editar etiqueta", t.name, t.color, { n, col -> vm.saveTag(t, n, col) }, { newTagEdit = null }) }
    deleteCategory?.let { c ->
        ConfirmDialog("Excluir categoria?", "As tarefas desta categoria ficarão sem categoria.", "Excluir", destructive = true, onConfirm = { vm.deleteCategory(c) }, onDismiss = { deleteCategory = null })
    }
    deleteTag?.let { t ->
        ConfirmDialog("Excluir etiqueta?", "A etiqueta será removida de todas as tarefas e notas.", "Excluir", destructive = true, onConfirm = { vm.deleteTag(t) }, onDismiss = { deleteTag = null })
    }
}
