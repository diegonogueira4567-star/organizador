package com.organizador.app.ui.tasks

import android.content.Context
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Notes
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.organizador.app.ui.LocalAppActions
import com.organizador.app.ui.common.AttachmentList
import com.organizador.app.ui.common.ColorDot
import com.organizador.app.ui.common.ConfirmDialog
import com.organizador.app.ui.common.DatePickerModal
import com.organizador.app.ui.common.Formats
import com.organizador.app.ui.common.RecurrenceEditor
import com.organizador.app.ui.common.SectionHeader
import com.organizador.app.ui.common.TextInputDialog
import com.organizador.app.ui.common.TimePickerModal
import com.organizador.app.ui.common.priorityColor
import com.organizador.app.ui.common.priorityLabel
import com.organizador.app.ui.common.queryFileMeta
import com.organizador.app.ui.navigation.Routes
import com.organizador.app.ui.theme.LocalAppSettings
import com.organizador.domain.model.Attachment
import com.organizador.domain.model.AttachmentOwner
import com.organizador.domain.model.Category
import com.organizador.domain.model.Priority
import com.organizador.domain.model.Project
import com.organizador.domain.model.SubtaskDraft
import com.organizador.domain.model.Tag
import com.organizador.domain.model.TaskDraft
import com.organizador.domain.model.TimeProvider
import com.organizador.domain.recurrence.RecurrenceRule
import com.organizador.domain.repository.AttachmentRepository
import com.organizador.domain.repository.LabelRepository
import com.organizador.domain.repository.ProjectRepository
import com.organizador.domain.repository.TaskRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject

data class TaskForm(
    val id: String? = null,
    val title: String = "",
    val description: String = "",
    val notes: String = "",
    val dueDate: LocalDate? = null,
    val dueTime: LocalTime? = null,
    val priority: Priority = Priority.P4,
    val projectId: String? = null,
    val categoryId: String? = null,
    val tagIds: Set<String> = emptySet(),
    val subtasks: List<SubtaskDraft> = emptyList(),
    val recurrence: RecurrenceRule? = null,
    val reminderOffsets: List<Int> = emptyList(),
    val linkedNoteId: String? = null,
    val completed: Boolean = false,
    val archived: Boolean = false,
    val deleted: Boolean = false,
) {
    fun toDraft() = TaskDraft(
        id = id, title = title, description = description, notes = notes,
        // Recorrência exige uma data de referência: usa o início da regra se o prazo estiver vazio.
        dueDate = dueDate ?: recurrence?.startDate, dueTime = dueTime, priority = priority,
        projectId = projectId, categoryId = categoryId, tagIds = tagIds, subtasks = subtasks.filter { it.title.isNotBlank() },
        recurrence = recurrence, reminderOffsets = if (dueDate != null || recurrence != null) reminderOffsets else emptyList(),
        linkedNoteId = linkedNoteId,
    )
}

