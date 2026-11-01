package com.organizador.data.repository

import androidx.room.withTransaction
import com.organizador.data.local.OrganizadorDatabase
import com.organizador.data.local.TaskQueryBuilder
import com.organizador.data.local.categoryById
import com.organizador.data.local.invalidationFlow
import com.organizador.data.local.dao.TaskRow
import com.organizador.data.local.entity.ReminderEntity
import com.organizador.data.local.entity.SubtaskEntity
import com.organizador.data.local.entity.TaskEntity
import com.organizador.data.local.entity.TaskTagEntity
import com.organizador.data.mapper.toDomain
import com.organizador.data.mapper.toEntity
import com.organizador.data.mapper.toMinuteOfDay
import com.organizador.data.storage.AttachmentStorage
import com.organizador.domain.model.ActivityType
import com.organizador.domain.model.EntityType
import com.organizador.domain.model.Ids
import com.organizador.domain.model.ReminderMath
import com.organizador.domain.model.Tag
import com.organizador.domain.model.Task
import com.organizador.domain.model.TaskDetails
import com.organizador.domain.model.TaskDraft
import com.organizador.domain.model.TaskListItem
import com.organizador.domain.model.TaskQuery
import com.organizador.domain.model.TaskStatus
import com.organizador.domain.model.TimeProvider
import com.organizador.domain.recurrence.RecurrenceRule
import com.organizador.domain.repository.ActivityRepository
import com.organizador.domain.repository.TaskCounts
import com.organizador.domain.repository.TaskRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

internal fun parseTags(data: String?): List<Tag> =
    data?.split('\u001E')?.mapNotNull { chunk ->
        val parts = chunk.split('\u001F')
        if (parts.size < 3) null else Tag(parts[0], parts[1], parts[2].toLongOrNull() ?: 0L, Instant.EPOCH)
    }?.sortedBy { it.name.lowercase() }.orEmpty()

internal fun TaskRow.toListItem() = TaskListItem(
    task = task.toDomain(),
    projectName = projectName,
    projectColor = projectColor,
    subtaskTotal = subtaskTotal,
    subtaskDone = subtaskDone,
    tags = parseTags(tagData),
    hasReminder = hasReminder,
    isRecurring = task.recurrenceRuleId != null,
)

