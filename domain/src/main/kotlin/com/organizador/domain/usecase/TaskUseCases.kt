package com.organizador.domain.usecase

import com.organizador.domain.model.ActivityType
import com.organizador.domain.model.EntityType
import com.organizador.domain.model.SubtaskDraft
import com.organizador.domain.model.TaskDetails
import com.organizador.domain.model.TaskDraft
import com.organizador.domain.model.TimeProvider
import com.organizador.domain.recurrence.RecurrenceEngine
import com.organizador.domain.recurrence.RecurrenceRule
import com.organizador.domain.repository.ActivityRepository
import com.organizador.domain.repository.TaskRepository
import com.organizador.domain.repository.TransactionRunner
import java.time.LocalDate
import javax.inject.Inject

data class CompletionResult(val taskId: String, val nextTaskId: String?, val nextDueDate: LocalDate?)

/**
 * Conclui uma tarefa. Para tarefas recorrentes, a instância atual é preservada como concluída
 * (histórico/estatísticas) e uma nova instância é criada na próxima data da regra, com subtarefas pendentes.
 */
class CompleteTaskUseCase @Inject constructor(
    private val tasks: TaskRepository,
    private val activity: ActivityRepository,
    private val tx: TransactionRunner,
    private val time: TimeProvider,
) {
    suspend operator fun invoke(taskId: String): CompletionResult? = tx.inTransaction {
        val details = tasks.getDetails(taskId) ?: return@inTransaction null
        if (details.task.isCompleted) return@inTransaction CompletionResult(taskId, null, null)
        val now = time.now()
        tasks.setCompleted(taskId, true, now)
        activity.log(ActivityType.TASK_COMPLETED, EntityType.TASK, taskId, details.task.title, details.task.projectId, now)

        val rule = details.recurrence
        val due = details.task.dueDate
        if (rule == null || !rule.enabled || due == null) return@inTransaction CompletionResult(taskId, null, null)
        val next = nextDueDate(rule, due, time.today()) ?: return@inTransaction CompletionResult(taskId, null, null)

        // A regra passa para a nova instância; a concluída fica sem regra para não gerar duplicatas ao reabrir.
        tasks.updateTask(details.task.copy(recurrenceRuleId = null, status = com.organizador.domain.model.TaskStatus.COMPLETED, completedAt = now, updatedAt = now))
        val nextId = tasks.save(details.toNextDraft(next, rule))
        CompletionResult(taskId, nextId, next)
    }

    companion object {
        /** Próxima data a partir do prazo; se ainda ficar no passado (tarefa muito atrasada), usa hoje em diante. */
        fun nextDueDate(rule: RecurrenceRule, currentDue: LocalDate, today: LocalDate): LocalDate? {
            val next = RecurrenceEngine.nextAfter(rule, currentDue) ?: return null
            return if (next.isBefore(today)) RecurrenceEngine.nextOnOrAfter(rule, today) else next
        }
    }
}

private fun TaskDetails.toNextDraft(nextDue: LocalDate, rule: RecurrenceRule) = TaskDraft(
    title = task.title,
    description = task.description,
    notes = task.notes,
    dueDate = nextDue,
    dueTime = task.dueTime,
    priority = task.priority,
    projectId = task.projectId,
    categoryId = task.categoryId,
    tagIds = tags.map { it.id }.toSet(),
    subtasks = subtasks.map { SubtaskDraft(title = it.title, done = false) },
    recurrence = rule,
    reminderOffsets = reminders.mapNotNull { it.offsetMinutes },
    linkedNoteId = task.linkedNoteId,
)

/** Desfaz uma conclusão recente: reabre a tarefa e remove a próxima instância recorrente criada. */
class UndoCompletionUseCase @Inject constructor(
    private val tasks: TaskRepository,
    private val activity: ActivityRepository,
    private val tx: TransactionRunner,
    private val time: TimeProvider,
) {
    suspend operator fun invoke(result: CompletionResult) = tx.inTransaction {
        val nextRuleId = result.nextTaskId?.let { tasks.getTask(it)?.recurrenceRuleId }
        val task = tasks.getTask(result.taskId) ?: return@inTransaction
        val now = time.now()
        // A regra volta para a tarefa original ANTES de apagar a próxima instância; do contrário ela ficaria
        // órfã e seria removida junto, quebrando a chave estrangeira (BUG-002).
        tasks.updateTask(
            task.copy(
                status = com.organizador.domain.model.TaskStatus.OPEN,
                completedAt = null,
                recurrenceRuleId = nextRuleId ?: task.recurrenceRuleId,
                updatedAt = now,
            ),
        )
        result.nextTaskId?.let { tasks.deletePermanently(it) }
        activity.log(ActivityType.TASK_REOPENED, EntityType.TASK, task.id, task.title, task.projectId, now)
    }
}

class ReopenTaskUseCase @Inject constructor(
    private val tasks: TaskRepository,
    private val activity: ActivityRepository,
    private val time: TimeProvider,
) {
    suspend operator fun invoke(taskId: String) {
        val task = tasks.getTask(taskId) ?: return
        val now = time.now()
        tasks.setCompleted(taskId, false, now)
        activity.log(ActivityType.TASK_REOPENED, EntityType.TASK, taskId, task.title, task.projectId, now)
    }
}
