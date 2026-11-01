package com.organizador.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import com.organizador.data.local.entity.ActivityEntity
import com.organizador.data.local.entity.CategoryEntity
import com.organizador.data.local.entity.ProjectEntity
import com.organizador.data.local.entity.RecurrenceRuleEntity
import com.organizador.data.local.entity.ReminderEntity
import com.organizador.data.local.entity.TagEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProjectDao {
    @Query("SELECT * FROM projects WHERE archived = 0 OR :includeArchived ORDER BY archived, sortOrder, name COLLATE NOCASE")
    fun observeAll(includeArchived: Boolean): Flow<List<ProjectEntity>>

    @Query("SELECT * FROM projects WHERE id = :id")
    fun observe(id: String): Flow<ProjectEntity?>

    @Query("SELECT * FROM projects WHERE id = :id")
    suspend fun get(id: String): ProjectEntity?

    @Upsert
    suspend fun upsert(project: ProjectEntity)

    @Query("UPDATE projects SET archived = :archived, updatedAt = :at WHERE id = :id")
    suspend fun setArchived(id: String, archived: Boolean, at: Long)

    @Query("UPDATE projects SET sortOrder = :order WHERE id = :id")
    suspend fun setOrder(id: String, order: Int)

    @Query("DELETE FROM projects WHERE id = :id")
    suspend fun delete(id: String)

    @Query("SELECT COALESCE(MAX(sortOrder), 0) FROM projects")
    suspend fun maxOrder(): Int
}

@Dao
interface LabelDao {
    @Query("SELECT * FROM categories ORDER BY sortOrder, name COLLATE NOCASE")
    fun observeCategories(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun category(id: String): CategoryEntity?

    @Upsert
    suspend fun upsertCategory(category: CategoryEntity)

    @Query("DELETE FROM categories WHERE id = :id")
    suspend fun deleteCategory(id: String)

    @Query("UPDATE categories SET sortOrder = :order WHERE id = :id")
    suspend fun setCategoryOrder(id: String, order: Int)

    @Query("SELECT * FROM tags ORDER BY name COLLATE NOCASE")
    fun observeTags(): Flow<List<TagEntity>>

    @Query("SELECT * FROM tags WHERE name = :name COLLATE NOCASE LIMIT 1")
    suspend fun tagByName(name: String): TagEntity?

    @Upsert
    suspend fun upsertTag(tag: TagEntity)

    @Query("DELETE FROM tags WHERE id = :id")
    suspend fun deleteTag(id: String)
}

@Dao
interface RecurrenceDao {
    @Query("SELECT * FROM recurrence_rules WHERE id = :id")
    suspend fun get(id: String): RecurrenceRuleEntity?

    @Query("SELECT * FROM recurrence_rules WHERE id = :id")
    fun observe(id: String): Flow<RecurrenceRuleEntity?>

    @Query("SELECT * FROM recurrence_rules")
    fun observeAll(): Flow<List<RecurrenceRuleEntity>>

    @Upsert
    suspend fun upsert(rule: RecurrenceRuleEntity)

    @Query("DELETE FROM recurrence_rules WHERE id = :id")
    suspend fun delete(id: String)

    /** Remove regras não referenciadas por nenhuma tarefa ou checklist. */
    @Query(
        """DELETE FROM recurrence_rules WHERE id = :id
           AND NOT EXISTS (SELECT 1 FROM tasks WHERE recurrenceRuleId = :id)
           AND NOT EXISTS (SELECT 1 FROM checklist_templates WHERE recurrenceRuleId = :id)""",
    )
    suspend fun deleteIfOrphan(id: String)
}

@Dao
interface ReminderDao {
    @Query("SELECT * FROM reminders WHERE taskId = :ownerId OR checklistId = :ownerId ORDER BY triggerAt")
    fun observeFor(ownerId: String): Flow<List<ReminderEntity>>

    @Query("SELECT * FROM reminders WHERE taskId = :ownerId OR checklistId = :ownerId ORDER BY triggerAt")
    suspend fun forOwner(ownerId: String): List<ReminderEntity>

    @Query("SELECT * FROM reminders WHERE fired = 0 ORDER BY triggerAt")
    suspend fun pending(): List<ReminderEntity>

    @Query("SELECT * FROM reminders WHERE id = :id")
    suspend fun get(id: String): ReminderEntity?

    @Upsert
    suspend fun upsert(reminder: ReminderEntity)

    @Query("UPDATE reminders SET fired = 1 WHERE id = :id")
    suspend fun markFired(id: String)

    @Query("UPDATE reminders SET fired = 0, snoozedUntil = :until WHERE id = :id")
    suspend fun snooze(id: String, until: Long)

    @Query("DELETE FROM reminders WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM reminders WHERE taskId = :ownerId OR checklistId = :ownerId")
    suspend fun deleteForOwner(ownerId: String)
}

@Dao
interface ActivityDao {
    @Query("SELECT * FROM activity_history ORDER BY createdAt DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<ActivityEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entry: ActivityEntity)
}
