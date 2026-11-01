package com.organizador.app.core.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import com.organizador.domain.model.ChecklistKind
import com.organizador.domain.model.ReminderMath
import com.organizador.domain.model.TimeProvider
import com.organizador.domain.recurrence.OccurrencePlanner
import com.organizador.domain.recurrence.RecurrenceEngine
import com.organizador.domain.repository.ChecklistRepository
import com.organizador.domain.repository.ReminderRepository
import com.organizador.domain.repository.SettingsRepository
import com.organizador.domain.repository.TaskRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Duration
import java.time.Instant
import java.time.ZonedDateTime
import javax.inject.Inject
import javax.inject.Singleton

enum class AlarmType { REMINDER, CHECKLIST, ROLLOVER, SUMMARY }

/**
 * Reconstrói todos os alarmes a partir do banco (fonte da verdade). Idempotente: pode ser chamado a
 * qualquer momento (abertura, boot, mudança de fuso, alteração de lembretes) sem duplicar avisos.
 *
 * Precisão: com a permissão de alarmes exatos usa setExactAndAllowWhileIdle; sem ela, setAndAllowWhileIdle,
 * que o Android pode atrasar (economia de bateria). A interface informa essa diferença ao usuário.
 */
@Singleton
class AlarmScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val reminders: ReminderRepository,
    private val tasks: TaskRepository,
    private val checklists: ChecklistRepository,
    private val settings: SettingsRepository,
    private val time: TimeProvider,
) {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)
    private val prefs = context.getSharedPreferences("alarm_registry", Context.MODE_PRIVATE)

    fun canScheduleExact(): Boolean = Build.VERSION.SDK_INT < 31 || alarmManager.canScheduleExactAlarms()

    suspend fun syncAll() {
        val s = settings.current()
        val now = time.now()
        val zone = time.zone()
        val wanted = LinkedHashMap<String, Instant>() // chave "TIPO/id" -> instante

        if (s.notifications.enabled && s.notifications.taskReminders) {
            for (reminder in reminders.pending()) {
                val task = tasks.getTask(reminder.ownerId)
                if (task == null || task.isCompleted || task.isDeleted || task.archived) {
                    reminders.markFired(reminder.id)
                    continue
                }
                val trigger = reminder.snoozedUntil ?: reminder.triggerAt
                if (Duration.between(trigger, now) > Duration.ofHours(MISSED_GRACE_HOURS)) {
                    reminders.markFired(reminder.id) // perdido há muito tempo: não notificar retroativamente
                    continue
                }
                val at = ReminderMath.applyQuietHours(maxOf(trigger, now.plusSeconds(2)), s.notifications.quietHours, zone)
                wanted["${AlarmType.REMINDER}/${reminder.id}"] = at
            }
        }

        if (s.notifications.enabled && (s.notifications.checklistAlerts || s.notifications.routineAlerts)) {
            for ((template, occurrence) in checklists.openOccurrencesWithDue()) {
                val enabled = if (template.isRoutine) s.notifications.routineAlerts else s.notifications.checklistAlerts
                val due = occurrence.dueAt ?: continue
                if (!enabled || !due.isAfter(now)) continue
                wanted["${AlarmType.CHECKLIST}/${occurrence.id}"] = ReminderMath.applyQuietHours(due, s.notifications.quietHours, zone)
            }
        }

        nextRollover(s.routineResetTime)?.let { wanted["${AlarmType.ROLLOVER}/next"] = it }

        if (s.notifications.enabled && (s.notifications.overdueAlerts || s.notifications.upcomingDeadlines)) {
            var at = ZonedDateTime.of(time.today(), s.notifications.dailySummaryTime, zone)
            if (!at.toInstant().isAfter(now)) at = at.plusDays(1)
            wanted["${AlarmType.SUMMARY}/daily"] = at.toInstant()
        }

        val previous = prefs.getStringSet(KEY_REGISTRY, emptySet()).orEmpty()
        (previous - wanted.keys).forEach { key -> cancel(key) }
        wanted.forEach { (key, at) -> schedule(key, at, exact = !key.startsWith(AlarmType.ROLLOVER.name)) }
        prefs.edit().putStringSet(KEY_REGISTRY, wanted.keys).apply()
        Log.i(TAG, "alarmes agendados: ${wanted.size} (exatos: ${canScheduleExact()})")
    }

    /** Próximo instante em que alguma janela de checklist abre/fecha (para reconciliar na hora certa). */
    private suspend fun nextRollover(defaultReset: java.time.LocalTime): Instant? {
        val now = time.now()
        return checklists.recurringTemplates()
            .filter { (template, rule) -> template.kind == ChecklistKind.RECURRING && rule.enabled }
            .mapNotNull { (_, rule) ->
                val zone = rule.zone(time.zone())
                val reset = rule.resetTime ?: defaultReset
                val effective = OccurrencePlanner.effectiveDate(now.atZone(zone), reset)
                RecurrenceEngine.nextAfter(rule, effective)?.let { OccurrencePlanner.windowStart(it, reset, zone).toInstant() }
                    ?: rule.endDate?.let { OccurrencePlanner.windowStart(it.plusDays(1), reset, zone).toInstant() }?.takeIf { it.isAfter(now) }
            }
            .minOrNull()
    }

    private fun schedule(key: String, at: Instant, exact: Boolean) {
        val pending = pendingIntent(key, create = true) ?: return
        val millis = at.toEpochMilli()
        try {
            if (exact && canScheduleExact()) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pending)
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pending)
            }
        } catch (e: SecurityException) {
            // Permissão de alarme exato revogada durante o agendamento: recorre ao inexato.
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pending)
        }
    }

    private fun cancel(key: String) {
        pendingIntent(key, create = false)?.let {
            alarmManager.cancel(it)
            it.cancel()
        }
    }

    private fun pendingIntent(key: String, create: Boolean): PendingIntent? {
        val intent = Intent(context, AlarmReceiver::class.java)
            .setAction(AlarmReceiver.ACTION_ALARM)
            .setData(Uri.parse("organizador://alarm/$key"))
        val flags = PendingIntent.FLAG_IMMUTABLE or if (create) PendingIntent.FLAG_UPDATE_CURRENT else PendingIntent.FLAG_NO_CREATE
        return PendingIntent.getBroadcast(context, 0, intent, flags)
    }

    companion object {
        private const val TAG = "OrganizadorAlarm"
        private const val KEY_REGISTRY = "keys"
        const val MISSED_GRACE_HOURS = 12L

        fun parse(uri: Uri?): Pair<AlarmType, String>? {
            val segments = uri?.pathSegments ?: return null
            if (uri.host != "alarm" || segments.size < 2) return null
            val type = AlarmType.entries.firstOrNull { it.name == segments[0] } ?: return null
            return type to segments[1]
        }
    }
}
