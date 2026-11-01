package com.organizador.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.organizador.data.local.entity.ActivityEntity
import com.organizador.data.local.entity.CategoryEntity
import com.organizador.data.local.entity.ChecklistItemEntity
import com.organizador.data.local.entity.ChecklistItemStateEntity
import com.organizador.data.local.entity.ChecklistOccurrenceEntity
import com.organizador.data.local.entity.ChecklistTemplateEntity
import com.organizador.data.local.entity.EntityLinkEntity
import com.organizador.data.local.entity.NoteAttachmentEntity
import com.organizador.data.local.entity.NoteDraftEntity
import com.organizador.data.local.entity.NoteEntity
import com.organizador.data.local.entity.NoteFolderEntity
import com.organizador.data.local.entity.NoteTagEntity
import com.organizador.data.local.entity.ProjectEntity
import com.organizador.data.local.entity.RecurrenceRuleEntity
import com.organizador.data.local.entity.ReminderEntity
import com.organizador.data.local.entity.SubtaskEntity
import com.organizador.data.local.entity.TagEntity
import com.organizador.data.local.entity.TaskAttachmentEntity
import com.organizador.data.local.entity.TaskEntity
import com.organizador.data.local.entity.TaskTagEntity

/** Leitura e escrita em massa usadas exclusivamente por backup/restauração. */
@Dao
interface BackupDao {
    @Query("SELECT * FROM projects") suspend fun projects(): List<ProjectEntity>
    @Query("SELECT * FROM categories") suspend fun categories(): List<CategoryEntity>
    @Query("SELECT * FROM tags") suspend fun tags(): List<TagEntity>
    @Query("SELECT * FROM recurrence_rules") suspend fun rules(): List<RecurrenceRuleEntity>
    @Query("SELECT * FROM tasks") suspend fun tasks(): List<TaskEntity>
    @Query("SELECT * FROM subtasks") suspend fun subtasks(): List<SubtaskEntity>
    @Query("SELECT * FROM task_tags") suspend fun taskTags(): List<TaskTagEntity>
    @Query("SELECT * FROM task_attachments") suspend fun taskAttachments(): List<TaskAttachmentEntity>
    @Query("SELECT * FROM note_folders") suspend fun noteFolders(): List<NoteFolderEntity>
    @Query("SELECT * FROM notes") suspend fun notes(): List<NoteEntity>
    @Query("SELECT * FROM note_tags") suspend fun noteTags(): List<NoteTagEntity>
    @Query("SELECT * FROM note_attachments") suspend fun noteAttachments(): List<NoteAttachmentEntity>
    @Query("SELECT * FROM note_drafts") suspend fun noteDrafts(): List<NoteDraftEntity>
    @Query("SELECT * FROM entity_links") suspend fun links(): List<EntityLinkEntity>
    @Query("SELECT * FROM checklist_templates") suspend fun templates(): List<ChecklistTemplateEntity>
    @Query("SELECT * FROM checklist_items") suspend fun items(): List<ChecklistItemEntity>
    @Query("SELECT * FROM checklist_occurrences") suspend fun occurrences(): List<ChecklistOccurrenceEntity>
    @Query("SELECT * FROM checklist_item_states") suspend fun states(): List<ChecklistItemStateEntity>
    @Query("SELECT * FROM reminders") suspend fun reminders(): List<ReminderEntity>
    @Query("SELECT * FROM activity_history") suspend fun activity(): List<ActivityEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertProjects(v: List<ProjectEntity>)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertCategories(v: List<CategoryEntity>)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertTags(v: List<TagEntity>)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertRules(v: List<RecurrenceRuleEntity>)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertTasks(v: List<TaskEntity>)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertSubtasks(v: List<SubtaskEntity>)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertTaskTags(v: List<TaskTagEntity>)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertTaskAttachments(v: List<TaskAttachmentEntity>)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertNoteFolders(v: List<NoteFolderEntity>)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertNotes(v: List<NoteEntity>)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertNoteTags(v: List<NoteTagEntity>)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertNoteAttachments(v: List<NoteAttachmentEntity>)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertNoteDrafts(v: List<NoteDraftEntity>)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertLinks(v: List<EntityLinkEntity>)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertTemplates(v: List<ChecklistTemplateEntity>)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertItems(v: List<ChecklistItemEntity>)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertOccurrences(v: List<ChecklistOccurrenceEntity>)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertStates(v: List<ChecklistItemStateEntity>)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertReminders(v: List<ReminderEntity>)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertActivity(v: List<ActivityEntity>)

    /** Ordem respeita as chaves estrangeiras (filhos antes dos pais). */
    @Query("DELETE FROM activity_history") suspend fun clearActivity()
    @Query("DELETE FROM reminders") suspend fun clearReminders()
    @Query("DELETE FROM checklist_item_states") suspend fun clearStates()
    @Query("DELETE FROM checklist_occurrences") suspend fun clearOccurrences()
    @Query("DELETE FROM checklist_items") suspend fun clearItems()
    @Query("DELETE FROM checklist_templates") suspend fun clearTemplates()
    @Query("DELETE FROM entity_links") suspend fun clearLinks()
    @Query("DELETE FROM note_drafts") suspend fun clearDrafts()
    @Query("DELETE FROM note_attachments") suspend fun clearNoteAttachments()
    @Query("DELETE FROM note_tags") suspend fun clearNoteTags()
    @Query("DELETE FROM notes") suspend fun clearNotes()
    @Query("DELETE FROM note_folders") suspend fun clearNoteFolders()
    @Query("DELETE FROM task_attachments") suspend fun clearTaskAttachments()
    @Query("DELETE FROM task_tags") suspend fun clearTaskTags()
    @Query("DELETE FROM subtasks") suspend fun clearSubtasks()
    @Query("DELETE FROM tasks") suspend fun clearTasks()
    @Query("DELETE FROM recurrence_rules") suspend fun clearRules()
    @Query("DELETE FROM tags") suspend fun clearTags()
    @Query("DELETE FROM categories") suspend fun clearCategories()
    @Query("DELETE FROM projects") suspend fun clearProjects()
}