@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class TaskRepositoryImpl @Inject constructor(
    private val db: OrganizadorDatabase,
    private val time: TimeProvider,
    private val activity: ActivityRepository,
    private val storage: AttachmentStorage,
) : TaskRepository {
    private val taskDao = db.taskDao()
    private val recurrenceDao = db.recurrenceDao()
    private val reminderDao = db.reminderDao()

    override fun observeTasks(query: TaskQuery): Flow<List<TaskListItem>> =
        time.todayFlow().flatMapLatest { today ->
            taskDao.observeRows(TaskQueryBuilder.build(query, today).toRoom()).map { rows -> rows.map { it.toListItem() } }
        }

    override fun observeTasksDueBetween(from: LocalDate, to: LocalDate): Flow<List<TaskListItem>> =
        taskDao.observeRows(TaskQueryBuilder.dueBetween(from, to).toRoom()).map { rows -> rows.map { it.toListItem() } }

    override fun observeRecurringOpenTasks(): Flow<List<Pair<Task, RecurrenceRule>>> =
        db.invalidationFlow("tasks", "recurrence_rules").map {
            taskDao.recurringOpen().mapNotNull { t ->
                val rule = t.recurrenceRuleId?.let { recurrenceDao.get(it) }?.toDomain() ?: return@mapNotNull null
                t.toDomain() to rule
            }
        }

    override fun observeCounts(): Flow<TaskCounts> = time.todayFlow().flatMapLatest { today ->
        taskDao.observeCounts(today.toEpochDay()).map { TaskCounts(it.inbox, it.today, it.overdue, it.upcoming, it.trash) }
    }

    override fun observeTaskDetails(id: String): Flow<TaskDetails?> =
        // Qualquer alteração nas tabelas relacionadas reemite os detalhes completos.
        db.invalidationFlow("tasks", "subtasks", "task_tags", "tags", "reminders", "task_attachments", "recurrence_rules", "projects", "categories")
            .map { getDetails(id) }

    override suspend fun getTask(id: String): Task? = taskDao.get(id)?.toDomain()

    override suspend fun getDetails(id: String): TaskDetails? {
        val task = taskDao.get(id) ?: return null
        return TaskDetails(
            task = task.toDomain(),
            subtasks = taskDao.subtasks(id).map { it.toDomain() },
            tags = taskDao.tags(id).map { it.toDomain() },
            recurrence = task.recurrenceRuleId?.let { recurrenceDao.get(it)?.toDomain() },
            reminders = reminderDao.forOwner(id).map { it.toDomain() },
            attachments = taskDao.attachments(id).map { it.toDomain() },
            project = task.projectId?.let { db.projectDao().get(it)?.toDomain() },
            category = task.categoryId?.let { cid -> db.categoryById(cid) },
        )
    }

    override suspend fun save(draft: TaskDraft): String = db.withTransaction {
        require(draft.title.isNotBlank()) { "Título obrigatório" }
        val now = time.now()
        val existing = draft.id?.let { taskDao.get(it) }
        val id = existing?.id ?: draft.id ?: Ids.new()

        val oldRuleId = existing?.recurrenceRuleId
        val ruleId = draft.recurrence?.let { rule ->
            val withId = if (rule.id.isBlank()) rule.copy(id = oldRuleId ?: Ids.new()) else rule
            recurrenceDao.upsert(withId.toEntity())
            withId.id
        }

        taskDao.upsert(
            TaskEntity(
                id = id,
                title = draft.title.trim(),
                description = draft.description,
                notes = draft.notes,
                dueDate = draft.dueDate?.toEpochDay(),
                dueTime = draft.dueTime?.toMinuteOfDay(),
                priority = draft.priority.level,
                projectId = draft.projectId,
                categoryId = draft.categoryId,
                status = existing?.status ?: TaskStatus.OPEN.name,
                archived = existing?.archived ?: false,
                deletedAt = existing?.deletedAt,
                recurrenceRuleId = ruleId,
                linkedNoteId = draft.linkedNoteId ?: existing?.linkedNoteId,
                sortOrder = existing?.sortOrder ?: (taskDao.maxSortOrder() + 1),
                createdAt = existing?.createdAt ?: now.toEpochMilli(),
                updatedAt = now.toEpochMilli(),
                completedAt = existing?.completedAt,
            ),
        )
        if (oldRuleId != null && oldRuleId != ruleId) recurrenceDao.deleteIfOrphan(oldRuleId)

        val previous = taskDao.subtasks(id).associateBy { it.id }
        val keep = draft.subtasks.mapIndexed { index, sub ->
            val subId = sub.id ?: Ids.new()
            val prev = previous[subId]
            taskDao.upsertSubtask(
                SubtaskEntity(
                    id = subId, taskId = id, title = sub.title.trim(), done = sub.done, sortOrder = index,
                    createdAt = prev?.createdAt ?: now.toEpochMilli(),
                    completedAt = if (sub.done) prev?.completedAt ?: now.toEpochMilli() else null,
                ),
            )
            subId
        }
        taskDao.deleteSubtasksExcept(id, keep)

        taskDao.clearTags(id)
        if (draft.tagIds.isNotEmpty()) taskDao.insertTaskTags(draft.tagIds.map { TaskTagEntity(id, it) })

        // Lembretes relativos ao prazo são recriados; o agendamento de alarmes reage à alteração da tabela.
        reminderDao.deleteForOwner(id)
        val due = draft.dueDate
        if (due != null) {
            draft.reminderOffsets.distinct().forEach { offset ->
                reminderDao.upsert(
                    ReminderEntity(
                        id = Ids.new(), taskId = id, checklistId = null,
                        triggerAt = ReminderMath.triggerFor(due, draft.dueTime, offset, time.zone()).toEpochMilli(),
                        offsetMinutes = offset, fired = false, snoozedUntil = null, createdAt = now.toEpochMilli(),
                    ),
                )
            }
        }

        activity.log(
            if (existing == null) ActivityType.TASK_CREATED else ActivityType.TASK_UPDATED,
            EntityType.TASK, id, draft.title.trim(), draft.projectId, now,
        )
        id
    }

    override suspend fun updateTask(task: Task) {
        taskDao.upsert(task.toEntity())
    }

    override suspend fun setCompleted(id: String, completed: Boolean, at: Instant) {
        db.withTransaction {
            taskDao.setStatus(
                id,
                if (completed) TaskStatus.COMPLETED.name else TaskStatus.OPEN.name,
                if (completed) at.toEpochMilli() else null,
                at.toEpochMilli(),
            )
            // Lembretes de tarefa concluída não devem disparar.
            if (completed) reminderDao.forOwner(id).forEach { reminderDao.markFired(it.id) }
        }
    }

    override suspend fun moveToProject(id: String, projectId: String?) =
        taskDao.setProject(id, projectId, time.now().toEpochMilli())

    override suspend fun setArchived(id: String, archived: Boolean) {
        val now = time.now()
        taskDao.setArchived(id, archived, now.toEpochMilli())
        if (archived) taskDao.get(id)?.let { activity.log(ActivityType.TASK_ARCHIVED, EntityType.TASK, id, it.title, it.projectId, now) }
    }

    override suspend fun softDelete(id: String, at: Instant) {
        db.withTransaction {
            taskDao.setDeleted(id, at.toEpochMilli(), at.toEpochMilli())
            taskDao.get(id)?.let { activity.log(ActivityType.TASK_DELETED, EntityType.TASK, id, it.title, it.projectId, at) }
        }
    }

    override suspend fun restore(id: String) {
        val now = time.now()
        db.withTransaction {
            taskDao.setDeleted(id, null, now.toEpochMilli())
            taskDao.get(id)?.let { activity.log(ActivityType.TASK_RESTORED, EntityType.TASK, id, it.title, it.projectId, now) }
        }
    }

    override suspend fun deletePermanently(id: String) {
        val files = taskDao.attachments(id)
        db.withTransaction {
            val ruleId = taskDao.get(id)?.recurrenceRuleId
            db.noteDao().deleteLinksOf(id)
            taskDao.delete(id) // subtarefas, etiquetas, anexos e lembretes caem por CASCADE
            ruleId?.let { recurrenceDao.deleteIfOrphan(it) }
        }
        files.forEach { storage.delete(it.relativePath) }
    }

    override suspend fun emptyTrash(): Int {
        val ids = taskDao.trashedIds()
        ids.forEach { deletePermanently(it) }
        return ids.size
    }

    override suspend fun duplicate(id: String, now: Instant): String? {
        val details = getDetails(id) ?: return null
        return save(
            TaskDraft(
                title = details.task.title + " (cópia)",
                description = details.task.description,
                notes = details.task.notes,
                dueDate = details.task.dueDate,
                dueTime = details.task.dueTime,
                priority = details.task.priority,
                projectId = details.task.projectId,
                categoryId = details.task.categoryId,
                tagIds = details.tags.map { it.id }.toSet(),
                subtasks = details.subtasks.map { com.organizador.domain.model.SubtaskDraft(title = it.title, done = false) },
                recurrence = details.recurrence?.copy(id = Ids.new()),
                reminderOffsets = details.reminders.mapNotNull { it.offsetMinutes },
                linkedNoteId = details.task.linkedNoteId,
            ),
        )
    }

    override suspend fun reorder(orderedIds: List<String>) {
        db.withTransaction { orderedIds.forEachIndexed { index, id -> taskDao.setSortOrder(id, index.toLong()) } }
    }

    override suspend fun addSubtask(taskId: String, title: String): String {
        val id = Ids.new()
        val order = taskDao.subtasks(taskId).size
        taskDao.upsertSubtask(SubtaskEntity(id, taskId, title.trim(), false, order, time.now().toEpochMilli(), null))
        return id
    }

    override suspend fun setSubtaskDone(subtaskId: String, done: Boolean, at: Instant) {
        val sub = taskDao.subtask(subtaskId) ?: return
        taskDao.upsertSubtask(sub.copy(done = done, completedAt = if (done) at.toEpochMilli() else null))
    }

    override suspend fun deleteSubtask(subtaskId: String) = taskDao.deleteSubtask(subtaskId)

    override suspend fun openTasksWithDueDate(): List<Task> = taskDao.openWithDueDate().map { it.toDomain() }
}
