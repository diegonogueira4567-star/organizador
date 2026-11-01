package com.organizador.data.mapper

import com.organizador.data.local.entity.ActivityEntity
import com.organizador.data.local.entity.CategoryEntity
import com.organizador.data.local.entity.ChecklistItemEntity
import com.organizador.data.local.entity.ChecklistItemStateEntity
import com.organizador.data.local.entity.ChecklistOccurrenceEntity
import com.organizador.data.local.entity.ChecklistTemplateEntity
import com.organizador.data.local.entity.EntityLinkEntity
import com.organizador.data.local.entity.NoteAttachmentEntity
import com.organizador.data.local.entity.NoteEntity
import com.organizador.data.local.entity.NoteFolderEntity
import com.organizador.data.local.entity.ProjectEntity
import com.organizador.data.local.entity.RecurrenceRuleEntity
import com.organizador.data.local.entity.ReminderEntity
import com.organizador.data.local.entity.SubtaskEntity
import com.organizador.data.local.entity.TagEntity
import com.organizador.data.local.entity.TaskAttachmentEntity
import com.organizador.data.local.entity.TaskEntity
import com.organizador.domain.model.ActivityEntry
import com.organizador.domain.model.ActivityType
import com.organizador.domain.model.Attachment
import com.organizador.domain.model.AttachmentOwner
import com.organizador.domain.model.Category
import com.organizador.domain.model.ChecklistItem
import com.organizador.domain.model.ChecklistItemState
import com.organizador.domain.model.ChecklistKind
import com.organizador.domain.model.ChecklistOccurrence
import com.organizador.domain.model.ChecklistTemplate
import com.organizador.domain.model.EntityLink
import com.organizador.domain.model.EntityType
import com.organizador.domain.model.LinkType
import com.organizador.domain.model.Note
import com.organizador.domain.model.NoteFolder
import com.organizador.domain.model.OccurrenceStatus
import com.organizador.domain.model.Priority
import com.organizador.domain.model.Project
import com.organizador.domain.model.Reminder
import com.organizador.domain.model.ReminderOwner
import com.organizador.domain.model.Subtask
import com.organizador.domain.model.Tag
import com.organizador.domain.model.Task
import com.organizador.domain.model.TaskStatus
import com.organizador.domain.note.NoteDocumentCodec
import com.organizador.domain.recurrence.Frequency
import com.organizador.domain.recurrence.RecurrenceRule
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

internal fun Long.toInstant(): Instant = Instant.ofEpochMilli(this)
internal fun Long.toLocalDate(): LocalDate = LocalDate.ofEpochDay(this)
internal fun Int.toLocalTime(): LocalTime = LocalTime.of(this / 60, this % 60)
internal fun LocalTime.toMinuteOfDay(): Int = hour * 60 + minute

/** Enum desconhecido (ex.: backup de versão futura) cai no valor padrão em vez de travar a leitura. */
internal inline fun <reified E : Enum<E>> String.toEnum(default: E): E =
    enumValues<E>().firstOrNull { it.name == this } ?: default

fun ProjectEntity.toDomain() = Project(id, name, description, color, icon, sortOrder, archived, createdAt.toInstant(), updatedAt.toInstant())
fun Project.toEntity() = ProjectEntity(id, name, description, color, icon, sortOrder, archived, createdAt.toEpochMilli(), updatedAt.toEpochMilli())

fun CategoryEntity.toDomain() = Category(id, name, color, sortOrder, createdAt.toInstant())
fun Category.toEntity() = CategoryEntity(id, name, color, sortOrder, createdAt.toEpochMilli())

fun TagEntity.toDomain() = Tag(id, name, color, createdAt.toInstant())
fun Tag.toEntity() = TagEntity(id, name, color, createdAt.toEpochMilli())

fun TaskEntity.toDomain() = Task(
    id = id, title = title, description = description, notes = notes,
    dueDate = dueDate?.toLocalDate(), dueTime = dueTime?.toLocalTime(),
    priority = Priority.fromLevel(priority), projectId = projectId, categoryId = categoryId,
    status = status.toEnum(TaskStatus.OPEN), archived = archived, deletedAt = deletedAt?.toInstant(),
    recurrenceRuleId = recurrenceRuleId, linkedNoteId = linkedNoteId, sortOrder = sortOrder,
    createdAt = createdAt.toInstant(), updatedAt = updatedAt.toInstant(), completedAt = completedAt?.toInstant(),
)

fun Task.toEntity() = TaskEntity(
    id = id, title = title, description = description, notes = notes,
    dueDate = dueDate?.toEpochDay(), dueTime = dueTime?.toMinuteOfDay(), priority = priority.level,
    projectId = projectId, categoryId = categoryId, status = status.name, archived = archived,
    deletedAt = deletedAt?.toEpochMilli(), recurrenceRuleId = recurrenceRuleId, linkedNoteId = linkedNoteId,
    sortOrder = sortOrder, createdAt = createdAt.toEpochMilli(), updatedAt = updatedAt.toEpochMilli(),
    completedAt = completedAt?.toEpochMilli(),
)

fun SubtaskEntity.toDomain() = Subtask(id, taskId, title, done, sortOrder, createdAt.toInstant(), completedAt?.toInstant())

