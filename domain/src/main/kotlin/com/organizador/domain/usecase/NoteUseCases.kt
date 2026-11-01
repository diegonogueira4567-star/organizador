package com.organizador.domain.usecase

import com.organizador.domain.model.ActivityType
import com.organizador.domain.model.EntityType
import com.organizador.domain.model.LinkType
import com.organizador.domain.model.SaveResult
import com.organizador.domain.model.TaskDraft
import com.organizador.domain.model.TimeProvider
import com.organizador.domain.note.BlockType
import com.organizador.domain.note.NoteDocumentCodec
import com.organizador.domain.repository.ActivityRepository
import com.organizador.domain.repository.NoteRepository
import com.organizador.domain.repository.TaskRepository
import com.organizador.domain.repository.TransactionRunner
import javax.inject.Inject

/** Transforma a nota inteira em uma tarefa vinculada. A nota original é preservada. */
class ConvertNoteToTaskUseCase @Inject constructor(
    private val notes: NoteRepository,
    private val tasks: TaskRepository,
    private val activity: ActivityRepository,
    private val tx: TransactionRunner,
    private val time: TimeProvider,
) {
    suspend operator fun invoke(noteId: String, projectId: String? = null): String? = tx.inTransaction {
        val note = notes.getNote(noteId) ?: return@inTransaction null
        val title = note.title.ifBlank { note.plainText.lineSequence().firstOrNull { it.isNotBlank() }.orEmpty() }
            .ifBlank { "Tarefa da nota" }
        val taskId = tasks.save(
            TaskDraft(
                title = title.take(200),
                description = note.plainText.take(4000),
                projectId = projectId ?: note.projectId,
                linkedNoteId = note.id,
            ),
        )
        notes.addLink(LinkType.NOTE_TASK, note.id, taskId, time.now())
        activity.log(ActivityType.TASK_CREATED, EntityType.TASK, taskId, title, projectId, time.now())
        taskId
    }
}

/**
 * Transforma itens (checklist/listas) da nota em tarefas. Cada bloco convertido guarda o id da tarefa,
 * mantendo o texto original intacto. Itens já convertidos são ignorados (operação repetível).
 */
class ConvertNoteItemsToTasksUseCase @Inject constructor(
    private val notes: NoteRepository,
    private val tasks: TaskRepository,
    private val tx: TransactionRunner,
    private val time: TimeProvider,
) {
    suspend operator fun invoke(noteId: String, blockIds: Set<String>, projectId: String? = null): Int = tx.inTransaction {
        val note = notes.getNote(noteId) ?: return@inTransaction 0
        var created = 0
        val newBlocks = note.document.blocks.map { block ->
            val convertible = block.id in blockIds && block.taskId == null && block.text.isNotBlank() &&
                block.type in setOf(BlockType.TODO, BlockType.BULLET, BlockType.NUMBERED, BlockType.PARAGRAPH)
            if (!convertible) return@map block
            val taskId = tasks.save(
                TaskDraft(title = block.text.take(200), projectId = projectId ?: note.projectId, linkedNoteId = note.id),
            )
            if (block.type == BlockType.TODO && block.checked) tasks.setCompleted(taskId, true, time.now())
            notes.addLink(LinkType.NOTE_TASK, note.id, taskId, time.now())
            created++
            block.copy(taskId = taskId)
        }
        if (created > 0) {
            val result = notes.saveContent(note.id, note.title, note.document.copy(blocks = newBlocks), note.version, time.now())
            check(result is SaveResult.Saved) { "Falha ao vincular itens convertidos: $result" }
        }
        created
    }
}

class CreateNoteUseCase @Inject constructor(
    private val notes: NoteRepository,
    private val activity: ActivityRepository,
    private val time: TimeProvider,
) {
    suspend operator fun invoke(title: String = "", folderId: String? = null, projectId: String? = null, text: String = ""): String {
        val doc = if (text.isEmpty()) com.organizador.domain.note.NoteDocument.empty() else NoteDocumentCodec.fromPlainText(text)
        val id = notes.create(title, doc, folderId, projectId, time.now())
        activity.log(ActivityType.NOTE_CREATED, EntityType.NOTE, id, title.ifBlank { "Nota sem título" }, projectId, time.now())
        return id
    }
}
