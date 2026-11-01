package com.organizador.domain.model

import java.time.DayOfWeek
import java.time.LocalTime

enum class ThemeMode { SYSTEM, LIGHT, DARK }
enum class Density { COMPACT, COMFORTABLE, SPACIOUS }
enum class DateFormatPref { DMY, MDY, YMD }
enum class TimeFormatPref { H24, H12 }

enum class StartDestination { DASHBOARD, TODAY, INBOX, UPCOMING, CHECKLISTS, ROUTINES, NOTES, CALENDAR }

/** Cartões do painel inicial (ordem e visibilidade configuráveis). */
enum class DashboardCard { TODAY_TASKS, OVERDUE, UPCOMING, CHECKLISTS_TODAY, ROUTINES, RECENT_NOTES, DAILY_PROGRESS, WEEKLY_SUMMARY, SHORTCUTS }

/** Itens da navegação principal (ordem configurável). */
enum class NavItem { DASHBOARD, TODAY, UPCOMING, INBOX, CHECKLISTS, ROUTINES, NOTES, PROJECTS, CALENDAR, STATISTICS, SEARCH }

data class DashboardCardConfig(val card: DashboardCard, val visible: Boolean)

data class QuietHours(val enabled: Boolean = false, val start: LocalTime = LocalTime.of(22, 0), val end: LocalTime = LocalTime.of(7, 0)) {
    fun contains(time: LocalTime): Boolean = enabled && if (start <= end) {
        time >= start && time < end
    } else {
        time >= start || time < end
    }
}

data class NotificationPrefs(
    val enabled: Boolean = true,
    val taskReminders: Boolean = true,
    val overdueAlerts: Boolean = true,
    val upcomingDeadlines: Boolean = true,
    val checklistAlerts: Boolean = true,
    val routineAlerts: Boolean = true,
    val dailySummaryTime: LocalTime = LocalTime.of(8, 0),
    val defaultSnoozeMinutes: Int = 15,
    val quietHours: QuietHours = QuietHours(),
)

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val accentColor: Long = DEFAULT_ACCENT,
    val dynamicColor: Boolean = false,
    val fontScale: Float = 1.0f,
    val density: Density = Density.COMFORTABLE,
    val firstDayOfWeek: DayOfWeek = DayOfWeek.SUNDAY,
    val dateFormat: DateFormatPref = DateFormatPref.DMY,
    val timeFormat: TimeFormatPref = TimeFormatPref.H24,
    val routineResetTime: LocalTime = LocalTime.MIDNIGHT,
    val confirmDeletion: Boolean = true,
    val startDestination: StartDestination = StartDestination.DASHBOARD,
    val navOrder: List<NavItem> = NavItem.entries,
    val dashboardCards: List<DashboardCardConfig> = DEFAULT_DASHBOARD.map { DashboardCardConfig(it, true) },
    val defaultTaskSort: TaskSort = TaskSort.DUE_DATE,
    val defaultNoteSort: NoteSort = NoteSort.UPDATED,
    val notifications: NotificationPrefs = NotificationPrefs(),
    val pinEnabled: Boolean = false,
    val biometricEnabled: Boolean = false,
    val lockTimeoutSeconds: Int = 60,
    val onboardingDone: Boolean = false,
) {
    companion object {
        const val DEFAULT_ACCENT: Long = 0xFFDC4C3E
        val DEFAULT_DASHBOARD: List<DashboardCard> = listOf(
            DashboardCard.DAILY_PROGRESS, DashboardCard.OVERDUE, DashboardCard.TODAY_TASKS, DashboardCard.ROUTINES,
            DashboardCard.CHECKLISTS_TODAY, DashboardCard.UPCOMING, DashboardCard.RECENT_NOTES, DashboardCard.WEEKLY_SUMMARY, DashboardCard.SHORTCUTS,
        )
        val ACCENTS: List<Long> = listOf(0xFFDC4C3E, 0xFFE8743B, 0xFF2E7D32, 0xFF1E88E5, 0xFF5E35B1, 0xFFD81B60, 0xFF00897B, 0xFF6D4C41)
    }
}
