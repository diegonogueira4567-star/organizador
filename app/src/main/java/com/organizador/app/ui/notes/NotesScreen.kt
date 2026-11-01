package com.organizador.app.ui.notes

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.automirrored.outlined.EventNote
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.organizador.app.ui.LocalAppActions
import com.organizador.app.ui.common.CardSurface
import com.organizador.app.ui.common.ConfirmDialog
import com.organizador.app.ui.common.EmptyState
import com.organizador.app.ui.common.Formats
import com.organizador.app.ui.common.TextInputDialog
import com.organizador.app.ui.navigation.Routes
import com.organizador.app.ui.tasks.SheetAction
import com.organizador.app.ui.theme.LocalSpacing
import com.organizador.domain.model.Ids
import com.organizador.domain.model.NoteFolder
import com.organizador.domain.model.NoteSort
import com.organizador.domain.model.NoteSummary
import com.organizador.domain.model.NoteView
import com.organizador.domain.model.Palette
import com.organizador.domain.model.Tag
import com.organizador.domain.model.TimeProvider
import com.organizador.domain.repository.LabelRepository
import com.organizador.domain.repository.NoteRepository
import com.organizador.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.ZoneId
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class NotesViewModel @Inject constructor(
    private val notes: NoteRepository,
    labels: LabelRepository,
    private val settings: SettingsRepository,
    private val time: TimeProvider,
) : ViewModel() {
    val view = MutableStateFlow<NoteView>(NoteView.All)
    val query = MutableStateFlow("")
    val sort = MutableStateFlow<NoteSort?>(null)
    val folders: StateFlow<List<NoteFolder>> = notes.observeFolders().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val tags: StateFlow<List<Tag>> = labels.observeTags().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val items: StateFlow<List<NoteSummary>?> = combine(view, query, sort, settings.settings) { v, q, s, prefs -> Triple(v, q, s ?: prefs.defaultNoteSort) }
        .flatMapLatest { (v, q, s) -> notes.observeNotes(v, s, q) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun setSort(s: NoteSort) {
        sort.value = s
        viewModelScope.launch { settings.update { it.copy(defaultNoteSort = s) } }
    }

    fun createFolder(name: String, parentId: String?) = viewModelScope.launch {
        notes.saveFolder(NoteFolder(Ids.new(), name, parentId, Palette.default, folders.value.size, time.now()))
    }

    fun renameFolder(folder: NoteFolder, name: String) = viewModelScope.launch { notes.saveFolder(folder.copy(name = name)) }
    fun deleteFolder(folder: NoteFolder) = viewModelScope.launch { notes.deleteFolder(folder.id) }

    fun toggleFavorite(n: NoteSummary) = meta(n) { it.copy(favorite = !it.favorite) }
    fun togglePin(n: NoteSummary) = meta(n) { it.copy(pinned = !it.pinned) }
    fun toggleArchive(n: NoteSummary) = meta(n) { it.copy(archived = !it.archived) }
    fun move(n: NoteSummary, folderId: String?) = viewModelScope.launch {
        val note = notes.getNote(n.id) ?: return@launch
        notes.updateMeta(n.id, folderId, note.projectId, note.favorite, note.pinned, note.archived)
    }

    private fun meta(n: NoteSummary, transform: (NoteSummary) -> NoteSummary) = viewModelScope.launch {
        val note = notes.getNote(n.id) ?: return@launch
        val t = transform(n)
        notes.updateMeta(n.id, note.folderId, note.projectId, t.favorite, t.pinned, t.archived)
    }

    fun delete(n: NoteSummary) = viewModelScope.launch { notes.softDelete(n.id, time.now()) }
    fun restore(n: NoteSummary) = viewModelScope.launch { notes.restore(n.id) }
    fun deleteForever(n: NoteSummary) = viewModelScope.launch { notes.deletePermanently(n.id) }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun NotesScreen(folderId: String?) {
    val vm: NotesViewModel = hiltViewModel(key = "notes-$folderId")
    LaunchedEffect(folderId) { if (folderId != null) vm.view.value = NoteView.Folder(folderId) }
    val actions = LocalAppActions.current
    val spacing = LocalSpacing.current
    val view by vm.view.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val items by vm.items.collectAsStateWithLifecycle()
    val sort by vm.sort.collectAsStateWithLifecycle()
    val settings = com.organizador.app.ui.theme.LocalAppSettings.current
    val folders by vm.folders.collectAsStateWithLifecycle()
    val tags by vm.tags.collectAsStateWithLifecycle()
    var searching by remember { mutableStateOf(false) }
    var sortMenu by remember { mutableStateOf(false) }
    var newFolder by remember { mutableStateOf(false) }
    var folderMenu by remember { mutableStateOf<NoteFolder?>(null) }
    var renameFolder by remember { mutableStateOf<NoteFolder?>(null) }
    var deleteFolder by remember { mutableStateOf<NoteFolder?>(null) }
    var noteMenu by remember { mutableStateOf<NoteSummary?>(null) }
    var moveNote by remember { mutableStateOf<NoteSummary?>(null) }
    var deleteForever by remember { mutableStateOf<NoteSummary?>(null) }
    val currentFolder = (view as? NoteView.Folder)?.folderId

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                if (searching) {
                    TextField(
                        query, { vm.query.value = it }, placeholder = { Text("Pesquisar no título e no conteúdo") }, singleLine = true,
                        colors = TextFieldDefaults.colors(focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent),
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    Text(currentFolder?.let { folderPath(it, folders) } ?: "Notas", maxLines = 1)
                }
            },
            navigationIcon = { IconButton(onClick = actions.openDrawer) { Icon(Icons.Default.Menu, "Menu") } },
            actions = {
                IconButton(onClick = { if (searching) vm.query.value = ""; searching = !searching }) { Icon(if (searching) Icons.Default.Close else Icons.Default.Search, "Pesquisar") }
                IconButton(onClick = { newFolder = true }) { Icon(Icons.Outlined.CreateNewFolder, "Nova pasta") }
                IconButton(onClick = { sortMenu = true }) { Icon(Icons.AutoMirrored.Filled.Sort, "Ordenar") }
                DropdownMenu(sortMenu, { sortMenu = false }) {
                    listOf(NoteSort.UPDATED to "Modificação", NoteSort.CREATED to "Criação", NoteSort.TITLE to "Título").forEach { (s, label) ->
                        DropdownMenuItem(text = { Text(label) }, leadingIcon = { RadioButton((sort ?: settings.defaultNoteSort) == s, null) }, onClick = { vm.setSort(s); sortMenu = false })
                    }
                }
            },
        )
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = spacing.screen), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(view == NoteView.All, { vm.view.value = NoteView.All }, { Text("Todas") })
            FilterChip(view == NoteView.Favorites, { vm.view.value = NoteView.Favorites }, { Text("Favoritas") })
            FilterChip(view == NoteView.Archived, { vm.view.value = NoteView.Archived }, { Text("Arquivadas") })
            FilterChip(view == NoteView.Trash, { vm.view.value = NoteView.Trash }, { Text("Lixeira") })
            tags.forEach { t -> FilterChip(view == NoteView.ByTag(t.id), { vm.view.value = NoteView.ByTag(t.id) }, { Text("#${t.name}") }) }
        }
        // Pastas e subpastas do nível atual.
        val visibleFolders = folders.filter { it.parentId == currentFolder }
        if (visibleFolders.isNotEmpty() || currentFolder != null) {
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = spacing.screen, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (currentFolder != null) {
                    val parent = folders.firstOrNull { it.id == currentFolder }?.parentId
                    AssistChip(onClick = { vm.view.value = parent?.let { NoteView.Folder(it) } ?: NoteView.All }, label = { Text("← Voltar") })
                }
                visibleFolders.forEach { f ->
                    Column {
                        Row(
                            Modifier.combinedClickable(onClick = { vm.view.value = NoteView.Folder(f.id) }, onLongClick = { folderMenu = f }),
                        ) { AssistChip(onClick = { vm.view.value = NoteView.Folder(f.id) }, label = { Text(f.name) }, leadingIcon = { Icon(Icons.Outlined.Folder, null) }) }
                        DropdownMenu(folderMenu == f, { folderMenu = null }) {
                            DropdownMenuItem(text = { Text("Renomear") }, onClick = { folderMenu = null; renameFolder = f })
                            DropdownMenuItem(text = { Text("Excluir pasta") }, onClick = { folderMenu = null; deleteFolder = f })
                        }
                    }
                }
                if (currentFolder != null) {
                    folders.firstOrNull { it.id == currentFolder }?.let { cur ->
                        AssistChip(onClick = { folderMenu = cur }, label = { Text("Opções da pasta") })
                        DropdownMenu(folderMenu == cur, { folderMenu = null }) {
                            DropdownMenuItem(text = { Text("Renomear") }, onClick = { folderMenu = null; renameFolder = cur })
                            DropdownMenuItem(text = { Text("Excluir pasta") }, onClick = { folderMenu = null; deleteFolder = cur })
                        }
                    }
                }
            }
        }
        val list = items ?: return@Column
        if (list.isEmpty()) {
            EmptyState(
                Icons.AutoMirrored.Outlined.EventNote,
                if (query.isNotBlank()) "Nada encontrado" else if (view == NoteView.Trash) "Lixeira vazia" else "Nenhuma nota",
                if (query.isNotBlank()) "Tente outros termos." else "Toque em + para escrever uma nota.",
            )
            return@Column
        }
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = spacing.screen), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(list, key = { it.id }) { n ->
                CardSurface(Modifier.animateItem().combinedClickable(onClick = { actions.navigate(Routes.note(n.id)) }, onLongClick = { noteMenu = n })) {
                    Column(Modifier.padding(spacing.card)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (n.pinned) Icon(Icons.Filled.PushPin, "Fixada", Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                            if (n.pinned) Spacer(Modifier.width(4.dp))
                            Text(n.title.ifBlank { "Sem título" }, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (n.favorite) Icon(Icons.Filled.Star, "Favorita", Modifier.size(16.dp), tint = Color(0xFFF2B01E))
                        }
                        if (n.preview.isNotBlank()) {
                            Text(n.preview, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3, overflow = TextOverflow.Ellipsis)
                        }
                        Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            val updated = n.updatedAt.atZone(ZoneId.systemDefault()).toLocalDate()
                            Text(Formats.relativeDate(updated, java.time.LocalDate.now()), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            n.tags.forEach { Text("#${it.name}", style = MaterialTheme.typography.labelSmall, color = Color(it.color)) }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(80.dp)) }
        }
    }

    noteMenu?.let { n ->
        ModalBottomSheet(onDismissRequest = { noteMenu = null }) {
            Text(n.title.ifBlank { "Sem título" }, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
            if (n.deletedAt != null) {
                SheetAction(Icons.Outlined.Restore, "Restaurar") { noteMenu = null; vm.restore(n) }
                SheetAction(Icons.Outlined.DeleteForever, "Excluir permanentemente") { noteMenu = null; deleteForever = n }
            } else {
                SheetAction(Icons.Filled.Star, if (n.favorite) "Remover dos favoritos" else "Favoritar") { noteMenu = null; vm.toggleFavorite(n) }
                SheetAction(Icons.Filled.PushPin, if (n.pinned) "Desafixar" else "Fixar") { noteMenu = null; vm.togglePin(n) }
                SheetAction(Icons.Outlined.Folder, "Mover para pasta") { noteMenu = null; moveNote = n }
                SheetAction(Icons.Outlined.Folder, if (n.archived) "Desarquivar" else "Arquivar") { noteMenu = null; vm.toggleArchive(n) }
                SheetAction(Icons.Outlined.Delete, "Excluir") {
                    noteMenu = null
                    vm.delete(n)
                    actions.showMessage("Nota movida para a lixeira", "Desfazer") { vm.restore(n) }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
    moveNote?.let { n ->
        ModalBottomSheet(onDismissRequest = { moveNote = null }) {
            SheetAction(Icons.Outlined.Folder, "Sem pasta") { vm.move(n, null); moveNote = null }
            folders.forEach { f -> SheetAction(Icons.Outlined.Folder, folderPath(f.id, folders)) { vm.move(n, f.id); moveNote = null } }
            Spacer(Modifier.height(24.dp))
        }
    }
    if (newFolder) {
        TextInputDialog(if (currentFolder != null) "Nova subpasta" else "Nova pasta", onConfirm = { vm.createFolder(it, currentFolder) }, onDismiss = { newFolder = false })
    }
    renameFolder?.let { f -> TextInputDialog("Renomear pasta", f.name, onConfirm = { vm.renameFolder(f, it) }, onDismiss = { renameFolder = null }) }
    deleteFolder?.let { f ->
        ConfirmDialog(
            "Excluir pasta?", "A pasta \"${f.name}\" e suas subpastas serão removidas. As notas NÃO são apagadas: ficam sem pasta.",
            "Excluir", destructive = true,
            onConfirm = { vm.deleteFolder(f); if (currentFolder == f.id) vm.view.value = NoteView.All },
            onDismiss = { deleteFolder = null },
        )
    }
    deleteForever?.let { n ->
        ConfirmDialog("Excluir permanentemente?", "A nota e seus anexos serão apagados definitivamente.", "Excluir", destructive = true, onConfirm = { vm.deleteForever(n) }, onDismiss = { deleteForever = null })
    }
}
