package com.organizador.data.repository

import androidx.room.withTransaction
import com.organizador.data.local.NoteQueryBuilder
import com.organizador.data.local.OrganizadorDatabase
import com.organizador.data.local.dao.NoteSummaryRow
import com.organizador.data.local.entity.EntityLinkEntity
import com.organizador.data.local.entity.NoteDraftEntity
import com.organizador.data.local.entity.NoteEntity
import com.organizador.data.local.entity.NoteTagEntity
import com.organizador.data.mapper.toDomain
import com.organizador.data.mapper.toEntity
import com.organizador.data.storage.AttachmentStorage
import com.organizador.domain.model.ActivityType
import com.organizador.domain.model.EntityLink
import com.organizador.domain.model.EntityType
import com.organizador.domain.model.Ids
import com.organizador.domain.model.LinkType
import com.organizador.domain.model.Note
import com.organizador.domain.model.NoteDraft
import com.organizador.domain.model.NoteFolder
import com.organizador.domain.model.NoteSort
import com.organizador.domain.model.NoteSummary
import com.organizador.domain.model.NoteView
import com.organizador.domain.model.SaveResult
import com.organizador.domain.model.Tag
import com.organizador.domain.model.TimeProvider
import com.organizador.domain.note.NoteDocument
import com.organizador.domain.note.NoteDocumentCodec
import com.organizador.domain.repository.ActivityRepository
import com.organizador.domain.repository.NoteRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NoteRepositoryImpl @Inject constructor(
    private val db: OrganizadorDatabase,
    private val storage: AttachmentStorage,
    private val activity: ActivityRepository,
    private val time: TimeProvider,
) : NoteRepository {
    private val dao = db.noteDao()

    override fun observeNotes(view: NoteView, sort: NoteSort, query: String): Flow<List<NoteSummary>> =
        dao.observeSummaries(NoteQueryBuilder.build(view, sort, query).toRoom()).map { rows -> rows.map { it.toDomain() } }

    override fun observeRecent(limit: Int): Flow<List<NoteSummary>> =
        dao.observeSummaries(NoteQueryBuilder.build(NoteView.All, NoteSort.UPDATED, "", limit).toRoom()).map { rows -> rows.map { it.toDomain() } }

    override fun observeNote(id: String): Flow<Note?> = dao.observe(id).map { it?.toDomain() }
    override fun observeFolders(): Flow<List<NoteFolder>> = dao.observeFolders().map { l -> l.map { it.toDomain() } }
    override fun observeNoteTags(noteId: String): Flow<List<Tag>> = dao.observeTags(noteId).map { l -> l.map { it.toDomain() } }
    override fun observeLinks(noteId: String): Flow<List<EntityLink>> = dao.observeLinks(noteId).map { l -> l.map { it.toDomain() } }

    override suspend fun getNote(id: String): Note? = dao.get(id)?.toDomain()

    override suspend fun create(title: String, document: NoteDocument, folderId: String?, projectId: String?, now: Instant): String {
        val id = Ids.new()
        dao.insert(
            NoteEntity(
                id = id, title = title.trim(), content = NoteDocumentCodec.encode(document),
                plainText = NoteDocumentCodec.plainText(document), folderId = folderId, projectId = projectId,
                favorite = false, pinned = false, archived = false, deletedAt = null, version = 1,
                createdAt = now.toEpochMilli(), updatedAt = now.toEpochMilli(),
            ),
        )
        return id
    }

    override suspend fun saveContent(id: String, title: String, document: NoteDocument, baseVersion: Long, now: Instant): SaveResult =
        db.withTransaction {
            val updated = dao.updateContent(
                id, title.trim(), NoteDocumentCodec.encode(document), NoteDocumentCodec.plainText(document), baseVersion, now.toEpochMilli(),
            )
            if (updated == 1) return@withTransaction SaveResult.Saved(baseVersion + 1)
            val current = dao.get(id) ?: return@withTransaction SaveResult.NotFound
            // Conflito: a versão gravada mudou desde que o editor abriu. Nada é sobrescrito;
            // o conteúdo local é preservado em uma cópia para o usuário comparar.
            val copyId = create("${title.ifBlank { "Nota" }} (conflito)", document, current.folderId, current.projectId, now)
            activity.log(ActivityType.NOTE_CREATED, EntityType.NOTE, copyId, "Cópia de conflito", current.projectId, now)
            SaveResult.Conflict(copyId)
        }

    override suspend fun updateMeta(id: String, folderId: String?, projectId: String?, favorite: Boolean, pinned: Boolean, archived: Boolean) =
        dao.updateMeta(id, folderId, projectId, favorite, pinned, archived)

    override suspend fun setTags(noteId: String, tagIds: Set<String>) = db.withTransaction {
        dao.clearTags(noteId)
        if (tagIds.isNotEmpty()) dao.insertTags(tagIds.map { NoteTagEntity(noteId, it) })
    }

    override suspend fun softDelete(id: String, at: Instant) {
        dao.setDeleted(id, at.toEpochMilli())
        dao.get(id)?.let { activity.log(ActivityType.NOTE_DELETED, EntityType.NOTE, id, it.title.ifBlank { "Nota" }, it.projectId, at) }
    }

    override suspend fun restore(id: String) = dao.setDeleted(id, null)

    override suspend fun deletePermanently(id: String) {
        val files = dao.attachments(id)
        db.withTransaction {
            dao.deleteLinksOf(id)
            dao.delete(id)
        }
        files.forEach { storage.delete(it.relativePath) }
    }

    override suspend fun saveDraft(draft: NoteDraft) = dao.upsertDraft(
        NoteDraftEntity(draft.noteId, draft.title, NoteDocumentCodec.encode(draft.document), draft.baseVersion, draft.updatedAt.toEpochMilli()),
    )

    override suspend fun getDraft(noteId: String): NoteDraft? = dao.draft(noteId)?.let {
        NoteDraft(it.noteId, it.title, NoteDocumentCodec.decode(it.content), it.baseVersion, Instant.ofEpochMilli(it.updatedAt))
    }

    override suspend fun deleteDraft(noteId: String) = dao.deleteDraft(noteId)

    override suspend fun saveFolder(folder: NoteFolder) {
        require(folder.parentId != folder.id) { "Pasta não pode ser filha de si mesma" }
        dao.upsertFolder(folder.toEntity())
    }

    /** Notas da pasta (e subpastas) voltam para a raiz; nada é apagado. */
    override suspend fun deleteFolder(id: String) = dao.deleteFolder(id)

    override suspend fun addLink(type: LinkType, fromId: String, toId: String, now: Instant) =
        dao.insertLink(EntityLinkEntity(Ids.new(), type.name, fromId, toId, now.toEpochMilli()))

    override suspend fun removeLink(linkId: String) = dao.deleteLink(linkId)
}

private fun NoteSummaryRow.toDomain() = NoteSummary(
    id = id, title = title, preview = preview, folderId = folderId, favorite = favorite, pinned = pinned, archived = archived,
    deletedAt = deletedAt?.let(Instant::ofEpochMilli), createdAt = Instant.ofEpochMilli(createdAt),
    updatedAt = Instant.ofEpochMilli(updatedAt), tags = parseTags(tagData),
)
