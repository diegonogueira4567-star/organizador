package com.organizador.domain.model

import com.organizador.domain.note.NoteDocument
import java.time.Instant

data class Note(
    val id: String,
    val title: String,
    val document: NoteDocument,
    /** Texto puro derivado do documento, usado em pesquisa e prévias. */
    val plainText: String,
    val folderId: String? = null,
    val projectId: String? = null,
    val favorite: Boolean = false,
    val pinned: Boolean = false,
    val archived: Boolean = false,
    val deletedAt: Instant? = null,
    /** Versão otimista: incrementada a cada gravação para detectar conflitos entre editores. */
    val version: Long = 1,
    val createdAt: Instant,
    val updatedAt: Instant,
)

data class NoteSummary(
    val id: String,
    val title: String,
    val preview: String,
    val folderId: String?,
    val favorite: Boolean,
    val pinned: Boolean,
    val archived: Boolean,
    val deletedAt: Instant?,
    val createdAt: Instant,
    val updatedAt: Instant,
    val tags: List<Tag>,
)

data class NoteFolder(
    val id: String,
    val name: String,
    val parentId: String? = null,
    val color: Long = Palette.default,
    val sortOrder: Int = 0,
    val createdAt: Instant,
)

/** Rascunho gravado continuamente pelo editor para recuperação após encerramento inesperado. */
data class NoteDraft(
    val noteId: String,
    val title: String,
    val document: NoteDocument,
    val baseVersion: Long,
    val updatedAt: Instant,
)

enum class NoteSort { UPDATED, CREATED, TITLE }

sealed interface NoteView {
    data object All : NoteView
    data object Favorites : NoteView
    data object Archived : NoteView
    data object Trash : NoteView
    data class Folder(val folderId: String) : NoteView
    data class ByTag(val tagId: String) : NoteView
    data class ByProject(val projectId: String) : NoteView
}

sealed interface SaveResult {
    data class Saved(val version: Long) : SaveResult

    /** Outra alteração foi gravada depois que o editor carregou a nota; o conteúdo local virou uma cópia. */
    data class Conflict(val conflictCopyId: String) : SaveResult
    data object NotFound : SaveResult
}

enum class LinkType { NOTE_TASK, NOTE_NOTE, NOTE_CHECKLIST }

data class EntityLink(val id: String, val type: LinkType, val fromId: String, val toId: String, val createdAt: Instant)
