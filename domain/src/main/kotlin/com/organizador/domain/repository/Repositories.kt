package com.organizador.domain.repository

import com.organizador.domain.model.ActivityEntry
import com.organizador.domain.model.ActivityType
import com.organizador.domain.model.AppSettings
import com.organizador.domain.model.Attachment
import com.organizador.domain.model.AttachmentOwner
import com.organizador.domain.model.Category
import com.organizador.domain.model.ChecklistDetails
import com.organizador.domain.model.ChecklistDraft
import com.organizador.domain.model.ChecklistItemState
import com.organizador.domain.model.ChecklistOccurrence
import com.organizador.domain.model.ChecklistSummary
import com.organizador.domain.model.ChecklistTemplate
import com.organizador.domain.model.EntityLink
import com.organizador.domain.model.EntityType
import com.organizador.domain.model.LinkType
import com.organizador.domain.model.Note
import com.organizador.domain.model.NoteDraft
import com.organizador.domain.model.NoteFolder
import com.organizador.domain.model.NoteSort
import com.organizador.domain.model.NoteSummary
import com.organizador.domain.model.NoteView
import com.organizador.domain.model.OccurrenceDetails
import com.organizador.domain.model.OccurrenceStatus
import com.organizador.domain.model.Project
import com.organizador.domain.model.Reminder
import com.organizador.domain.model.SaveResult
import com.organizador.domain.model.Statistics
import com.organizador.domain.model.StatsPeriod
import com.organizador.domain.model.Tag
import com.organizador.domain.model.Task
import com.organizador.domain.model.TaskDetails
import com.organizador.domain.model.TaskDraft
import com.organizador.domain.model.TaskListItem
import com.organizador.domain.model.TaskQuery
import com.organizador.domain.note.NoteDocument
import com.organizador.domain.recurrence.OpenOccurrence
import com.organizador.domain.recurrence.ReconcilePlan
import com.organizador.domain.recurrence.RecurrenceRule
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.LocalDate

/** Executa um bloco em uma transação do banco (atomicidade entre repositórios). */
interface TransactionRunner {
    suspend fun <T> inTransaction(block: suspend () -> T): T
}

interface TaskRepository {
    fun observeTasks(query: TaskQuery): Flow<List<TaskListItem>>
    fun observeTaskDetails(id: String): Flow<TaskDetails?>
    fun observeTasksDueBetween(from: LocalDate, to: LocalDate): Flow<List<TaskListItem>>

    /** Tarefas abertas com regra de recorrência (para projetar instâncias futuras no calendário). */
    fun observeRecurringOpenTasks(): Flow<List<Pair<Task, RecurrenceRule>>>
    fun observeCounts(): Flow<TaskCounts>
    suspend fun getTask(id: String): Task?
    suspend fun getDetails(id: String): TaskDetails?

    /** Cria ou atualiza a tarefa com subtarefas, etiquetas, recorrência e lembretes, atomicamente. */
    suspend fun save(draft: TaskDraft): String
    suspend fun updateTask(task: Task)
    suspend fun setCompleted(id: String, completed: Boolean, at: Instant)
    suspend fun moveToProject(id: String, projectId: String?)
    suspend fun setArchived(id: String, archived: Boolean)
    suspend fun softDelete(id: String, at: Instant)
    suspend fun restore(id: String)
    suspend fun deletePermanently(id: String)
    suspend fun emptyTrash(): Int
    suspend fun duplicate(id: String, now: Instant): String?
    suspend fun reorder(orderedIds: List<String>)
    suspend fun addSubtask(taskId: String, title: String): String
    suspend fun setSubtaskDone(subtaskId: String, done: Boolean, at: Instant)
    suspend fun deleteSubtask(subtaskId: String)
    suspend fun openTasksWithDueDate(): List<Task>
}

data class TaskCounts(val inbox: Int, val today: Int, val overdue: Int, val upcoming: Int, val trash: Int)

interface ProjectRepository {
    fun observeProjects(includeArchived: Boolean = false): Flow<List<Project>>
    fun observeProject(id: String): Flow<Project?>
    suspend fun getProject(id: String): Project?
    suspend fun save(project: Project)
    suspend fun setArchived(id: String, archived: Boolean)
    suspend fun delete(id: String)
    suspend fun reorder(orderedIds: List<String>)
}

interface LabelRepository {
    fun observeCategories(): Flow<List<Category>>
    fun observeTags(): Flow<List<Tag>>
    suspend fun saveCategory(category: Category)
    suspend fun deleteCategory(id: String)
    suspend fun reorderCategories(orderedIds: List<String>)
    suspend fun saveTag(tag: Tag)
    suspend fun deleteTag(id: String)
    suspend fun findOrCreateTag(name: String): Tag
}

interface RecurrenceRepository {
    suspend fun get(id: String): RecurrenceRule?
    suspend fun save(rule: RecurrenceRule)
    suspend fun delete(id: String)
}

