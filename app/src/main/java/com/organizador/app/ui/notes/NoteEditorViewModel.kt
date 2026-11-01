package com.organizador.app.ui.notes

import android.content.Context
import android.net.Uri
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.organizador.app.di.ApplicationScope
import com.organizador.app.ui.common.queryFileMeta
import com.organizador.domain.model.Attachment
import com.organizador.domain.model.AttachmentOwner
import com.organizador.domain.model.ChecklistSummary
import com.organizador.domain.model.EntityLink
import com.organizador.domain.model.Ids
import com.organizador.domain.model.LinkType
import com.organizador.domain.model.NoteDraft
import com.organizador.domain.model.NoteFolder
import com.organizador.domain.model.NoteSort
import com.organizador.domain.model.NoteSummary
import com.organizador.domain.model.NoteView
import com.organizador.domain.model.Project
import com.organizador.domain.model.SaveResult
import com.organizador.domain.model.Tag
import com.organizador.domain.model.TimeProvider
import com.organizador.domain.note.BlockType
import com.organizador.domain.note.NoteBlock
import com.organizador.domain.note.NoteDocument
import com.organizador.domain.note.SpanEditing
import com.organizador.domain.note.SpanStyle
import com.organizador.domain.note.TextSpan
import com.organizador.domain.repository.AttachmentRepository
import com.organizador.domain.repository.ChecklistRepository
import com.organizador.domain.repository.LabelRepository
import com.organizador.domain.repository.NoteRepository
import com.organizador.domain.repository.ProjectRepository
import com.organizador.domain.usecase.ConvertNoteItemsToTasksUseCase
import com.organizador.domain.usecase.ConvertNoteToTaskUseCase
import com.organizador.domain.usecase.CreateNoteUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

/** Bloco em edição: o texto vive em TextFieldValue (com seleção) e os estilos em spans. */
data class EditorBlock(
    val id: String,
    val type: BlockType,
    val value: TextFieldValue = TextFieldValue(""),
    val spans: List<TextSpan> = emptyList(),
    val checked: Boolean = false,
    val attachmentId: String? = null,
    val rows: List<List<String>>? = null,
    val refId: String? = null,
    val taskId: String? = null,
) {
    fun toBlock() = NoteBlock(id, type, value.text, spans, checked, attachmentId, rows, refId, taskId)
}

fun NoteBlock.toEditor() = EditorBlock(id, type, TextFieldValue(text), spans, checked, attachmentId, rows, refId, taskId)

enum class SaveStatus { SAVED, PENDING, SAVING, ERROR }

data class NoteEditorState(
    val noteId: String,
    val title: String,
    val blocks: List<EditorBlock>,
    val version: Long,
    val folderId: String?,
    val projectId: String?,
    val favorite: Boolean,
    val pinned: Boolean,
    val archived: Boolean,
    val deleted: Boolean,
    val focused: Int = -1,
    val status: SaveStatus = SaveStatus.SAVED,
)

