package com.organizador.app.ui.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.EventNote
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.organizador.app.ui.LocalAppActions
import com.organizador.app.ui.common.EmptyState
import com.organizador.app.ui.common.Formats
import com.organizador.app.ui.common.SectionHeader
import com.organizador.app.ui.navigation.Routes
import com.organizador.domain.model.ChecklistSummary
import com.organizador.domain.model.NoteSort
import com.organizador.domain.model.NoteSummary
import com.organizador.domain.model.NoteView
import com.organizador.domain.model.TaskFilter
import com.organizador.domain.model.TaskListItem
import com.organizador.domain.model.TaskQuery
import com.organizador.domain.model.TaskSort
import com.organizador.domain.model.TaskView
import com.organizador.domain.model.TimeProvider
import com.organizador.domain.repository.ChecklistRepository
import com.organizador.domain.repository.NoteRepository
import com.organizador.domain.repository.TaskRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class SearchResults(val tasks: List<TaskListItem> = emptyList(), val notes: List<NoteSummary> = emptyList(), val checklists: List<ChecklistSummary> = emptyList()) {
    val isEmpty: Boolean get() = tasks.isEmpty() && notes.isEmpty() && checklists.isEmpty()
}

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    tasks: TaskRepository,
    notes: NoteRepository,
    checklists: ChecklistRepository,
    time: TimeProvider,
) : ViewModel() {
    val query = MutableStateFlow("")
    val today = time.today()

    val results: StateFlow<SearchResults?> = query.debounce(250).map { it.trim() }.distinctUntilChanged().flatMapLatest { q ->
        if (q.length < 2) {
            flowOf(null)
        } else {
            combine(
                tasks.observeTasks(TaskQuery(TaskView.All, TaskFilter(text = q, includeCompleted = true), TaskSort.UPDATED)),
                notes.observeNotes(NoteView.All, NoteSort.UPDATED, q),
                checklists.observeSummaries(routines = null),
            ) { t, n, c ->
                SearchResults(t.take(50), n.take(50), c.filter { it.template.title.contains(q, ignoreCase = true) || it.template.description.contains(q, ignoreCase = true) })
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen() {
    val vm: SearchViewModel = hiltViewModel()
    val actions = LocalAppActions.current
    val query by vm.query.collectAsStateWithLifecycle()
    val results by vm.results.collectAsStateWithLifecycle()
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                TextField(
                    query, { vm.query.value = it }, placeholder = { Text("Pesquisar tarefas, notas e checklists") }, singleLine = true,
                    colors = TextFieldDefaults.colors(focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                )
            },
            navigationIcon = { IconButton(onClick = actions.openDrawer) { Icon(Icons.Default.Menu, "Menu") } },
            actions = { if (query.isNotEmpty()) IconButton(onClick = { vm.query.value = "" }) { Icon(Icons.Default.Close, "Limpar") } },
        )
        val r = results
        when {
            r == null -> EmptyState(Icons.Outlined.Search, "Pesquisa local", "Digite ao menos 2 letras. A pesquisa procura em títulos, descrições e conteúdo das notas, sem internet.")
            r.isEmpty -> EmptyState(Icons.Outlined.Search, "Nada encontrado", "Nenhum resultado para \"$query\".")
            else -> LazyColumn {
                if (r.tasks.isNotEmpty()) {
                    item { SectionHeader("Tarefas (${r.tasks.size})", Modifier.padding(horizontal = 16.dp)) }
                    items(r.tasks, key = { "t" + it.task.id }) { item ->
                        ListItem(
                            headlineContent = { Text(item.task.title, textDecoration = if (item.task.isCompleted) TextDecoration.LineThrough else null) },
                            supportingContent = {
                                Text(listOfNotNull(item.projectName ?: "Caixa de entrada", item.task.dueDate?.let { Formats.relativeDate(it, vm.today) }, if (item.task.isDeleted) "na lixeira" else null).joinToString(" · "))
                            },
                            leadingContent = { Icon(Icons.Outlined.TaskAlt, null) },
                            modifier = Modifier.clickable { actions.navigate(Routes.task(item.task.id)) },
                        )
                    }
                }
                if (r.notes.isNotEmpty()) {
                    item { SectionHeader("Notas (${r.notes.size})", Modifier.padding(horizontal = 16.dp)) }
                    items(r.notes, key = { "n" + it.id }) { n ->
                        ListItem(
                            headlineContent = { Text(n.title.ifBlank { "Sem título" }) },
                            supportingContent = { Text(n.preview, maxLines = 2, color = MaterialTheme.colorScheme.onSurfaceVariant) },
                            leadingContent = { Icon(Icons.AutoMirrored.Outlined.EventNote, null) },
                            modifier = Modifier.clickable { actions.navigate(Routes.note(n.id)) },
                        )
                    }
                }
                if (r.checklists.isNotEmpty()) {
                    item { SectionHeader("Checklists (${r.checklists.size})", Modifier.padding(horizontal = 16.dp)) }
                    items(r.checklists, key = { "c" + it.template.id }) { c ->
                        ListItem(
                            headlineContent = { Text(c.template.title) },
                            leadingContent = { Icon(Icons.Outlined.Checklist, null) },
                            modifier = Modifier.clickable { actions.navigate(Routes.checklistRun(c.template.id)) },
                        )
                    }
                }
            }
        }
    }
}
