package com.organizador.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.ForeignKey.Companion.CASCADE
import androidx.room.ForeignKey.Companion.SET_NULL
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/*
 * Convenções de armazenamento:
 * - Instantes: epoch millis (UTC). Datas locais: epochDay. Horários: minuto do dia.
 * - Ids: UUID em texto, estáveis entre backup/restauração.
 * - Exclusões do usuário são lógicas (deletedAt); remoção física só na lixeira, explicitamente.
 */

@Entity(tableName = "projects", indices = [Index("archived"), Index("sortOrder")])
@Serializable
data class ProjectEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String,
    val color: Long,
    val icon: String,
    val sortOrder: Int,
    val archived: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(tableName = "categories", indices = [Index("sortOrder")])
@Serializable
data class CategoryEntity(
    @PrimaryKey val id: String,
    val name: String,
    val color: Long,
    val sortOrder: Int,
    val createdAt: Long,
)

@Entity(tableName = "tags", indices = [Index(value = ["name"], unique = true)])
@Serializable
data class TagEntity(
    @PrimaryKey val id: String,
    val name: String,
    val color: Long,
    val createdAt: Long,
)

@Entity(tableName = "recurrence_rules")
@Serializable
data class RecurrenceRuleEntity(
    @PrimaryKey val id: String,
    val frequency: String,
    val interval: Int,
    /** ISO 1..7 separados por vírgula. */
    val daysOfWeek: String,
    /** 1..31 ou -1 separados por vírgula. */
    val daysOfMonth: String,
    val monthOfYear: Int?,
    val dayOfMonth: Int?,
    val startDate: Long,
    val endDate: Long?,
    val timeOfDay: Int?,
    val resetTime: Int?,
    val zoneId: String?,
    val enabled: Boolean,
)

@Entity(
    tableName = "tasks",
    foreignKeys = [
        ForeignKey(entity = ProjectEntity::class, parentColumns = ["id"], childColumns = ["projectId"], onDelete = SET_NULL),
        ForeignKey(entity = CategoryEntity::class, parentColumns = ["id"], childColumns = ["categoryId"], onDelete = SET_NULL),
        ForeignKey(entity = RecurrenceRuleEntity::class, parentColumns = ["id"], childColumns = ["recurrenceRuleId"], onDelete = SET_NULL),
        ForeignKey(entity = NoteEntity::class, parentColumns = ["id"], childColumns = ["linkedNoteId"], onDelete = SET_NULL),
    ],
    indices = [
        Index("projectId"), Index("categoryId"), Index("recurrenceRuleId"), Index("linkedNoteId"),
        Index(value = ["status", "dueDate"]), Index("deletedAt"), Index("completedAt"),
    ],
)
@Serializable
data class TaskEntity(
    @PrimaryKey val id: String,
    val title: String,
    val description: String,
    val notes: String,
    val dueDate: Long?,
    val dueTime: Int?,
    val priority: Int,
    val projectId: String?,
    val categoryId: String?,
    val status: String,
    val archived: Boolean,
    val deletedAt: Long?,
    val recurrenceRuleId: String?,
    val linkedNoteId: String?,
    val sortOrder: Long,
    val createdAt: Long,
    val updatedAt: Long,
    val completedAt: Long?,
)

@Entity(
    tableName = "subtasks",
    foreignKeys = [ForeignKey(entity = TaskEntity::class, parentColumns = ["id"], childColumns = ["taskId"], onDelete = CASCADE)],
    indices = [Index("taskId")],
)
@Serializable
data class SubtaskEntity(
    @PrimaryKey val id: String,
    val taskId: String,
    val title: String,
    val done: Boolean,
    val sortOrder: Int,
    val createdAt: Long,
    val completedAt: Long?,
)

