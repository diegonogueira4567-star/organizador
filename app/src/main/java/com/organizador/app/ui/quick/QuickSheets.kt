package com.organizador.app.ui.quick

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.EventNote
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.OpenInFull
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.organizador.app.ui.LocalAppActions
import com.organizador.app.ui.QuickAddPrefill
import com.organizador.app.ui.common.ColorDot
import com.organizador.app.ui.common.DatePickerModal
import com.organizador.app.ui.common.Formats
import com.organizador.app.ui.common.priorityColor
import com.organizador.app.ui.common.priorityLabel
import com.organizador.app.ui.tasks.SheetAction
import com.organizador.domain.model.Priority
import com.organizador.domain.model.Project
import com.organizador.domain.model.TaskDraft
import com.organizador.domain.model.TimeProvider
import com.organizador.domain.repository.ProjectRepository
import com.organizador.domain.repository.TaskRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class QuickAddViewModel @Inject constructor(
    private val tasks: TaskRepository,
    projects: ProjectRepository,
    private val time: TimeProvider,
) : ViewModel() {
    val projects: StateFlow<List<Project>> = projects.observeProjects().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val today: LocalDate get() = time.today()

    fun save(title: String, description: String, date: LocalDate?, priority: Priority, projectId: String?, then: (String) -> Unit) = viewModelScope.launch {
        val id = tasks.save(TaskDraft(title = title.trim(), description = description.trim(), dueDate = date, priority = priority, projectId = projectId))
        then(id)
    }
}

/** Captura rápida de tarefa no estilo "adicionar e continuar". */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickAddTaskSheet(prefill: QuickAddPrefill, onDismiss: () -> Unit, onOpenFull: (String) -> Unit) {
    val vm: QuickAddViewModel = hiltViewModel()
    val actions = LocalAppActions.current
    val projects by vm.projects.collectAsStateWithLifecycle()
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(prefill.date) }
    var priority by remember { mutableStateOf(Priority.P4) }
    var projectId by remember { mutableStateOf(prefill.projectId) }
    var pickDate by remember { mutableStateOf(false) }
    var priorityMenu by remember { mutableStateOf(false) }
    var projectMenu by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    var focused by remember { mutableStateOf(false) }

    fun submit(openFull: Boolean) {
        if (title.isBlank()) return
        vm.save(title, description, date, priority, projectId) { id ->
            if (openFull) onOpenFull(id) else actions.showMessage("Tarefa adicionada", null, null)
        }
        title = ""
        description = ""
        if (openFull) onDismiss()
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = 12.dp).imePadding()) {
            TextField(
                value = title, onValueChange = { title = it }, placeholder = { Text("Ex.: Pagar a conta de luz") },
                textStyle = MaterialTheme.typography.titleMedium, singleLine = true,
                colors = TextFieldDefaults.colors(focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send, capitalization = KeyboardCapitalization.Sentences),
                keyboardActions = KeyboardActions(onSend = { submit(false) }),
                // O foco só pode ser pedido depois que a folha estiver posicionada (BUG-003).
                modifier = Modifier.fillMaxWidth().focusRequester(focus).onGloballyPositioned {
                    if (!focused) {
                        focused = true
                        focus.requestFocus()
                    }
                },
            )
            TextField(
                value = description, onValueChange = { description = it }, placeholder = { Text("Descrição") },
                colors = TextFieldDefaults.colors(focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(
                    onClick = { pickDate = true },
                    label = { Text(date?.let { Formats.relativeDate(it, vm.today) } ?: "Data") },
                    leadingIcon = { Icon(Icons.Outlined.CalendarToday, null) },
                )
                AssistChip(onClick = { date = vm.today }, label = { Text("Hoje") })
                AssistChip(onClick = { date = vm.today.plusDays(1) }, label = { Text("Amanhã") })
                Column {
                    AssistChip(onClick = { priorityMenu = true }, label = { Text(priorityLabel(priority)) }, leadingIcon = { Icon(Icons.Default.Flag, null, tint = priorityColor(priority)) })
                    DropdownMenu(priorityMenu, { priorityMenu = false }) {
                        Priority.entries.forEach { p ->
                            DropdownMenuItem(text = { Text(priorityLabel(p)) }, leadingIcon = { Icon(Icons.Default.Flag, null, tint = priorityColor(p)) }, onClick = { priority = p; priorityMenu = false })
                        }
                    }
                }
                Column {
                    AssistChip(
                        onClick = { projectMenu = true },
                        label = { Text(projects.firstOrNull { it.id == projectId }?.name ?: "Entrada") },
                        leadingIcon = { Icon(Icons.Outlined.Folder, null) },
                    )
                    DropdownMenu(projectMenu, { projectMenu = false }) {
                        DropdownMenuItem(text = { Text("Caixa de entrada") }, onClick = { projectId = null; projectMenu = false })
                        projects.forEach { p ->
                            DropdownMenuItem(text = { Text(p.name) }, leadingIcon = { ColorDot(p.color, size = 12) }, onClick = { projectId = p.id; projectMenu = false })
                        }
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                IconButton(onClick = { submit(true) }, enabled = title.isNotBlank()) { Icon(Icons.Outlined.OpenInFull, "Mais detalhes") }
                Text("Mais detalhes", style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
                FilledIconButton(onClick = { submit(false) }, enabled = title.isNotBlank()) { Icon(Icons.AutoMirrored.Filled.Send, "Adicionar") }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
    if (pickDate) DatePickerModal(date, { date = it }, { pickDate = false })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickCreateSheet(
    onDismiss: () -> Unit,
    onTask: () -> Unit,
    onNote: () -> Unit,
    onChecklist: () -> Unit,
    onRoutine: () -> Unit,
    onProject: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text("Criar", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
        SheetAction(Icons.Outlined.TaskAlt, "Tarefa", onTask)
        SheetAction(Icons.AutoMirrored.Outlined.EventNote, "Nota", onNote)
        SheetAction(Icons.Outlined.Checklist, "Checklist", onChecklist)
        SheetAction(Icons.Outlined.Repeat, "Rotina", onRoutine)
        SheetAction(Icons.Outlined.Folder, "Projeto", onProject)
        Spacer(Modifier.height(24.dp))
    }
}
