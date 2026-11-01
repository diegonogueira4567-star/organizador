package com.organizador.app.ui.checklists

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
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.organizador.app.core.sync.SyncCoordinator
import com.organizador.app.ui.LocalAppActions
import com.organizador.app.ui.common.ColorDot
import com.organizador.app.ui.common.ColorPickerRow
import com.organizador.app.ui.common.ConfirmDialog
import com.organizador.app.ui.common.RecurrenceEditor
import com.organizador.app.ui.common.SectionHeader
import com.organizador.app.ui.navigation.Routes
import com.organizador.domain.model.ChecklistDraft
import com.organizador.domain.model.ChecklistItemDraft
import com.organizador.domain.model.ChecklistKind
import com.organizador.domain.model.Palette
import com.organizador.domain.model.Project
import com.organizador.domain.model.TimeProvider
import com.organizador.domain.recurrence.RecurrencePreset
import com.organizador.domain.recurrence.RecurrenceRule
import com.organizador.domain.repository.ChecklistRepository
import com.organizador.domain.repository.ProjectRepository
import com.organizador.domain.usecase.InstantiateTemplateUseCase
import com.organizador.domain.usecase.SaveChecklistUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject

data class ChecklistForm(
    val id: String? = null,
    val title: String = "",
    val description: String = "",
    val kind: ChecklistKind = ChecklistKind.SIMPLE,
    val isRoutine: Boolean = false,
    val projectId: String? = null,
    val color: Long = Palette.default,
    val items: List<ChecklistItemDraft> = emptyList(),
    val recurrence: RecurrenceRule? = null,
)

sealed interface ChecklistEditorEvent {
    data class Saved(val id: String, val isNew: Boolean) : ChecklistEditorEvent
    data class Message(val text: String) : ChecklistEditorEvent
    data class Open(val route: String) : ChecklistEditorEvent
}