sealed interface EditorEvent {
    data object Saved : EditorEvent
    data object Closed : EditorEvent
    data class Message(val text: String) : EditorEvent
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TaskEditorViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val tasks: TaskRepository,
    projects: ProjectRepository,
    private val labels: LabelRepository,
    private val attachmentsRepo: AttachmentRepository,
    private val actions: TaskActions,
    private val time: TimeProvider,
) : ViewModel() {
    val form = MutableStateFlow<TaskForm?>(null)
    private var original: TaskForm? = null
    val events = MutableSharedFlow<EditorEvent>(extraBufferCapacity = 4)
    val projects: StateFlow<List<Project>> = projects.observeProjects().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val categories: StateFlow<List<Category>> = labels.observeCategories().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val tags: StateFlow<List<Tag>> = labels.observeTags().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    private val idFlow = MutableStateFlow<String?>(null)
    val attachments: StateFlow<List<Attachment>> = idFlow.flatMapLatest { id ->
        if (id == null) flowOf(emptyList()) else attachmentsRepo.observe(AttachmentOwner.TASK, id)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val today: LocalDate get() = time.today()

    fun load(taskId: String?, presetProjectId: String?, presetDate: LocalDate?) {
        if (form.value != null) return
        viewModelScope.launch {
            val loaded = taskId?.let { tasks.getDetails(it) }?.let { d ->
                TaskForm(
                    id = d.task.id, title = d.task.title, description = d.task.description, notes = d.task.notes,
                    dueDate = d.task.dueDate, dueTime = d.task.dueTime, priority = d.task.priority, projectId = d.task.projectId,
                    categoryId = d.task.categoryId, tagIds = d.tags.map { it.id }.toSet(),
                    subtasks = d.subtasks.map { SubtaskDraft(it.id, it.title, it.done) }, recurrence = d.recurrence,
                    reminderOffsets = d.reminders.mapNotNull { it.offsetMinutes }, linkedNoteId = d.task.linkedNoteId,
                    completed = d.task.isCompleted, archived = d.task.archived, deleted = d.task.isDeleted,
                )
            } ?: TaskForm(projectId = presetProjectId, dueDate = presetDate)
            original = loaded
            form.value = loaded
            idFlow.value = loaded.id
        }
    }

    fun update(transform: (TaskForm) -> TaskForm) {
        form.value = form.value?.let(transform)
    }

    val isDirty: Boolean get() = form.value != original

    fun save(close: Boolean = true) = viewModelScope.launch {
        val current = form.value ?: return@launch
        if (current.title.isBlank()) {
            events.emit(EditorEvent.Message("Informe um título"))
            return@launch
        }
        val id = persist(current)
        if (close) events.emit(EditorEvent.Saved) else events.emit(EditorEvent.Message("Salvo"))
        if (!close) idFlow.value = id
    }

    private suspend fun persist(current: TaskForm): String {
        val id = tasks.save(current.toDraft())
        val saved = current.copy(id = id)
        form.value = saved
        original = saved
        return id
    }

    fun addTag(name: String) = viewModelScope.launch {
        val tag = labels.findOrCreateTag(name)
        update { it.copy(tagIds = it.tagIds + tag.id) }
    }

    fun addAttachment(uri: Uri) = viewModelScope.launch {
        val current = form.value ?: return@launch
        if (current.title.isBlank()) {
            events.emit(EditorEvent.Message("Informe um título antes de anexar arquivos"))
            return@launch
        }
        try {
            // Anexos precisam de uma tarefa gravada: salva automaticamente se ainda for nova.
            val id = current.id ?: persist(current).also { idFlow.value = it }
            withContext(Dispatchers.IO) {
                val (name, type) = queryFileMeta(context, uri)
                val stream = context.contentResolver.openInputStream(uri) ?: error("Não foi possível ler o arquivo")
                attachmentsRepo.add(AttachmentOwner.TASK, id, name, type, stream, time.now())
            }
        } catch (e: Exception) {
            events.emit(EditorEvent.Message(e.message ?: "Falha ao anexar arquivo"))
        }
    }

    fun removeAttachment(a: Attachment) = viewModelScope.launch { attachmentsRepo.delete(a.id) }
    fun fileFor(a: Attachment): File = attachmentsRepo.fileFor(a)

    fun toggleCompleted() = viewModelScope.launch {
        val current = form.value ?: return@launch
        val id = current.id ?: return@launch
        if (isDirty) persist(current)
        actions.toggle(id, current.completed)
        events.emit(EditorEvent.Closed)
    }

    fun delete() = viewModelScope.launch {
        form.value?.id?.let { actions.delete(it) }
        events.emit(EditorEvent.Closed)
    }

    fun restore() = viewModelScope.launch {
        form.value?.id?.let { actions.restore(it) }
        update { it.copy(deleted = false) }
    }

    fun duplicate() = viewModelScope.launch {
        form.value?.id?.let { actions.duplicate(it) }
        events.emit(EditorEvent.Message("Cópia criada"))
    }

    fun archive() = viewModelScope.launch {
        val current = form.value ?: return@launch
        current.id?.let { actions.archive(it, !current.archived) }
        events.emit(EditorEvent.Closed)
    }
}

private val reminderChoices = listOf(0 to "No horário", 10 to "10 min antes", 30 to "30 min antes", 60 to "1 h antes", 1440 to "1 dia antes")

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TaskEditorScreen(taskId: String?, presetProjectId: String?, presetDate: LocalDate?) {
    val vm: TaskEditorViewModel = hiltViewModel()
    LaunchedEffect(taskId) { vm.load(taskId, presetProjectId, presetDate) }
    val actions = LocalAppActions.current
    val settings = LocalAppSettings.current
    val formState by vm.form.collectAsStateWithLifecycle()
    val projects by vm.projects.collectAsStateWithLifecycle()
    val categories by vm.categories.collectAsStateWithLifecycle()
    val tags by vm.tags.collectAsStateWithLifecycle()
    val attachments by vm.attachments.collectAsStateWithLifecycle()
    var pickDate by remember { mutableStateOf(false) }
    var pickTime by remember { mutableStateOf(false) }
    var projectMenu by remember { mutableStateOf(false) }
    var categoryMenu by remember { mutableStateOf(false) }
    var priorityMenu by remember { mutableStateOf(false) }
    var moreMenu by remember { mutableStateOf(false) }
    var newTag by remember { mutableStateOf(false) }
    var confirmDiscard by remember { mutableStateOf(false) }
    var newSubtask by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        vm.events.collect { e ->
            when (e) {
                EditorEvent.Saved, EditorEvent.Closed -> actions.back()
                is EditorEvent.Message -> actions.showMessage(e.text, null, null)
            }
        }
    }
    BackHandler(enabled = vm.isDirty) { confirmDiscard = true }

    val form = formState ?: return
    Column(Modifier.fillMaxSize().imePadding()) {
        TopAppBar(
            title = { Text(if (form.id == null) "Nova tarefa" else "Tarefa") },
            navigationIcon = { IconButton(onClick = { if (vm.isDirty) confirmDiscard = true else actions.back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar") } },
            actions = {
                TextButton(onClick = { vm.save() }, enabled = form.title.isNotBlank()) { Text("Salvar") }
                if (form.id != null) {
                    IconButton(onClick = { moreMenu = true }) { Icon(Icons.Default.MoreVert, "Mais") }
                    DropdownMenu(moreMenu, { moreMenu = false }) {
                        DropdownMenuItem(text = { Text(if (form.completed) "Reabrir" else "Concluir") }, onClick = { moreMenu = false; vm.toggleCompleted() })
                        DropdownMenuItem(text = { Text("Duplicar") }, onClick = { moreMenu = false; vm.duplicate() })
                        DropdownMenuItem(text = { Text(if (form.archived) "Desarquivar" else "Arquivar") }, onClick = { moreMenu = false; vm.archive() })
                        if (form.deleted) {
                            DropdownMenuItem(text = { Text("Restaurar da lixeira") }, onClick = { moreMenu = false; vm.restore() })
                        } else {
                            DropdownMenuItem(text = { Text("Excluir") }, onClick = { moreMenu = false; vm.delete() })
                        }
                    }
                }
            },
        )
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            if (form.deleted) Text("Esta tarefa está na lixeira.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            TextField(
                value = form.title, onValueChange = { v -> vm.update { it.copy(title = v) } },
                placeholder = { Text("Nome da tarefa") }, textStyle = MaterialTheme.typography.titleLarge,
                colors = TextFieldDefaults.colors(focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )
            TextField(
                value = form.description, onValueChange = { v -> vm.update { it.copy(description = v) } },
                placeholder = { Text("Descrição") },
                colors = TextFieldDefaults.colors(focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(
                    onClick = { pickDate = true },
                    label = { Text(form.dueDate?.let { Formats.relativeDate(it, vm.today) } ?: "Data") },
                    leadingIcon = { Icon(Icons.Outlined.CalendarToday, null) },
                )
                AssistChip(
                    onClick = { pickTime = true },
                    label = { Text(form.dueTime?.let { Formats.time(it, settings.timeFormat) } ?: "Horário") },
                    leadingIcon = { Icon(Icons.Outlined.Schedule, null) },
                )
                Column {
                    AssistChip(
                        onClick = { priorityMenu = true },
                        label = { Text(priorityLabel(form.priority)) },
                        leadingIcon = { Icon(Icons.Default.Flag, null, tint = priorityColor(form.priority)) },
                    )
                    DropdownMenu(priorityMenu, { priorityMenu = false }) {
                        Priority.entries.forEach { p ->
                            DropdownMenuItem(
                                text = { Text(priorityLabel(p)) },
                                leadingIcon = { Icon(Icons.Default.Flag, null, tint = priorityColor(p)) },
                                onClick = { vm.update { it.copy(priority = p) }; priorityMenu = false },
                            )
                        }
                    }
                }
                Column {
                    AssistChip(
                        onClick = { projectMenu = true },
                        label = { Text(projects.firstOrNull { it.id == form.projectId }?.name ?: "Caixa de entrada") },
                        leadingIcon = { Icon(Icons.Outlined.Folder, null) },
                    )
                    DropdownMenu(projectMenu, { projectMenu = false }) {
                        DropdownMenuItem(text = { Text("Caixa de entrada") }, onClick = { vm.update { it.copy(projectId = null) }; projectMenu = false })
                        projects.forEach { p ->
                            DropdownMenuItem(text = { Text(p.name) }, leadingIcon = { ColorDot(p.color, size = 12) }, onClick = { vm.update { it.copy(projectId = p.id) }; projectMenu = false })
                        }
                    }
                }
                Column {
                    AssistChip(
                        onClick = { categoryMenu = true },
                        label = { Text(categories.firstOrNull { it.id == form.categoryId }?.name ?: "Categoria") },
                        leadingIcon = { Icon(Icons.Outlined.Category, null) },
                    )
                    DropdownMenu(categoryMenu, { categoryMenu = false }) {
                        DropdownMenuItem(text = { Text("Sem categoria") }, onClick = { vm.update { it.copy(categoryId = null) }; categoryMenu = false })
                        categories.forEach { c ->
                            DropdownMenuItem(text = { Text(c.name) }, leadingIcon = { ColorDot(c.color, size = 12) }, onClick = { vm.update { it.copy(categoryId = c.id) }; categoryMenu = false })
                        }
                    }
                }
            }

            SectionHeader("Etiquetas")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                tags.forEach { tag ->
                    val selected = tag.id in form.tagIds
                    FilterChip(
                        selected = selected,
                        onClick = { vm.update { it.copy(tagIds = if (selected) it.tagIds - tag.id else it.tagIds + tag.id) } },
                        label = { Text("#${tag.name}") },
                        leadingIcon = { ColorDot(tag.color, size = 8) },
                    )
                }
                AssistChip(onClick = { newTag = true }, label = { Text("Nova") }, leadingIcon = { Icon(Icons.Default.Add, null) })
            }

            SectionHeader("Subtarefas (${form.subtasks.count { it.done }}/${form.subtasks.size})")
            form.subtasks.forEachIndexed { index, sub ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = sub.done, onCheckedChange = { checked -> vm.update { f -> f.copy(subtasks = f.subtasks.toMutableList().also { it[index] = sub.copy(done = checked) }) } })
                    TextField(
                        value = sub.title,
                        onValueChange = { v -> vm.update { f -> f.copy(subtasks = f.subtasks.toMutableList().also { it[index] = sub.copy(title = v) }) } },
                        colors = TextFieldDefaults.colors(focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent),
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { vm.update { f -> f.copy(subtasks = f.subtasks.toMutableList().also { it.removeAt(index) }) } }) { Icon(Icons.Default.Close, "Remover subtarefa") }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = newSubtask, onValueChange = { newSubtask = it }, placeholder = { Text("Adicionar subtarefa") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done, capitalization = KeyboardCapitalization.Sentences),
                    keyboardActions = KeyboardActions(onDone = {
                        if (newSubtask.isNotBlank()) { vm.update { it.copy(subtasks = it.subtasks + SubtaskDraft(title = newSubtask.trim())) }; newSubtask = "" }
                    }),
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = {
                    if (newSubtask.isNotBlank()) { vm.update { it.copy(subtasks = it.subtasks + SubtaskDraft(title = newSubtask.trim())) }; newSubtask = "" }
                }) { Icon(Icons.Default.Add, "Adicionar") }
            }

            SectionHeader("Repetição")
            RecurrenceEditor(
                rule = form.recurrence,
                defaultStart = form.dueDate ?: vm.today,
                onChange = { r -> vm.update { it.copy(recurrence = r, dueDate = it.dueDate ?: r?.startDate) } },
                showTime = false,
            )

            SectionHeader("Lembretes")
            if (form.dueDate == null && form.recurrence == null) {
                Text("Defina uma data para ativar lembretes.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    reminderChoices.forEach { (offset, label) ->
                        val selected = offset in form.reminderOffsets
                        FilterChip(
                            selected = selected,
                            onClick = { vm.update { it.copy(reminderOffsets = if (selected) it.reminderOffsets - offset else it.reminderOffsets + offset) } },
                            label = { Text(label) },
                            leadingIcon = { Icon(Icons.Outlined.Alarm, null) },
                        )
                    }
                }
                if (form.dueTime == null) {
                    Text("Sem horário definido, o lembrete usa 09:00 do dia.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            SectionHeader("Observações")
            OutlinedTextField(
                value = form.notes, onValueChange = { v -> vm.update { it.copy(notes = v) } },
                placeholder = { Text("Observações") }, minLines = 3, modifier = Modifier.fillMaxWidth(),
                leadingIcon = { Icon(Icons.Outlined.Notes, null) },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            )

            SectionHeader("Anexos")
            AttachmentList(
                attachments = attachments,
                fileFor = vm::fileFor,
                onAdd = { vm.addAttachment(it) },
                onRemove = { vm.removeAttachment(it) },
                onError = { actions.showMessage(it, null, null) },
            )

            form.linkedNoteId?.let { noteId ->
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                TextButton(onClick = { actions.navigate(Routes.note(noteId)) }) { Text("Abrir nota vinculada") }
            }
            Spacer(Modifier.height(48.dp))
        }
    }

    if (pickDate) DatePickerModal(form.dueDate, { d -> vm.update { it.copy(dueDate = d) } }, { pickDate = false })
    if (pickTime) TimePickerModal(form.dueTime, { t -> vm.update { it.copy(dueTime = t, dueDate = it.dueDate ?: vm.today) } }, { pickTime = false })
    if (newTag) TextInputDialog("Nova etiqueta", label = "Nome", onConfirm = { vm.addTag(it) }, onDismiss = { newTag = false })
    if (confirmDiscard) {
        ConfirmDialog(
            "Descartar alterações?", "As alterações não salvas desta tarefa serão perdidas.", "Descartar", destructive = true,
            onConfirm = { actions.back() }, onDismiss = { confirmDiscard = false },
        )
    }
}
