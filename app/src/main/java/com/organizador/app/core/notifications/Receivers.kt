package com.organizador.app.core.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.organizador.app.core.sync.ReconcileWorker
import com.organizador.app.core.sync.SyncCoordinator
import com.organizador.app.ui.common.Formats
import com.organizador.domain.model.TaskDraft
import com.organizador.domain.model.TimeProvider
import com.organizador.domain.model.TaskQuery
import com.organizador.domain.model.TaskView
import com.organizador.domain.repository.ChecklistRepository
import com.organizador.domain.repository.ReminderRepository
import com.organizador.domain.repository.SettingsRepository
import com.organizador.domain.repository.TaskRepository
import com.organizador.domain.usecase.CompleteTaskUseCase
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

private val receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

/** Executa trabalho curto fora da thread principal mantendo o broadcast vivo até terminar. */
private fun BroadcastReceiver.runAsync(block: suspend () -> Unit) {
    val pending = goAsync()
    receiverScope.launch {
        try {
            block()
        } catch (e: Exception) {
            Log.e("OrganizadorReceiver", "falha ao processar ${javaClass.simpleName}", e)
        } finally {
            pending.finish()
        }
    }
}

@AndroidEntryPoint
class AlarmReceiver : BroadcastReceiver() {
    @Inject lateinit var reminders: ReminderRepository
    @Inject lateinit var tasks: TaskRepository
    @Inject lateinit var checklists: ChecklistRepository
    @Inject lateinit var settings: SettingsRepository
    @Inject lateinit var notifications: NotificationHelper
    @Inject lateinit var scheduler: AlarmScheduler
    @Inject lateinit var time: TimeProvider

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_ALARM) return
        val (type, id) = AlarmScheduler.parse(intent.data) ?: return
        runAsync {
            val s = settings.current()
            when (type) {
                AlarmType.REMINDER -> {
                    val reminder = reminders.get(id) ?: return@runAsync
                    if (reminder.fired) return@runAsync
                    val task = tasks.getTask(reminder.ownerId)
                    reminders.markFired(id) // marca antes de exibir: evita aviso duplicado em execuções concorrentes
                    if (task == null || task.isCompleted || task.isDeleted || !s.notifications.enabled) return@runAsync
                    val local = time.now().atZone(time.zone()).toLocalTime()
                    if (s.notifications.quietHours.contains(local)) {
                        reminders.snooze(id, com.organizador.domain.model.ReminderMath.applyQuietHours(time.now(), s.notifications.quietHours, time.zone()))
                    } else {
                        val due = task.dueDate?.let { Formats.relativeDate(it, time.today()) + (task.dueTime?.let { t -> " às " + Formats.time(t, s.timeFormat) } ?: "") }
                        notifications.showTaskReminder(task.id, id, task.title, due ?: "Lembrete", s.notifications.defaultSnoozeMinutes)
                    }
                }
                AlarmType.CHECKLIST -> {
                    val details = checklists.observeOccurrence(id).first() ?: return@runAsync
                    if (!details.occurrence.isOpen || details.occurrence.status.isFinal || !s.notifications.enabled) return@runAsync
                    val pending = details.states.count { !it.checked }
                    notifications.showChecklist(
                        details.template.id, id, details.template.title,
                        if (pending == 0) "Tudo concluído" else "$pending de ${details.states.size} itens pendentes",
                    )
                }
                AlarmType.ROLLOVER -> ReconcileWorker.runNow(context)
                AlarmType.SUMMARY -> showSummary(s)
            }
            scheduler.syncAll()
        }
    }

    private suspend fun showSummary(s: com.organizador.domain.model.AppSettings) {
        val overdue = if (s.notifications.overdueAlerts) tasks.observeTasks(TaskQuery(TaskView.Overdue)).first() else emptyList()
        val upcoming = if (s.notifications.upcomingDeadlines) tasks.observeTasks(TaskQuery(TaskView.Upcoming(1))).first() else emptyList()
        if (overdue.isEmpty() && upcoming.isEmpty()) return
        val lines = buildList {
            if (overdue.isNotEmpty()) add("${overdue.size} tarefa(s) atrasada(s)")
            val today = upcoming.count { it.task.dueDate == time.today() }
            if (today > 0) add("$today para hoje")
            val tomorrow = upcoming.size - today
            if (tomorrow > 0) add("$tomorrow para amanhã")
        }
        notifications.showSummary("Resumo do dia", lines)
    }

    companion object {
        const val ACTION_ALARM = "com.organizador.app.ALARM"
    }
}

@AndroidEntryPoint
class NotificationActionReceiver : BroadcastReceiver() {
    @Inject lateinit var completeTask: CompleteTaskUseCase
    @Inject lateinit var reminders: ReminderRepository
    @Inject lateinit var tasks: TaskRepository
    @Inject lateinit var notifications: NotificationHelper
    @Inject lateinit var scheduler: AlarmScheduler
    @Inject lateinit var time: TimeProvider

    override fun onReceive(context: Context, intent: Intent) {
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION, 0)
        runAsync {
            when (intent.action) {
                ACTION_COMPLETE -> intent.getStringExtra(EXTRA_TASK)?.let { completeTask(it) }
                ACTION_SNOOZE -> intent.getStringExtra(EXTRA_REMINDER)?.let { id ->
                    val minutes = intent.getIntExtra(EXTRA_MINUTES, 15).coerceAtLeast(1)
                    reminders.snooze(id, time.now().plusSeconds(minutes * 60L))
                }
                ACTION_TOMORROW -> intent.getStringExtra(EXTRA_TASK)?.let { id ->
                    val details = tasks.getDetails(id) ?: return@let
                    val t = details.task
                    tasks.save(
                        TaskDraft(
                            id = t.id, title = t.title, description = t.description, notes = t.notes,
                            dueDate = time.today().plusDays(1), dueTime = t.dueTime, priority = t.priority,
                            projectId = t.projectId, categoryId = t.categoryId, tagIds = details.tags.map { it.id }.toSet(),
                            subtasks = details.subtasks.map { com.organizador.domain.model.SubtaskDraft(it.id, it.title, it.done) },
                            recurrence = details.recurrence, reminderOffsets = details.reminders.mapNotNull { it.offsetMinutes },
                            linkedNoteId = t.linkedNoteId,
                        ),
                    )
                }
            }
            notifications.cancel(notificationId)
            scheduler.syncAll()
        }
    }

    companion object {
        const val ACTION_COMPLETE = "com.organizador.app.COMPLETE"
        const val ACTION_SNOOZE = "com.organizador.app.SNOOZE"
        const val ACTION_TOMORROW = "com.organizador.app.TOMORROW"
        const val EXTRA_TASK = "task"
        const val EXTRA_REMINDER = "reminder"
        const val EXTRA_MINUTES = "minutes"
        const val EXTRA_NOTIFICATION = "notification"
    }
}

/** Reinicialização, atualização do app e mudanças de data/hora/fuso: recalcula execuções e alarmes. */
class SystemEventsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.i("OrganizadorReceiver", "evento do sistema: ${intent.action}")
        // WorkManager garante a execução mesmo que o processo seja encerrado logo após o broadcast.
        ReconcileWorker.runNow(context)
    }
}