@HiltViewModel
class ChecklistEditorViewModel @Inject constructor(
    private val checklists: ChecklistRepository,
    projects: ProjectRepository,
    private val save: SaveChecklistUseCase,
    private val instantiate: InstantiateTemplateUseCase,
    private val sync: SyncCoordinator,
    private val time: TimeProvider,
) : ViewModel() {
    val form = MutableStateFlow<ChecklistForm?>(null)
    private var original: ChecklistForm? = null
    val events = MutableSharedFlow<ChecklistEditorEvent>(extraBufferCapacity = 4)
    val projects: StateFlow<List<Project>> = projects.observeProjects().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val today: LocalDate get() = time.today()

    fun load(id: String?, kind: String?, routine: Boolean, date: LocalDate?) {
        if (form.value != null) return
        viewModelScope.launch {
            val loaded = id?.let { checklists.getDetails(it) }?.let { d ->
                ChecklistForm(
                    id = d.template.id, title = d.template.title, description = d.template.description, kind = d.template.kind,
                    isRoutine = d.template.isRoutine, projectId = d.template.projectId, color = d.template.color,
                    items = d.items.map { ChecklistItemDraft(it.id, it.title) }, recurrence = d.rule,
                )
            } ?: run {
                val k = kind?.let { runCatching { ChecklistKind.valueOf(it) }.getOrNull() } ?: if (routine) ChecklistKind.RECURRING else ChecklistKind.SIMPLE
                val start = date ?: time.today()
                ChecklistForm(
                    kind = k, isRoutine = routine,
                    recurrence = if (k == ChecklistKind.RECURRING) RecurrencePreset.EVERY_DAY.build("", start).copy(timeOfDay = if (routine) LocalTime.of(8, 0) else null) else null,
                )
            }
            original = loaded
            form.value = loaded
        }
    }

    val isDirty: Boolean get() = form.value != original

    fun update(transform: (ChecklistForm) -> ChecklistForm) {
        form.value = form.value?.let(transform)
    }

    fun setKind(kind: ChecklistKind) = update {
        it.copy(kind = kind, recurrence = if (kind == ChecklistKind.RECURRING) it.recurrence ?: RecurrencePreset.EVERY_DAY.build("", time.today()) else it.recurrence)
    }

    fun submit() = viewModelScope.launch {
        val f = form.value ?: return@launch
        if (f.title.isBlank()) { events.emit(ChecklistEditorEvent.Message("Informe um título")); return@launch }
        if (f.kind == ChecklistKind.RECURRING && f.recurrence == null) { events.emit(ChecklistEditorEvent.Message("Configure a recorrência")); return@launch }
        try {
            val id = save(
                ChecklistDraft(
                    id = f.id, title = f.title, description = f.description, kind = f.kind, isRoutine = f.isRoutine,
                    projectId = f.projectId, color = f.color, items = f.items.filter { it.title.isNotBlank() },
                    recurrence = if (f.kind == ChecklistKind.RECURRING) f.recurrence else null,
                ),
            )
            original = form.value
            sync.requestSync() // reagenda avisos de rotina
            events.emit(ChecklistEditorEvent.Saved(id, f.id == null))
        } catch (e: IllegalArgumentException) {
            events.emit(ChecklistEditorEvent.Message(e.message ?: "Dados inválidos"))
        }
    }

    fun useTemplate() = viewModelScope.launch {
        val id = form.value?.id ?: return@launch
        instantiate(id)?.let { events.emit(ChecklistEditorEvent.Open(Routes.checklistRun(it))) }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ChecklistEditorScreen(templateId: String?, presetKind: String?, presetRoutine: Boolean, presetDate: LocalDate?) {
    val vm: ChecklistEditorViewModel = hiltViewModel()
    LaunchedEffect(templateId) { vm.load(templateId, presetKind, presetRoutine, presetDate) }
    val actions = LocalAppActions.current
    val formState by vm.form.collectAsStateWithLifecycle()
    val projects by vm.projects.collectAsStateWithLifecycle()
    var newItem by remember { mutableStateOf("") }
    var projectMenu by remember { mutableStateOf(false) }
    var confirmDiscard by remember { mutableStateOf(false) }
    val titleFocus = remember { FocusRequester() }
    var titleFocused by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        vm.events.collect { e ->
            when (e) {
                is ChecklistEditorEvent.Saved -> {
                    actions.back()
                    if (e.isNew && vm.form.value?.kind != ChecklistKind.TEMPLATE) actions.navigate(Routes.checklistRun(e.id))
                }
                is ChecklistEditorEvent.Message -> actions.showMessage(e.text, null, null)
                is ChecklistEditorEvent.Open -> { actions.back(); actions.navigate(e.route) }
            }
        }
    }
    BackHandler(enabled = vm.isDirty) { confirmDiscard = true }
    val form = formState ?: return

    fun addItem() {
        if (newItem.isBlank()) return
        // Permite colar várias linhas de uma vez: cada linha vira um item.
        val lines = newItem.lines().map { it.trim() }.filter { it.isNotEmpty() }
        vm.update { f -> f.copy(items = f.items + lines.map { ChecklistItemDraft(title = it) }) }
        newItem = ""
    }

    Column(Modifier.fillMaxSize().imePadding()) {
        TopAppBar(
            title = { Text(if (form.id == null) (if (form.isRoutine) "Nova rotina" else "Novo checklist") else "Editar") },
            navigationIcon = { IconButton(onClick = { if (vm.isDirty) confirmDiscard = true else actions.back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar") } },
            actions = { TextButton(onClick = { vm.submit() }, enabled = form.title.isNotBlank()) { Text("Salvar") } },
        )
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            TextField(
                value = form.title, onValueChange = { v -> vm.update { it.copy(title = v) } }, placeholder = { Text(if (form.isRoutine) "Ex.: Rotina de sábado" else "Nome do checklist") },
                textStyle = MaterialTheme.typography.titleLarge, singleLine = true,
                colors = TextFieldDefaults.colors(focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth().focusRequester(titleFocus).onGloballyPositioned {
                    if (form.id == null && !titleFocused) {
                        titleFocused = true
                        titleFocus.requestFocus()
                    }
                },
            )
            TextField(
                value = form.description, onValueChange = { v -> vm.update { it.copy(description = v) } }, placeholder = { Text("Descrição") },
                colors = TextFieldDefaults.colors(focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent),
                modifier = Modifier.fillMaxWidth(),
            )

            SectionHeader("Tipo")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(
                    ChecklistKind.SIMPLE to "Simples", ChecklistKind.RECURRING to "Recorrente",
                    ChecklistKind.PERMANENT to "Permanente", ChecklistKind.TEMPLATE to "Modelo",
                ).forEach { (k, label) -> FilterChip(form.kind == k, { vm.setKind(k) }, { Text(label) }) }
            }
            Text(
                when (form.kind) {
                    ChecklistKind.SIMPLE -> "Lista única. Fica concluída quando todos os itens forem marcados."
                    ChecklistKind.RECURRING -> "Cada ocorrência gera uma execução nova com todos os itens pendentes; as anteriores ficam no histórico."
                    ChecklistKind.PERMANENT -> "Lista fixa que nunca reinicia."
                    ChecklistKind.TEMPLATE -> "Modelo reutilizável: use-o para criar novos checklists."
                },
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (form.kind == ChecklistKind.RECURRING) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                    Text("Mostrar em Rotinas", modifier = Modifier.weight(1f))
                    Switch(checked = form.isRoutine, onCheckedChange = { v -> vm.update { it.copy(isRoutine = v) } })
                }
            }

            SectionHeader("Itens (${form.items.size})")
            form.items.forEachIndexed { index, item ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextField(
                        value = item.title,
                        onValueChange = { v -> vm.update { f -> f.copy(items = f.items.toMutableList().also { it[index] = item.copy(title = v) }) } },
                        colors = TextFieldDefaults.colors(focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent),
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { vm.update { f -> f.copy(items = f.items.move(index, -1)) } }, enabled = index > 0) { Icon(Icons.Outlined.KeyboardArrowUp, "Subir") }
                    IconButton(onClick = { vm.update { f -> f.copy(items = f.items.move(index, 1)) } }, enabled = index < form.items.lastIndex) { Icon(Icons.Outlined.KeyboardArrowDown, "Descer") }
                    IconButton(onClick = { vm.update { f -> f.copy(items = f.items.toMutableList().also { it.removeAt(index) }) } }) { Icon(Icons.Default.Close, "Remover") }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    // Enter adiciona o item; colar várias linhas cria um item por linha.
                    value = newItem, onValueChange = { v -> newItem = v; if ('\n' in v) addItem() }, placeholder = { Text("Novo item") },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done, capitalization = KeyboardCapitalization.Sentences),
                    keyboardActions = KeyboardActions(onDone = { addItem() }),
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { addItem() }) { Icon(Icons.Default.Add, "Adicionar item") }
            }

            if (form.kind == ChecklistKind.RECURRING) {
                SectionHeader("Recorrência")
                RecurrenceEditor(
                    rule = form.recurrence,
                    defaultStart = vm.today,
                    onChange = { r -> vm.update { it.copy(recurrence = r) } },
                    allowNone = false,
                    showResetTime = true,
                )
                if (form.id != null) {
                    Text(
                        "Alterar a recorrência não modifica execuções anteriores; a execução atual sem progresso é ajustada à nova regra.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            SectionHeader("Organização")
            Column {
                OutlinedButton(onClick = { projectMenu = true }) { Text(projects.firstOrNull { it.id == form.projectId }?.name ?: "Sem projeto") }
                DropdownMenu(projectMenu, { projectMenu = false }) {
                    DropdownMenuItem(text = { Text("Sem projeto") }, onClick = { vm.update { it.copy(projectId = null) }; projectMenu = false })
                    projects.forEach { p -> DropdownMenuItem(text = { Text(p.name) }, leadingIcon = { ColorDot(p.color, size = 12) }, onClick = { vm.update { it.copy(projectId = p.id) }; projectMenu = false }) }
                }
            }
            Spacer(Modifier.height(8.dp))
            ColorPickerRow(form.color, { c -> vm.update { it.copy(color = c) } })

            if (form.kind == ChecklistKind.TEMPLATE && form.id != null) {
                Spacer(Modifier.height(16.dp))
                Button(onClick = { vm.useTemplate() }, modifier = Modifier.fillMaxWidth()) { Text("Usar este modelo") }
            }
            Spacer(Modifier.height(48.dp))
        }
    }
    if (confirmDiscard) {
        ConfirmDialog("Descartar alterações?", "As alterações não salvas serão perdidas.", "Descartar", destructive = true, onConfirm = { actions.back() }, onDismiss = { confirmDiscard = false })
    }
}

private fun <T> List<T>.move(index: Int, delta: Int): List<T> {
    val target = index + delta
    if (target !in indices) return this
    return toMutableList().apply { add(target, removeAt(index)) }
}