@Entity(
    tableName = "task_tags",
    primaryKeys = ["taskId", "tagId"],
    foreignKeys = [
        ForeignKey(entity = TaskEntity::class, parentColumns = ["id"], childColumns = ["taskId"], onDelete = CASCADE),
        ForeignKey(entity = TagEntity::class, parentColumns = ["id"], childColumns = ["tagId"], onDelete = CASCADE),
    ],
    indices = [Index("tagId")],
)
@Serializable
data class TaskTagEntity(val taskId: String, val tagId: String)

@Entity(
    tableName = "task_attachments",
    foreignKeys = [ForeignKey(entity = TaskEntity::class, parentColumns = ["id"], childColumns = ["taskId"], onDelete = CASCADE)],
    indices = [Index("taskId")],
)
@Serializable
data class TaskAttachmentEntity(
    @PrimaryKey val id: String,
    val taskId: String,
    val fileName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val relativePath: String,
    val createdAt: Long,
)

@Entity(
    tableName = "note_folders",
    foreignKeys = [ForeignKey(entity = NoteFolderEntity::class, parentColumns = ["id"], childColumns = ["parentId"], onDelete = CASCADE)],
    indices = [Index("parentId")],
)
@Serializable
data class NoteFolderEntity(
    @PrimaryKey val id: String,
    val name: String,
    val parentId: String?,
    val color: Long,
    val sortOrder: Int,
    val createdAt: Long,
)

@Entity(
    tableName = "notes",
    foreignKeys = [
        ForeignKey(entity = NoteFolderEntity::class, parentColumns = ["id"], childColumns = ["folderId"], onDelete = SET_NULL),
        ForeignKey(entity = ProjectEntity::class, parentColumns = ["id"], childColumns = ["projectId"], onDelete = SET_NULL),
    ],
    indices = [Index("folderId"), Index("projectId"), Index("updatedAt"), Index("deletedAt")],
)
@Serializable
data class NoteEntity(
    @PrimaryKey val id: String,
    val title: String,
    /** Documento de blocos em JSON versionado (NoteDocumentCodec). */
    val content: String,
    val plainText: String,
    val folderId: String?,
    val projectId: String?,
    val favorite: Boolean,
    val pinned: Boolean,
    val archived: Boolean,
    val deletedAt: Long?,
    val version: Long,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "note_tags",
    primaryKeys = ["noteId", "tagId"],
    foreignKeys = [
        ForeignKey(entity = NoteEntity::class, parentColumns = ["id"], childColumns = ["noteId"], onDelete = CASCADE),
        ForeignKey(entity = TagEntity::class, parentColumns = ["id"], childColumns = ["tagId"], onDelete = CASCADE),
    ],
    indices = [Index("tagId")],
)
@Serializable
data class NoteTagEntity(val noteId: String, val tagId: String)

@Entity(
    tableName = "note_attachments",
    foreignKeys = [ForeignKey(entity = NoteEntity::class, parentColumns = ["id"], childColumns = ["noteId"], onDelete = CASCADE)],
    indices = [Index("noteId")],
)
@Serializable
data class NoteAttachmentEntity(
    @PrimaryKey val id: String,
    val noteId: String,
    val fileName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val relativePath: String,
    val createdAt: Long,
)

@Entity(
    tableName = "note_drafts",
    foreignKeys = [ForeignKey(entity = NoteEntity::class, parentColumns = ["id"], childColumns = ["noteId"], onDelete = CASCADE)],
)
@Serializable
data class NoteDraftEntity(
    @PrimaryKey val noteId: String,
    val title: String,
    val content: String,
    val baseVersion: Long,
    val updatedAt: Long,
)

@Entity(tableName = "entity_links", indices = [Index(value = ["type", "fromId", "toId"], unique = true), Index("toId")])
@Serializable
data class EntityLinkEntity(
    @PrimaryKey val id: String,
    val type: String,
    val fromId: String,
    val toId: String,
    val createdAt: Long,
)

