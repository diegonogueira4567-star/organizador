package com.organizador.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RawQuery
import androidx.room.Upsert
import androidx.sqlite.db.SupportSQLiteQuery
import com.organizador.data.local.entity.EntityLinkEntity
import com.organizador.data.local.entity.NoteAttachmentEntity
import com.organizador.data.local.entity.NoteDraftEntity
import com.organizador.data.local.entity.NoteEntity
import com.organizador.data.local.entity.NoteFolderEntity
import com.organizador.data.local.entity.NoteTagEntity
import com.organizador.data.local.entity.TagEntity
import kotlinx.coroutines.flow.Flow

/** Prévia de nota sem carregar o documento completo (economia de memória em listas). */
data class NoteSummaryRow(
    val id: String,
    val title: String,
    val preview: String,
    val folderId: String?,
    val favorite: Boolean,
    val pinned: Boolean,
    val archived: Boolean,
    val deletedAt: Long?,
    val createdAt: Long,
    val updatedAt: Long,
    val tagData: String?,
)

@Dao
interface NoteDao {
    @RawQuery(observedEntities = [NoteEntity::class, NoteTagEntity::class, TagEntity::class])
    fun observeSummaries(query: SupportSQLiteQuery): Flow<List<NoteSummaryRow>>

    @Query("SELECT * FROM notes WHERE id = :id")
    fun observe(id: String): Flow<NoteEntity?>

    @Query("SELECT * FROM notes WHERE id = :id")
    suspend fun get(id: String): NoteEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(note: NoteEntity)

    /** Atualização condicionada à versão (bloqueio otimista). Retorna linhas afetadas. */
    @Query(
        """UPDATE notes SET title = :title, content = :content, plainText = :plainText, version = version + 1, updatedAt = :at
           WHERE id = :id AND version = :baseVersion""",
    )
    suspend fun updateContent(id: String, title: String, content: String, plainText: String, baseVersion: Long, at: Long): Int

    @Query("UPDATE notes SET folderId = :folderId, projectId = :projectId, favorite = :favorite, pinned = :pinned, archived = :archived WHERE id = :id")
    suspend fun updateMeta(id: String, folderId: String?, projectId: String?, favorite: Boolean, pinned: Boolean, archived: Boolean)

    @Query("UPDATE notes SET deletedAt = :deletedAt WHERE id = :id")
    suspend fun setDeleted(id: String, deletedAt: Long?)

    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun delete(id: String)

    // Etiquetas
    @Query("SELECT g.* FROM tags g JOIN note_tags nt ON nt.tagId = g.id WHERE nt.noteId = :noteId ORDER BY g.name")
    fun observeTags(noteId: String): Flow<List<TagEntity>>

    @Query("DELETE FROM note_tags WHERE noteId = :noteId")
    suspend fun clearTags(noteId: String)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTags(tags: List<NoteTagEntity>)

    // Rascunhos
    @Upsert
    suspend fun upsertDraft(draft: NoteDraftEntity)

    @Query("SELECT * FROM note_drafts WHERE noteId = :noteId")
    suspend fun draft(noteId: String): NoteDraftEntity?

    @Query("DELETE FROM note_drafts WHERE noteId = :noteId")
    suspend fun deleteDraft(noteId: String)

    // Pastas
    @Query("SELECT * FROM note_folders ORDER BY sortOrder, name COLLATE NOCASE")
    fun observeFolders(): Flow<List<NoteFolderEntity>>

    @Upsert
    suspend fun upsertFolder(folder: NoteFolderEntity)

    @Query("DELETE FROM note_folders WHERE id = :id")
    suspend fun deleteFolder(id: String)

    // Vínculos
    @Query("SELECT * FROM entity_links WHERE fromId = :id OR toId = :id ORDER BY createdAt")
    fun observeLinks(id: String): Flow<List<EntityLinkEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertLink(link: EntityLinkEntity)

    @Query("DELETE FROM entity_links WHERE id = :id")
    suspend fun deleteLink(id: String)

    @Query("DELETE FROM entity_links WHERE fromId = :id OR toId = :id")
    suspend fun deleteLinksOf(id: String)

    // Anexos
    @Query("SELECT * FROM note_attachments WHERE noteId = :noteId ORDER BY createdAt")
    fun observeAttachments(noteId: String): Flow<List<NoteAttachmentEntity>>

    @Query("SELECT * FROM note_attachments WHERE noteId = :noteId")
    suspend fun attachments(noteId: String): List<NoteAttachmentEntity>

    @Query("SELECT * FROM note_attachments WHERE id = :id")
    suspend fun attachment(id: String): NoteAttachmentEntity?

    @Insert
    suspend fun insertAttachment(attachment: NoteAttachmentEntity)

    @Delete
    suspend fun deleteAttachment(attachment: NoteAttachmentEntity)
}