sealed interface NoteEvent {
    data class Message(val text: String) : NoteEvent
    data class Conflict(val copyId: String) : NoteEvent
    data class DraftAvailable(val draft: NoteDraft) : NoteEvent
    data class OpenTask(val taskId: String) : NoteEvent
    data object Closed : NoteEvent
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class NoteEditorViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val notes: NoteRepository,
    private val attachmentsRepo: AttachmentRepository,
    private val checklistsRepo: ChecklistRepository,
    projectsRepo: ProjectRepository,
    labels: LabelRepository,
    private val createNote: CreateNoteUseCase,
    private val convertToTask: ConvertNoteToTaskUseCase,
    private val convertItems: ConvertNoteItemsToTasksUseCase,
    @ApplicationScope private val appScope: CoroutineScope,
    private val time: TimeProvider,
) : ViewModel() {
    val state = MutableStateFlow<NoteEditorState?>(null)
    val events = MutableSharedFlow<NoteEvent>(extraBufferCapacity = 8)
    private val noteIdFlow = MutableStateFlow<String?>(null)

    val attachments: StateFlow<List<Attachment>> = noteIdFlow.filterNotNull().flatMapLatest { attachmentsRepo.observe(AttachmentOwner.NOTE, it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val links: StateFlow<List<EntityLink>> = noteIdFlow.filterNotNull().flatMapLatest { notes.observeLinks(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val noteTags: StateFlow<List<Tag>> = noteIdFlow.filterNotNull().flatMapLatest { notes.observeNoteTags(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val folders: StateFlow<List<NoteFolder>> = notes.observeFolders().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val tags: StateFlow<List<Tag>> = labels.observeTags().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val projects: StateFlow<List<Project>> = projectsRepo.observeProjects().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val checklists: StateFlow<List<ChecklistSummary>> = checklistsRepo.observeSummaries(null).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val allNotes: StateFlow<List<NoteSummary>> = notes.observeNotes(NoteView.All, NoteSort.TITLE).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private var createdHere = false
    private var revision = 0L
    private var savedRevision = 0L
    private var draftJob: Job? = null
    private var saveJob: Job? = null
    private val saveMutex = Mutex()

    fun load(noteId: String?, folderId: String?) {
        if (state.value != null) return
        viewModelScope.launch {
            val id = noteId ?: createNote(folderId = folderId).also { createdHere = true }
            val note = notes.getNote(id)
            if (note == null) {
                events.emit(NoteEvent.Message("Nota não encontrada"))
                events.emit(NoteEvent.Closed)
                return@launch
            }
            state.value = NoteEditorState(
                noteId = note.id, title = note.title, blocks = note.document.blocks.map { it.toEditor() }.ifEmpty { listOf(EditorBlock(Ids.new(), BlockType.PARAGRAPH)) },
                version = note.version, folderId = note.folderId, projectId = note.projectId, favorite = note.favorite,
                pinned = note.pinned, archived = note.archived, deleted = note.deletedAt != null,
                focused = if (createdHere) 0 else -1,
            )
            noteIdFlow.value = note.id
            // Rascunho mais recente que a versão gravada = sessão anterior encerrada sem salvar.
            notes.getDraft(id)?.let { draft ->
                val newer = draft.updatedAt.isAfter(note.updatedAt)
                val differs = draft.title != note.title || draft.document.blocks.map { it.text } != note.document.blocks.map { it.text } ||
                    draft.document != note.document
                if (newer && differs) events.emit(NoteEvent.DraftAvailable(draft)) else notes.deleteDraft(id)
            }
        }
    }

    // ---------- Edição ----------

    private fun edit(transform: (NoteEditorState) -> NoteEditorState) {
        val current = state.value ?: return
        state.value = transform(current).copy(status = SaveStatus.PENDING)
        revision++
        scheduleSave()
    }

    fun focus(index: Int) {
        state.value = state.value?.copy(focused = index)
    }

    fun setTitle(title: String) = edit { it.copy(title = title) }

    fun updateText(index: Int, value: TextFieldValue) {
        val current = state.value ?: return
        val block = current.blocks.getOrNull(index) ?: return
        if (value.text == block.value.text) {
            // Apenas seleção/cursor mudou: não é alteração de conteúdo.
            state.value = current.copy(blocks = current.blocks.replace(index, block.copy(value = value)), focused = index)
            return
        }
        val newline = value.text.indexOf('\n')
        if (newline >= 0 && block.type != BlockType.CODE) {
            splitBlock(index, block, value, newline)
            return
        }
        val spans = SpanEditing.adjust(block.value.text, value.text, block.spans)
        edit { it.copy(blocks = it.blocks.replace(index, block.copy(value = value, spans = spans)), focused = index) }
    }

    /** Enter divide o bloco; listas continuam no mesmo tipo, títulos viram parágrafo, item de lista vazio sai da lista. */
    private fun splitBlock(index: Int, block: EditorBlock, value: TextFieldValue, newline: Int) {
        val before = value.text.substring(0, newline)
        val after = value.text.substring(newline + 1).replace("\n", "")
        val listType = block.type in setOf(BlockType.BULLET, BlockType.NUMBERED, BlockType.TODO, BlockType.QUOTE)
        if (listType && before.isEmpty() && after.isEmpty()) {
            edit { it.copy(blocks = it.blocks.replace(index, block.copy(type = BlockType.PARAGRAPH, value = TextFieldValue(""))), focused = index) }
            return
        }
        val spansBefore = SpanEditing.adjust(block.value.text, before, block.spans)
        val spansAfter = block.spans.mapNotNull { s ->
            val start = (s.start - newline).coerceAtLeast(0)
            val end = (s.end - newline).coerceAtMost(after.length)
            if (s.end > newline && end > start) s.copy(start = start, end = end) else null
        }
        val first = block.copy(value = TextFieldValue(before, TextRange(before.length)), spans = spansBefore)
        val nextType = if (listType) block.type else BlockType.PARAGRAPH
        val second = EditorBlock(Ids.new(), nextType, TextFieldValue(after, TextRange(0)), spansAfter)
        edit { s -> s.copy(blocks = s.blocks.replace(index, first).toMutableList().apply { add(index + 1, second) }, focused = index + 1) }
    }

    /** Backspace no início do bloco: junta com o anterior (ou remove bloco vazio). */
    fun mergeWithPrevious(index: Int): Boolean {
        val current = state.value ?: return false
        if (index <= 0) return false
        val block = current.blocks[index]
        val previous = current.blocks[index - 1]
        if (!previous.type.isTextual) {
            if (block.value.text.isEmpty()) edit { it.copy(blocks = it.blocks.toMutableList().apply { removeAt(index) }, focused = index - 1) }
            return true
        }
        val offset = previous.value.text.length
        val merged = previous.copy(
            value = TextFieldValue(previous.value.text + block.value.text, TextRange(offset)),
            spans = SpanEditing.normalize(previous.spans + block.spans.map { it.copy(start = it.start + offset, end = it.end + offset) }),
        )
        edit { it.copy(blocks = it.blocks.toMutableList().apply { set(index - 1, merged); removeAt(index) }, focused = index - 1) }
        return true
    }

    fun toggleChecked(index: Int) = edit { s -> s.copy(blocks = s.blocks.replace(index, s.blocks[index].let { it.copy(checked = !it.checked) })) }

    fun setType(type: BlockType) {
        val s = state.value ?: return
        val index = s.focused.takeIf { it in s.blocks.indices } ?: return
        val block = s.blocks[index]
        if (!block.type.isTextual) return
        edit { it.copy(blocks = it.blocks.replace(index, block.copy(type = if (block.type == type) BlockType.PARAGRAPH else type))) }
    }

    fun applyStyle(style: SpanStyle, value: String? = null) {
        val s = state.value ?: return
        val index = s.focused.takeIf { it in s.blocks.indices } ?: return
        val block = s.blocks[index]
        val sel = block.value.selection
        if (sel.collapsed) {
            viewModelScope.launch { events.emit(NoteEvent.Message("Selecione um trecho do texto para formatar")) }
            return
        }
        val spans = SpanEditing.toggle(block.spans, sel.min, sel.max, style, value)
        edit { it.copy(blocks = it.blocks.replace(index, block.copy(spans = spans))) }
    }

    /** URL do link sob o cursor, se houver. */
    fun linkAtCursor(): String? {
        val s = state.value ?: return null
        val block = s.blocks.getOrNull(s.focused) ?: return null
        val pos = block.value.selection.start
        return block.spans.firstOrNull { it.style == SpanStyle.LINK && pos in it.start..it.end }?.value
    }

    private fun insertAfterFocus(block: EditorBlock) = edit { s ->
        val at = (s.focused.takeIf { it in s.blocks.indices } ?: s.blocks.lastIndex) + 1
        val list = s.blocks.toMutableList().apply { add(at, block) }
        // Garante um parágrafo editável após blocos não textuais.
        if (!block.type.isTextual && (at == list.lastIndex)) list.add(EditorBlock(Ids.new(), BlockType.PARAGRAPH))
        s.copy(blocks = list, focused = at)
    }

    fun insertDivider() = insertAfterFocus(EditorBlock(Ids.new(), BlockType.DIVIDER))
    fun insertTable() = insertAfterFocus(EditorBlock(Ids.new(), BlockType.TABLE, rows = listOf(listOf("", ""), listOf("", ""))))
    fun insertChecklistRef(id: String) = insertAfterFocus(EditorBlock(Ids.new(), BlockType.CHECKLIST_REF, refId = id))
    fun insertNoteRef(id: String) {
        insertAfterFocus(EditorBlock(Ids.new(), BlockType.NOTE_REF, refId = id))
        val noteId = state.value?.noteId ?: return
        viewModelScope.launch { notes.addLink(LinkType.NOTE_NOTE, noteId, id, time.now()) }
    }

    fun removeBlock(index: Int) = edit { s ->
        val list = s.blocks.toMutableList().apply { removeAt(index) }.ifEmpty { listOf(EditorBlock(Ids.new(), BlockType.PARAGRAPH)) }
        s.copy(blocks = list, focused = (index - 1).coerceAtLeast(0))
    }

    fun editCell(index: Int, row: Int, col: Int, text: String) = edit { s ->
        val block = s.blocks[index]
        val rows = block.rows.orEmpty().map { it.toMutableList() }.toMutableList()
        rows[row][col] = text
        s.copy(blocks = s.blocks.replace(index, block.copy(rows = rows)))
    }

    fun addRow(index: Int) = edit { s ->
        val block = s.blocks[index]
        val cols = block.rows?.firstOrNull()?.size ?: 2
        s.copy(blocks = s.blocks.replace(index, block.copy(rows = block.rows.orEmpty() + listOf(List(cols) { "" }))))
    }

    fun addColumn(index: Int) = edit { s ->
        val block = s.blocks[index]
        s.copy(blocks = s.blocks.replace(index, block.copy(rows = block.rows.orEmpty().map { it + "" })))
    }

    fun removeRow(index: Int) = edit { s ->
        val block = s.blocks[index]
        val rows = block.rows.orEmpty()
        if (rows.size <= 1) s else s.copy(blocks = s.blocks.replace(index, block.copy(rows = rows.dropLast(1))))
    }

    fun addAttachment(uri: Uri, asImage: Boolean) = viewModelScope.launch {
        val s = state.value ?: return@launch
        try {
            val attachment = withContext(Dispatchers.IO) {
                val (name, type) = queryFileMeta(context, uri)
                val stream = context.contentResolver.openInputStream(uri) ?: error("Não foi possível ler o arquivo")
                attachmentsRepo.add(AttachmentOwner.NOTE, s.noteId, name, type, stream, time.now())
            }
            insertAfterFocus(
                EditorBlock(Ids.new(), if (asImage && attachment.isImage) BlockType.IMAGE else BlockType.ATTACHMENT, TextFieldValue(attachment.fileName), attachmentId = attachment.id),
            )
        } catch (e: Exception) {
            events.emit(NoteEvent.Message(e.message ?: "Falha ao anexar"))
        }
    }

    fun attachmentFile(a: Attachment): File = attachmentsRepo.fileFor(a)

    // ---------- Metadados ----------

    private fun meta(transform: (NoteEditorState) -> NoteEditorState) {
        val s = state.value ?: return
        val updated = transform(s)
        state.value = updated
        viewModelScope.launch { notes.updateMeta(updated.noteId, updated.folderId, updated.projectId, updated.favorite, updated.pinned, updated.archived) }
    }

    fun toggleFavorite() = meta { it.copy(favorite = !it.favorite) }
    fun togglePinned() = meta { it.copy(pinned = !it.pinned) }
    fun toggleArchived() = meta { it.copy(archived = !it.archived) }
    fun moveToFolder(folderId: String?) = meta { it.copy(folderId = folderId) }
    fun setProject(projectId: String?) = meta { it.copy(projectId = projectId) }
    fun setTags(ids: Set<String>) = viewModelScope.launch { state.value?.noteId?.let { notes.setTags(it, ids) } }

    fun convertNoteToTask() = viewModelScope.launch {
        val s = state.value ?: return@launch
        flush()
        convertToTask(s.noteId, s.projectId)?.let { events.emit(NoteEvent.OpenTask(it)) }
    }

    fun convertItemsToTasks() = viewModelScope.launch {
        val s = state.value ?: return@launch
        flush()
        val ids = s.blocks.filter { it.type in setOf(BlockType.TODO, BlockType.BULLET, BlockType.NUMBERED) && it.taskId == null }.map { it.id }.toSet()
        if (ids.isEmpty()) {
            events.emit(NoteEvent.Message("Nenhum item de lista para converter"))
            return@launch
        }
        val created = convertItems(s.noteId, ids, s.projectId)
        // Recarrega para refletir os vínculos gravados nos blocos.
        notes.getNote(s.noteId)?.let { note ->
            state.value = state.value?.copy(blocks = note.document.blocks.map { it.toEditor() }, version = note.version, status = SaveStatus.SAVED)
            savedRevision = revision
        }
        events.emit(NoteEvent.Message("$created tarefa(s) criada(s) a partir dos itens"))
    }

    fun delete() = viewModelScope.launch {
        state.value?.noteId?.let { notes.softDelete(it, time.now()) }
        events.emit(NoteEvent.Closed)
    }

    fun restore() = viewModelScope.launch {
        state.value?.noteId?.let { notes.restore(it) }
        state.value = state.value?.copy(deleted = false)
    }

    // ---------- Rascunho e salvamento ----------

    fun recoverDraft(draft: NoteDraft) = edit { it.copy(title = draft.title, blocks = draft.document.blocks.map { b -> b.toEditor() }) }

    fun discardDraft() = viewModelScope.launch { state.value?.noteId?.let { notes.deleteDraft(it) } }

    private fun document(s: NoteEditorState) = NoteDocument(blocks = s.blocks.map { it.toBlock() })

    private fun scheduleSave() {
        draftJob?.cancel()
        draftJob = viewModelScope.launch {
            delay(DRAFT_DEBOUNCE_MS)
            val s = state.value ?: return@launch
            notes.saveDraft(NoteDraft(s.noteId, s.title, document(s), s.version, time.now()))
        }
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(SAVE_DEBOUNCE_MS)
            flush()
        }
    }

    /** Grava imediatamente se houver alterações pendentes. Seguro para chamadas concorrentes. */
    suspend fun flush() = saveMutex.withLock {
        val s = state.value ?: return@withLock
        if (savedRevision == revision) return@withLock
        val target = revision
        state.value = state.value?.copy(status = SaveStatus.SAVING)
        when (val result = notes.saveContent(s.noteId, s.title, document(s), s.version, time.now())) {
            is SaveResult.Saved -> {
                savedRevision = target
                val stillSame = revision == target
                state.value = state.value?.copy(version = result.version, status = if (stillSame) SaveStatus.SAVED else SaveStatus.PENDING)
                if (stillSame) notes.deleteDraft(s.noteId)
            }
            is SaveResult.Conflict -> {
                // O conteúdo local foi preservado na cópia; o editor volta à versão gravada.
                savedRevision = revision
                notes.getNote(s.noteId)?.let { note ->
                    state.value = state.value?.copy(
                        title = note.title, blocks = note.document.blocks.map { it.toEditor() }, version = note.version, status = SaveStatus.SAVED,
                    )
                }
                notes.deleteDraft(s.noteId)
                events.emit(NoteEvent.Conflict(result.conflictCopyId))
            }
            SaveResult.NotFound -> {
                state.value = state.value?.copy(status = SaveStatus.ERROR)
                events.emit(NoteEvent.Message("A nota foi excluída permanentemente; o conteúdo continua no rascunho."))
            }
        }
    }

    override fun onCleared() {
        val s = state.value
        val pending = revision != savedRevision
        val wasCreatedHere = createdHere
        // A gravação final roda fora do escopo da tela, que já está sendo destruído.
        appScope.launch {
            if (pending) flush()
            if (s != null && wasCreatedHere && s.title.isBlank() && s.blocks.all { it.value.text.isBlank() && it.type.isTextual }) {
                notes.deletePermanently(s.noteId) // nota nova abandonada vazia não polui a lista
            }
        }
        super.onCleared()
    }

    companion object {
        const val DRAFT_DEBOUNCE_MS = 400L
        const val SAVE_DEBOUNCE_MS = 1500L
    }
}

private fun <T> List<T>.replace(index: Int, value: T): List<T> = toMutableList().also { it[index] = value }
