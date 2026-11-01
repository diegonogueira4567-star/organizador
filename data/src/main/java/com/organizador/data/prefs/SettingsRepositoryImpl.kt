package com.organizador.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.organizador.domain.model.AppSettings
import com.organizador.domain.model.DashboardCard
import com.organizador.domain.model.DashboardCardConfig
import com.organizador.domain.model.DateFormatPref
import com.organizador.domain.model.Density
import com.organizador.domain.model.NavItem
import com.organizador.domain.model.NoteSort
import com.organizador.domain.model.NotificationPrefs
import com.organizador.domain.model.QuietHours
import com.organizador.domain.model.StartDestination
import com.organizador.domain.model.TaskSort
import com.organizador.domain.model.ThemeMode
import com.organizador.domain.model.TimeFormatPref
import com.organizador.domain.repository.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException
import java.time.DayOfWeek
import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Singleton

private val Context.settingsStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Singleton
class SettingsRepositoryImpl @Inject constructor(@ApplicationContext context: Context) : SettingsRepository {
    private val store = context.settingsStore

    override val settings: Flow<AppSettings> = store.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { it.toSettings() }

    override suspend fun current(): AppSettings = settings.first()

    override suspend fun update(transform: (AppSettings) -> AppSettings) {
        store.edit { prefs -> prefs.write(transform(prefs.toSettings())) }
    }

    /** Exportação para backup: chave -> valor em texto (preferências de segurança ficam de fora). */
    suspend fun exportRaw(): Map<String, String> = store.data.first().asMap().mapKeys { it.key.name }.mapValues { it.value.toString() }

    suspend fun importRaw(values: Map<String, String>) {
        store.edit { prefs ->
            val defaults = AppSettings()
            val restored = emptyPreferences().toMutablePreferences().apply {
                values.forEach { (name, value) -> putTyped(name, value) }
            }.toSettings()
            // Bloqueio por PIN não é restaurado: o hash do PIN não faz parte do backup.
            prefs.write(restored.copy(pinEnabled = defaults.pinEnabled, biometricEnabled = defaults.biometricEnabled))
        }
    }

    private fun MutablePreferences.putTyped(name: String, value: String) {
        when (name) {
            in INT_KEYS -> value.toIntOrNull()?.let { this[intPreferencesKey(name)] = it }
            in LONG_KEYS -> value.toLongOrNull()?.let { this[longPreferencesKey(name)] = it }
            in FLOAT_KEYS -> value.toFloatOrNull()?.let { this[floatPreferencesKey(name)] = it }
            in BOOL_KEYS -> this[booleanPreferencesKey(name)] = value.toBoolean()
            else -> this[stringPreferencesKey(name)] = value
        }
    }

    private object K {
        val theme = stringPreferencesKey("theme")
        val accent = longPreferencesKey("accent")
        val dynamic = booleanPreferencesKey("dynamic_color")
        val fontScale = floatPreferencesKey("font_scale")
        val density = stringPreferencesKey("density")
        val firstDay = intPreferencesKey("first_day")
        val dateFormat = stringPreferencesKey("date_format")
        val timeFormat = stringPreferencesKey("time_format")
        val resetTime = intPreferencesKey("routine_reset")
        val confirmDelete = booleanPreferencesKey("confirm_delete")
        val start = stringPreferencesKey("start_destination")
        val navOrder = stringPreferencesKey("nav_order")
        val dashboard = stringPreferencesKey("dashboard_cards")
        val taskSort = stringPreferencesKey("task_sort")
        val noteSort = stringPreferencesKey("note_sort")
        val notifEnabled = booleanPreferencesKey("notif_enabled")
        val notifTasks = booleanPreferencesKey("notif_tasks")
        val notifOverdue = booleanPreferencesKey("notif_overdue")
        val notifUpcoming = booleanPreferencesKey("notif_upcoming")
        val notifChecklists = booleanPreferencesKey("notif_checklists")
        val notifRoutines = booleanPreferencesKey("notif_routines")
        val summaryTime = intPreferencesKey("notif_summary_time")
        val snooze = intPreferencesKey("notif_snooze")
        val quietEnabled = booleanPreferencesKey("quiet_enabled")
        val quietStart = intPreferencesKey("quiet_start")
        val quietEnd = intPreferencesKey("quiet_end")
        val pin = booleanPreferencesKey("pin_enabled")
        val biometric = booleanPreferencesKey("biometric_enabled")
        val lockTimeout = intPreferencesKey("lock_timeout")
        val onboarding = booleanPreferencesKey("onboarding_done")
    }