interface ChecklistRepository {
    fun observeSummaries(routines: Boolean?, includeArchived: Boolean = false): Flow<List<ChecklistSummary>>
    fun observeTrash(): Flow<List<ChecklistTemplate>>
    fun observeDetails(templateId: String): Flow<ChecklistDetails?>
    fun observeOccurrence(occurrenceId: String): Flow<OccurrenceDetails?>
    fun observeCurrentOccurrence(templateId: String): Flow<OccurrenceDetails?>
    fun observeHistory(templateId: String, limit: Int = 500): Flow<List<ChecklistOccurrence>>
    fun observeOccurrencesBetween(from: LocalDate, to: LocalDate): Flow<List<Pair<ChecklistTemplate, ChecklistOccurrence>>>
    suspend fun getDetails(templateId: String): ChecklistDetails?

    /** Cria/atualiza modelo, itens e regra; sincroniza a execução aberta sem tocar no histórico fechado. */
    suspend fun save(draft: ChecklistDraft, now: Instant, trackingFrom: LocalDate?): String
    suspend fun setArchived(templateId: String, archived: Boolean)
    suspend fun softDelete(templateId: String, at: Instant)
    suspend fun restore(templateId: String)
    suspend fun deletePermanently(templateId: String)
    suspend fun setItemChecked(stateId: String, checked: Boolean, at: Instant)
    suspend fun setOccurrenceStatus(occurrenceId: String, status: OccurrenceStatus, at: Instant)

    /** Garante a execução única de listas simples/permanentes. */
    suspend fun ensureSingleOccurrence(templateId: String, date: LocalDate, now: Instant): String

    // Reconciliação de recorrências
    suspend fun recurringTemplates(): List<Pair<ChecklistTemplate, RecurrenceRule>>
    suspend fun openOccurrences(templateId: String): List<OpenOccurrence>
    suspend fun applyPlan(templateId: String, plan: ReconcilePlan, dueAt: (LocalDate) -> Instant?, now: Instant)
    suspend fun updateTracking(templateId: String, trackingFrom: LocalDate?, generatedThrough: LocalDate?)
    suspend fun discardUntouchedOpenOccurrences(templateId: String, keepDates: Set<LocalDate>)
    suspend fun openOccurrencesWithDue(): List<Pair<ChecklistTemplate, ChecklistOccurrence>>
}

interface NoteRepository {
    fun observeNotes(view: NoteView, sort: NoteSort, query: String = ""): Flow<List<NoteSummary>>
    fun observeNote(id: String): Flow<Note?>
    fun observeRecent(limit: Int): Flow<List<NoteSummary>>
    fun observeFolders(): Flow<List<NoteFolder>>
    fun observeNoteTags(noteId: String): Flow<List<Tag>>
    fun observeLinks(noteId: String): Flow<List<EntityLink>>
    suspend fun getNote(id: String): Note?
    suspend fun create(title: String, document: NoteDocument, folderId: String?, projectId: String?, now: Instant): String

    /** Grava com controle otimista de versão. */
    suspend fun saveContent(id: String, title: String, document: NoteDocument, baseVersion: Long, now: Instant): SaveResult
    suspend fun updateMeta(id: String, folderId: String?, projectId: String?, favorite: Boolean, pinned: Boolean, archived: Boolean)
    suspend fun setTags(noteId: String, tagIds: Set<String>)
    suspend fun softDelete(id: String, at: Instant)
    suspend fun restore(id: String)
    suspend fun deletePermanently(id: String)
    suspend fun saveDraft(draft: NoteDraft)
    suspend fun getDraft(noteId: String): NoteDraft?
    suspend fun deleteDraft(noteId: String)
    suspend fun saveFolder(folder: NoteFolder)
    suspend fun deleteFolder(id: String)
    suspend fun addLink(type: LinkType, fromId: String, toId: String, now: Instant)
    suspend fun removeLink(linkId: String)
}

interface AttachmentRepository {
    fun observe(owner: AttachmentOwner, ownerId: String): Flow<List<Attachment>>
    suspend fun get(id: String): Attachment?

    /** Copia o conteúdo para o armazenamento privado do app e registra o anexo. */
    suspend fun add(owner: AttachmentOwner, ownerId: String, fileName: String, mimeType: String, source: java.io.InputStream, now: Instant): Attachment
    suspend fun delete(id: String)
    fun fileFor(attachment: Attachment): java.io.File
}

interface ReminderRepository {
    fun observeFor(ownerId: String): Flow<List<Reminder>>

    /** Emite a cada alteração na tabela de lembretes; usado para (re)agendar alarmes automaticamente. */
    fun observePending(): Flow<List<Reminder>>
    suspend fun pending(): List<Reminder>
    suspend fun get(id: String): Reminder?
    suspend fun forOwner(ownerId: String): List<Reminder>
    suspend fun save(reminder: Reminder)
    suspend fun markFired(id: String)
    suspend fun snooze(id: String, until: Instant)
    suspend fun delete(id: String)
    suspend fun deleteForOwner(ownerId: String)
}

interface ActivityRepository {
    fun observeRecent(limit: Int): Flow<List<ActivityEntry>>
    suspend fun log(type: ActivityType, entityType: EntityType, entityId: String?, title: String, projectId: String? = null, at: Instant)
}

interface StatisticsRepository {
    suspend fun compute(period: StatsPeriod, projectId: String?, today: LocalDate): Statistics
}

interface SettingsRepository {
    val settings: Flow<AppSettings>
    suspend fun current(): AppSettings
    suspend fun update(transform: (AppSettings) -> AppSettings)
}