@Entity(
    tableName = "checklist_templates",
    foreignKeys = [
        ForeignKey(entity = ProjectEntity::class, parentColumns = ["id"], childColumns = ["projectId"], onDelete = SET_NULL),
        ForeignKey(entity = RecurrenceRuleEntity::class, parentColumns = ["id"], childColumns = ["recurrenceRuleId"], onDelete = SET_NULL),
    ],
    indices = [Index("projectId"), Index("recurrenceRuleId"), Index(value = ["kind", "isRoutine"]), Index("deletedAt")],
)
@Serializable
data class ChecklistTemplateEntity(
    @PrimaryKey val id: String,
    val title: String,
    val description: String,
    val kind: String,
    val isRoutine: Boolean,
    val projectId: String?,
    val color: Long,
    val icon: String,
    val recurrenceRuleId: String?,
    val trackingFrom: Long?,
    val generatedThrough: Long?,
    val archived: Boolean,
    val deletedAt: Long?,
    val sortOrder: Int,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "checklist_items",
    foreignKeys = [ForeignKey(entity = ChecklistTemplateEntity::class, parentColumns = ["id"], childColumns = ["templateId"], onDelete = CASCADE)],
    indices = [Index("templateId")],
)
@Serializable
data class ChecklistItemEntity(
    @PrimaryKey val id: String,
    val templateId: String,
    val title: String,
    val sortOrder: Int,
    val createdAt: Long,
)

/** Execução: o índice único (templateId, date) é a garantia de não duplicidade da geração. */
@Entity(
    tableName = "checklist_occurrences",
    foreignKeys = [ForeignKey(entity = ChecklistTemplateEntity::class, parentColumns = ["id"], childColumns = ["templateId"], onDelete = CASCADE)],
    indices = [Index(value = ["templateId", "date"], unique = true), Index("date"), Index("closedAt"), Index("status")],
)
@Serializable
data class ChecklistOccurrenceEntity(
    @PrimaryKey val id: String,
    val templateId: String,
    val date: Long,
    val status: String,
    val totalItems: Int,
    val checkedItems: Int,
    val dueAt: Long?,
    val closedAt: Long?,
    val completedAt: Long?,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "checklist_item_states",
    foreignKeys = [
        ForeignKey(entity = ChecklistOccurrenceEntity::class, parentColumns = ["id"], childColumns = ["occurrenceId"], onDelete = CASCADE),
        ForeignKey(entity = ChecklistItemEntity::class, parentColumns = ["id"], childColumns = ["itemId"], onDelete = SET_NULL),
    ],
    indices = [Index(value = ["occurrenceId", "itemId"], unique = true), Index("itemId")],
)
@Serializable
data class ChecklistItemStateEntity(
    @PrimaryKey val id: String,
    val occurrenceId: String,
    val itemId: String?,
    val title: String,
    val sortOrder: Int,
    val checked: Boolean,
    val checkedAt: Long?,
)

@Entity(
    tableName = "reminders",
    foreignKeys = [
        ForeignKey(entity = TaskEntity::class, parentColumns = ["id"], childColumns = ["taskId"], onDelete = CASCADE),
        ForeignKey(entity = ChecklistTemplateEntity::class, parentColumns = ["id"], childColumns = ["checklistId"], onDelete = CASCADE),
    ],
    indices = [Index("taskId"), Index("checklistId"), Index(value = ["fired", "triggerAt"])],
)
@Serializable
data class ReminderEntity(
    @PrimaryKey val id: String,
    val taskId: String?,
    val checklistId: String?,
    val triggerAt: Long,
    val offsetMinutes: Int?,
    val fired: Boolean,
    val snoozedUntil: Long?,
    val createdAt: Long,
)

@Entity(tableName = "activity_history", indices = [Index("createdAt"), Index(value = ["entityType", "entityId"])])
@Serializable
data class ActivityEntity(
    @PrimaryKey val id: String,
    val type: String,
    val entityType: String,
    val entityId: String?,
    val title: String,
    val projectId: String?,
    val createdAt: Long,
)