fun RecurrenceRuleEntity.toDomain() = RecurrenceRule(
    id = id,
    frequency = frequency.toEnum(Frequency.NONE),
    interval = interval.coerceAtLeast(1),
    daysOfWeek = daysOfWeek.split(',').mapNotNull { it.trim().toIntOrNull()?.takeIf { d -> d in 1..7 }?.let(DayOfWeek::of) }.toSet(),
    daysOfMonth = daysOfMonth.split(',').mapNotNull { it.trim().toIntOrNull()?.takeIf { d -> d == -1 || d in 1..31 } }.toSet(),
    monthOfYear = monthOfYear, dayOfMonth = dayOfMonth,
    startDate = startDate.toLocalDate(),
    endDate = endDate?.takeIf { it >= startDate }?.toLocalDate(),
    timeOfDay = timeOfDay?.toLocalTime(), resetTime = resetTime?.toLocalTime(), zoneId = zoneId, enabled = enabled,
)

fun RecurrenceRule.toEntity() = RecurrenceRuleEntity(
    id = id, frequency = frequency.name, interval = interval,
    daysOfWeek = daysOfWeek.map { it.value }.sorted().joinToString(","),
    daysOfMonth = daysOfMonth.sorted().joinToString(","),
    monthOfYear = monthOfYear, dayOfMonth = dayOfMonth,
    startDate = startDate.toEpochDay(), endDate = endDate?.toEpochDay(),
    timeOfDay = timeOfDay?.toMinuteOfDay(), resetTime = resetTime?.toMinuteOfDay(), zoneId = zoneId, enabled = enabled,
)

fun ReminderEntity.toDomain() = Reminder(
    id = id,
    owner = if (taskId != null) ReminderOwner.TASK else ReminderOwner.CHECKLIST,
    ownerId = taskId ?: checklistId.orEmpty(),
    triggerAt = triggerAt.toInstant(), offsetMinutes = offsetMinutes, fired = fired,
    snoozedUntil = snoozedUntil?.toInstant(), createdAt = createdAt.toInstant(),
)

fun Reminder.toEntity() = ReminderEntity(
    id = id,
    taskId = ownerId.takeIf { owner == ReminderOwner.TASK },
    checklistId = ownerId.takeIf { owner == ReminderOwner.CHECKLIST },
    triggerAt = triggerAt.toEpochMilli(), offsetMinutes = offsetMinutes, fired = fired,
    snoozedUntil = snoozedUntil?.toEpochMilli(), createdAt = createdAt.toEpochMilli(),
)

fun TaskAttachmentEntity.toDomain() = Attachment(id, AttachmentOwner.TASK, taskId, fileName, mimeType, sizeBytes, relativePath, createdAt.toInstant())
fun NoteAttachmentEntity.toDomain() = Attachment(id, AttachmentOwner.NOTE, noteId, fileName, mimeType, sizeBytes, relativePath, createdAt.toInstant())

fun NoteEntity.toDomain(): Note {
    val document = NoteDocumentCodec.decode(content)
    return Note(
        id = id, title = title, document = document, plainText = plainText, folderId = folderId, projectId = projectId,
        favorite = favorite, pinned = pinned, archived = archived, deletedAt = deletedAt?.toInstant(), version = version,
        createdAt = createdAt.toInstant(), updatedAt = updatedAt.toInstant(),
    )
}

fun NoteFolderEntity.toDomain() = NoteFolder(id, name, parentId, color, sortOrder, createdAt.toInstant())
fun NoteFolder.toEntity() = NoteFolderEntity(id, name, parentId, color, sortOrder, createdAt.toEpochMilli())

fun EntityLinkEntity.toDomain() = EntityLink(id, type.toEnum(LinkType.NOTE_NOTE), fromId, toId, createdAt.toInstant())

fun ChecklistTemplateEntity.toDomain() = ChecklistTemplate(
    id = id, title = title, description = description, kind = kind.toEnum(ChecklistKind.SIMPLE), isRoutine = isRoutine,
    projectId = projectId, color = color, icon = icon, recurrenceRuleId = recurrenceRuleId,
    trackingFrom = trackingFrom?.toLocalDate(), generatedThrough = generatedThrough?.toLocalDate(),
    archived = archived, deletedAt = deletedAt?.toInstant(), sortOrder = sortOrder,
    createdAt = createdAt.toInstant(), updatedAt = updatedAt.toInstant(),
)

fun ChecklistItemEntity.toDomain() = ChecklistItem(id, templateId, title, sortOrder, createdAt.toInstant())

fun ChecklistOccurrenceEntity.toDomain() = ChecklistOccurrence(
    id = id, templateId = templateId, date = date.toLocalDate(), status = status.toEnum(OccurrenceStatus.OPEN),
    totalItems = totalItems, checkedItems = checkedItems, dueAt = dueAt?.toInstant(), closedAt = closedAt?.toInstant(),
    completedAt = completedAt?.toInstant(), createdAt = createdAt.toInstant(), updatedAt = updatedAt.toInstant(),
)

fun ChecklistItemStateEntity.toDomain() = ChecklistItemState(id, occurrenceId, itemId, title, sortOrder, checked, checkedAt?.toInstant())

fun ActivityEntity.toDomain() = ActivityEntry(
    id, type.toEnum(ActivityType.TASK_UPDATED), entityType.toEnum(EntityType.SYSTEM), entityId, title, projectId, createdAt.toInstant(),
)
