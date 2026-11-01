package com.organizador.domain.model

import com.organizador.domain.recurrence.RecurrenceRule
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

enum class TaskStatus { OPEN, COMPLETED }

data class Task(
    val id: String,
    val title: String,
    val description: String = "",
    val notes: String = "",
    val dueDate: LocalDate? = null,
    val dueTime: LocalTime? = null,
    val priority: Priority = Priority.P4,
    /** null = Caixa de entrada. */
    val projectId: String? = null,
    val categoryId: String? = null,
    val status: TaskStatus = TaskStatus.OPEN,
    val archived: Boolean = false,
    /** Exclusão reversível (lixeira). */
    val deletedAt: Instant? = null,
    val recurrenceRuleId: String? = null,
    val linkedNoteId: String? = null,
    val sortOrder: Long = 0,
    val createdAt: Instant,
    val updatedAt: Instant,
    val completedAt: Instant? = null,
) {
    val isCompleted: Boolean get() = status == TaskStatus.COMPLETED
    val isDeleted: Boolean get() = deletedAt != null
}

data class Subtask(
    val id: String,
    val taskId: String,
    val title: String,
    val done: Boolean = false,
    val sortOrder: Int = 0,
    val createdAt: Instant,
    val completedAt: Instant? = null,
)

data class Project(
    val id: String,
    val name: String,
    val description: String = "",
    val color: Long = Palette.default,
    val icon: String = "folder",
    val sortOrder: Int = 0,
    val archived: Boolean = false,
    val createdAt: Instant,
    val updatedAt: Instant,
)

data class Category(
    val id: String,
    val name: String,
    val color: Long = Palette.default,
    val sortOrder: Int = 0,
    val createdAt: Instant,
)

data class Tag(
    val id: String,
    val name: String,
    val color: Long = Palette.default,
    val createdAt: Instant,
)

enum class AttachmentOwner { TASK, NOTE }

data class Attachment(
    val id: String,
    val owner: AttachmentOwner,
    val ownerId: String,
    val fileName: String,
    val mimeType: String,
    val sizeBytes: Long,
    /** Caminho relativo dentro do diretório privado de anexos do app. */
    val relativePath: String,
    val createdAt: Instant,
) {
    val isImage: Boolean get() = mimeType.startsWith("image/")
}

enum class ReminderOwner { TASK, CHECKLIST }

data class Reminder(
    val id: String,
    val owner: ReminderOwner,
    val ownerId: String,
    /** Momento absoluto do disparo; para tarefas recorrentes é recalculado a cada nova instância. */
    val triggerAt: Instant,
    /** Antecedência em minutos em relação ao prazo (null = horário absoluto). */
    val offsetMinutes: Int? = null,
    val fired: Boolean = false,
    val snoozedUntil: Instant? = null,
    val createdAt: Instant,
)

/** Linha de lista: tarefa com dados agregados para exibição sem consultas adicionais. */
data class TaskListItem(
    val task: Task,
    val projectName: String?,
    val projectColor: Long?,
    val subtaskTotal: Int,
    val subtaskDone: Int,
    val tags: List<Tag>,
    val hasReminder: Boolean,
    val isRecurring: Boolean,
)

data class TaskDetails(
    val task: Task,
    val subtasks: List<Subtask>,
    val tags: List<Tag>,
    val recurrence: RecurrenceRule?,
    val reminders: List<Reminder>,
    val attachments: List<Attachment>,
    val project: Project?,
    val category: Category?,
)

/** Dados completos para criação/edição de uma tarefa em uma única transação. */
data class TaskDraft(
    val id: String? = null,
    val title: String,
    val description: String = "",
    val notes: String = "",
    val dueDate: LocalDate? = null,
    val dueTime: LocalTime? = null,
    val priority: Priority = Priority.P4,
    val projectId: String? = null,
    val categoryId: String? = null,
    val tagIds: Set<String> = emptySet(),
    val subtasks: List<SubtaskDraft> = emptyList(),
    val recurrence: RecurrenceRule? = null,
    /** Antecedências (min) de lembretes relativos ao prazo. 0 = no horário. */
    val reminderOffsets: List<Int> = emptyList(),
    val linkedNoteId: String? = null,
)

data class SubtaskDraft(val id: String? = null, val title: String, val done: Boolean = false)
