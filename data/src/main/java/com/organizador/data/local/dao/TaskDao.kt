package com.organizador.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RawQuery
import androidx.room.Upsert
import androidx.sqlite.db.SupportSQLiteQuery
import com.organizador.data.local.entity.ProjectEntity
import com.organizador.data.local.entity.ReminderEntity
import com.organizador.data.local.entity.SubtaskEntity
import com.organizador.data.local.entity.TagEntity
import com.organizador.data.local.entity.TaskAttachmentEntity
import com.organizador.data.local.entity.TaskEntity
import com.organizador.data.local.entity.TaskTagEntity
import kotlinx.coroutines.flow.Flow

/** Linha de listagem: tarefa + agregados calculados no próprio SQL (sem N+1 consultas). */
data class TaskRow(
    @Embedded val task: TaskEntity,
    val projectName: String?,
    val projectColor: Long?,
    val subtaskTotal: Int,
    val subtaskDone: Int,
    val hasReminder: Boolean,
    /** Etiquetas codificadas: id \u001F nome \u001F cor, separadas por \u001E. */
    val tagData: String?,
)

data class TaskCountsRow(val inbox: Int, val today: Int, val overdue: Int, val upcoming: Int, val trash: Int)

@Dao
interface TaskDao {
    @RawQuery(observedEntities = [TaskEntity::class, SubtaskEntity::class, TaskTagEntity::class, TagEntity::class, ProjectEntity::class, ReminderEntity::class])
    fun observeRows(query: SupportSQLiteQuery): Flow<List<TaskRow>>

    @RawQuery
    suspend fun rows(query: SupportSQLiteQuery): List<TaskRow>

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun get(id: String): TaskEntity?

    @Query("SELECT * FROM tasks WHERE id = :id")
    fun observe(id: String): Flow<TaskEntity?>

    @Query("SELECT * FROM tasks WHERE status = 'OPEN' AND deletedAt IS NULL AND archived = 0 AND dueDate IS NOT NULL")
    suspend fun openWithDueDate(): List<TaskEntity>

    @Query(
        """SELECT * FROM tasks WHERE status = 'OPEN' AND deletedAt IS NULL AND archived = 0
           AND dueDate IS NOT NULL AND recurrenceRuleId IS NOT NULL""",
    )
    suspend fun recurringOpen(): List<TaskEntity>

    @Upsert
    suspend fun upsert(task: TaskEntity)

    @Query("UPDATE tasks SET status = :status, completedAt = :completedAt, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setStatus(id: String, status: String, completedAt: Long?, updatedAt: Long)

    @Query("UPDATE tasks SET projectId = :projectId, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setProject(id: String, projectId: String?, updatedAt: Long)

    @Query("UPDATE tasks SET archived = :archived, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setArchived(id: String, archived: Boolean, updatedAt: Long)

    @Query("UPDATE tasks SET deletedAt = :deletedAt, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setDeleted(id: String, deletedAt: Long?, updatedAt: Long)

    @Query("UPDATE tasks SET sortOrder = :sortOrder WHERE id = :id")
    suspend fun setSortOrder(id: String, sortOrder: Long)

    @Query("DELETE FROM tasks WHERE id = :id")
    suspend fun delete(id: String)

    @Query("SELECT id FROM tasks WHERE deletedAt IS NOT NULL")
    suspend fun trashedIds(): List<String>

    @Query("SELECT COALESCE(MAX(sortOrder), 0) FROM tasks")
    suspend fun maxSortOrder(): Long

    @Query(
        """
        SELECT
          (SELECT COUNT(*) FROM tasks WHERE deletedAt IS NULL AND archived = 0 AND status = 'OPEN' AND projectId IS NULL) AS inbox,
          (SELECT COUNT(*) FROM tasks WHERE deletedAt IS NULL AND archived = 0 AND status = 'OPEN' AND dueDate <= :today) AS today,
          (SELECT COUNT(*) FROM tasks WHERE deletedAt IS NULL AND archived = 0 AND status = 'OPEN' AND dueDate < :today) AS overdue,
          (SELECT COUNT(*) FROM tasks WHERE deletedAt IS NULL AND archived = 0 AND status = 'OPEN' AND dueDate > :today AND dueDate <= :today + 7) AS upcoming,
          (SELECT COUNT(*) FROM tasks WHERE deletedAt IS NOT NULL) AS trash
        """,
    )
    fun observeCounts(today: Long): Flow<TaskCountsRow>

    // Subtarefas
    @Query("SELECT * FROM subtasks WHERE taskId = :taskId ORDER BY sortOrder, createdAt")
    suspend fun subtasks(taskId: String): List<SubtaskEntity>

    @Query("SELECT * FROM subtasks WHERE taskId = :taskId ORDER BY sortOrder, createdAt")
    fun observeSubtasks(taskId: String): Flow<List<SubtaskEntity>>

    @Upsert
    suspend fun upsertSubtask(subtask: SubtaskEntity)

    @Query("SELECT * FROM subtasks WHERE id = :id")
    suspend fun subtask(id: String): SubtaskEntity?

    @Query("DELETE FROM subtasks WHERE id = :id")
    suspend fun deleteSubtask(id: String)

    @Query("DELETE FROM subtasks WHERE taskId = :taskId AND id NOT IN (:keep)")
    suspend fun deleteSubtasksExcept(taskId: String, keep: List<String>)

    // Etiquetas
    @Query("SELECT g.* FROM tags g JOIN task_tags tt ON tt.tagId = g.id WHERE tt.taskId = :taskId ORDER BY g.name")
    suspend fun tags(taskId: String): List<TagEntity>

    @Query("SELECT g.* FROM tags g JOIN task_tags tt ON tt.tagId = g.id WHERE tt.taskId = :taskId ORDER BY g.name")
    fun observeTags(taskId: String): Flow<List<TagEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTaskTags(tags: List<TaskTagEntity>)

    @Query("DELETE FROM task_tags WHERE taskId = :taskId")
    suspend fun clearTags(taskId: String)

    // Anexos
    @Query("SELECT * FROM task_attachments WHERE taskId = :taskId ORDER BY createdAt")
    fun observeAttachments(taskId: String): Flow<List<TaskAttachmentEntity>>

    @Query("SELECT * FROM task_attachments WHERE taskId = :taskId")
    suspend fun attachments(taskId: String): List<TaskAttachmentEntity>

    @Query("SELECT * FROM task_attachments WHERE id = :id")
    suspend fun attachment(id: String): TaskAttachmentEntity?

    @Insert
    suspend fun insertAttachment(attachment: TaskAttachmentEntity)

    @Delete
    suspend fun deleteAttachment(attachment: TaskAttachmentEntity)
}