    private companion object {
        val INT_KEYS = setOf("first_day", "routine_reset", "notif_summary_time", "notif_snooze", "quiet_start", "quiet_end", "lock_timeout")
        val LONG_KEYS = setOf("accent")
        val FLOAT_KEYS = setOf("font_scale")
        val BOOL_KEYS = setOf(
            "dynamic_color", "confirm_delete", "notif_enabled", "notif_tasks", "notif_overdue", "notif_upcoming",
            "notif_checklists", "notif_routines", "quiet_enabled", "pin_enabled", "biometric_enabled", "onboarding_done",
        )

        inline fun <reified E : Enum<E>> String?.enumOr(default: E): E = enumValues<E>().firstOrNull { it.name == this } ?: default
        fun Int?.time(default: LocalTime): LocalTime = this?.takeIf { it in 0 until 1440 }?.let { LocalTime.of(it / 60, it % 60) } ?: default
        fun LocalTime.minutes(): Int = hour * 60 + minute

        fun Preferences.toSettings(): AppSettings {
            val d = AppSettings()
            val navOrder = this[K.navOrder]?.split(',')?.mapNotNull { n -> NavItem.entries.firstOrNull { it.name == n } }
                ?.let { saved -> saved + NavItem.entries.filter { it !in saved } } ?: d.navOrder
            val dashboard = this[K.dashboard]?.split(',')?.mapNotNull { entry ->
                val (name, visible) = entry.split(':').let { it.getOrNull(0) to it.getOrNull(1) }
                DashboardCard.entries.firstOrNull { it.name == name }?.let { DashboardCardConfig(it, visible != "0") }
            }?.let { saved -> saved + DashboardCard.entries.filter { c -> saved.none { it.card == c } }.map { DashboardCardConfig(it, true) } }
                ?: d.dashboardCards
            return AppSettings(
                themeMode = this[K.theme].enumOr(d.themeMode),
                accentColor = this[K.accent] ?: d.accentColor,
                dynamicColor = this[K.dynamic] ?: d.dynamicColor,
                fontScale = (this[K.fontScale] ?: d.fontScale).coerceIn(0.8f, 1.4f),
                density = this[K.density].enumOr(d.density),
                firstDayOfWeek = this[K.firstDay]?.takeIf { it in 1..7 }?.let(DayOfWeek::of) ?: d.firstDayOfWeek,
                dateFormat = this[K.dateFormat].enumOr(d.dateFormat),
                timeFormat = this[K.timeFormat].enumOr(d.timeFormat),
                routineResetTime = this[K.resetTime].time(d.routineResetTime),
                confirmDeletion = this[K.confirmDelete] ?: d.confirmDeletion,
                startDestination = this[K.start].enumOr(d.startDestination),
                navOrder = navOrder,
                dashboardCards = dashboard,
                defaultTaskSort = this[K.taskSort].enumOr(d.defaultTaskSort),
                defaultNoteSort = this[K.noteSort].enumOr(d.defaultNoteSort),
                notifications = NotificationPrefs(
                    enabled = this[K.notifEnabled] ?: d.notifications.enabled,
                    taskReminders = this[K.notifTasks] ?: d.notifications.taskReminders,
                    overdueAlerts = this[K.notifOverdue] ?: d.notifications.overdueAlerts,
                    upcomingDeadlines = this[K.notifUpcoming] ?: d.notifications.upcomingDeadlines,
                    checklistAlerts = this[K.notifChecklists] ?: d.notifications.checklistAlerts,
                    routineAlerts = this[K.notifRoutines] ?: d.notifications.routineAlerts,
                    dailySummaryTime = this[K.summaryTime].time(d.notifications.dailySummaryTime),
                    defaultSnoozeMinutes = (this[K.snooze] ?: d.notifications.defaultSnoozeMinutes).coerceIn(1, 24 * 60),
                    quietHours = QuietHours(
                        enabled = this[K.quietEnabled] ?: d.notifications.quietHours.enabled,
                        start = this[K.quietStart].time(d.notifications.quietHours.start),
                        end = this[K.quietEnd].time(d.notifications.quietHours.end),
                    ),
                ),
                pinEnabled = this[K.pin] ?: d.pinEnabled,
                biometricEnabled = this[K.biometric] ?: d.biometricEnabled,
                lockTimeoutSeconds = this[K.lockTimeout] ?: d.lockTimeoutSeconds,
                onboardingDone = this[K.onboarding] ?: d.onboardingDone,
            )
        }

        fun MutablePreferences.write(s: AppSettings) {
            this[K.theme] = s.themeMode.name
            this[K.accent] = s.accentColor
            this[K.dynamic] = s.dynamicColor
            this[K.fontScale] = s.fontScale
            this[K.density] = s.density.name
            this[K.firstDay] = s.firstDayOfWeek.value
            this[K.dateFormat] = s.dateFormat.name
            this[K.timeFormat] = s.timeFormat.name
            this[K.resetTime] = s.routineResetTime.minutes()
            this[K.confirmDelete] = s.confirmDeletion
            this[K.start] = s.startDestination.name
            this[K.navOrder] = s.navOrder.joinToString(",") { it.name }
            this[K.dashboard] = s.dashboardCards.joinToString(",") { "${it.card.name}:${if (it.visible) 1 else 0}" }
            this[K.taskSort] = s.defaultTaskSort.name
            this[K.noteSort] = s.defaultNoteSort.name
            this[K.notifEnabled] = s.notifications.enabled
            this[K.notifTasks] = s.notifications.taskReminders
            this[K.notifOverdue] = s.notifications.overdueAlerts
            this[K.notifUpcoming] = s.notifications.upcomingDeadlines
            this[K.notifChecklists] = s.notifications.checklistAlerts
            this[K.notifRoutines] = s.notifications.routineAlerts
            this[K.summaryTime] = s.notifications.dailySummaryTime.minutes()
            this[K.snooze] = s.notifications.defaultSnoozeMinutes
            this[K.quietEnabled] = s.notifications.quietHours.enabled
            this[K.quietStart] = s.notifications.quietHours.start.minutes()
            this[K.quietEnd] = s.notifications.quietHours.end.minutes()
            this[K.pin] = s.pinEnabled
            this[K.biometric] = s.biometricEnabled
            this[K.lockTimeout] = s.lockTimeoutSeconds
            this[K.onboarding] = s.onboardingDone
        }
    }
}
