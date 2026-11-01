package com.organizador.app.ui.common

import com.organizador.domain.model.DateFormatPref
import com.organizador.domain.model.TimeFormatPref
import com.organizador.domain.recurrence.Frequency
import com.organizador.domain.recurrence.RecurrenceRule
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

/** Formatação em português, respeitando as preferências de data/hora do usuário. */
object Formats {
    val locale: Locale = Locale("pt", "BR")

    fun date(date: LocalDate, pref: DateFormatPref = DateFormatPref.DMY): String = when (pref) {
        DateFormatPref.DMY -> DateTimeFormatter.ofPattern("dd/MM/yyyy", locale)
        DateFormatPref.MDY -> DateTimeFormatter.ofPattern("MM/dd/yyyy", locale)
        DateFormatPref.YMD -> DateTimeFormatter.ofPattern("yyyy-MM-dd", locale)
    }.format(date)

    fun shortDate(date: LocalDate, today: LocalDate): String =
        if (date.year == today.year) DateTimeFormatter.ofPattern("d MMM", locale).format(date) else DateTimeFormatter.ofPattern("d MMM yyyy", locale).format(date)

    fun time(time: LocalTime, pref: TimeFormatPref = TimeFormatPref.H24): String = when (pref) {
        TimeFormatPref.H24 -> DateTimeFormatter.ofPattern("HH:mm", locale)
        TimeFormatPref.H12 -> DateTimeFormatter.ofPattern("h:mm a", locale)
    }.format(time)

    fun dateTime(instant: Instant, zone: ZoneId, datePref: DateFormatPref, timePref: TimeFormatPref): String {
        val z = instant.atZone(zone)
        return date(z.toLocalDate(), datePref) + " " + time(z.toLocalTime(), timePref)
    }

    /** "Hoje", "Amanhã", "Ontem", dia da semana para a próxima semana, ou data curta. */
    fun relativeDate(date: LocalDate, today: LocalDate): String {
        val diff = ChronoUnit.DAYS.between(today, date)
        return when {
            diff == 0L -> "Hoje"
            diff == 1L -> "Amanhã"
            diff == -1L -> "Ontem"
            diff in 2..6 -> weekday(date.dayOfWeek).replaceFirstChar { it.uppercase() }
            else -> shortDate(date, today)
        }
    }

    fun weekday(day: DayOfWeek, short: Boolean = false): String =
        day.getDisplayName(if (short) TextStyle.SHORT else TextStyle.FULL, locale)

    fun weekdayLetter(day: DayOfWeek): String = weekday(day, short = true).take(3).replaceFirstChar { it.uppercase() }

    fun monthYear(date: LocalDate): String =
        DateTimeFormatter.ofPattern("MMMM yyyy", locale).format(date).replaceFirstChar { it.uppercase() }

    fun longDate(date: LocalDate): String =
        DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM", locale).format(date).replaceFirstChar { it.uppercase() }

    /** Descrição legível da regra de recorrência. */
    fun recurrence(rule: RecurrenceRule): String {
        val base = when (rule.frequency) {
            Frequency.NONE -> "Uma vez"
            Frequency.DAILY -> if (rule.interval == 1) "Todos os dias" else "A cada ${rule.interval} dias"
            Frequency.WEEKLY -> {
                val days = rule.daysOfWeek.ifEmpty { setOf(rule.startDate.dayOfWeek) }
                val label = when (days) {
                    RecurrenceRule.WEEKDAYS -> "de segunda a sexta"
                    RecurrenceRule.WEEKEND -> "nos fins de semana"
                    DayOfWeek.entries.toSet() -> "todos os dias"
                    else -> days.sortedBy { it.value }.joinToString(", ") { weekday(it, short = true) }
                }
                if (rule.interval == 1) "Semanal: $label" else "A cada ${rule.interval} semanas: $label"
            }
            Frequency.MONTHLY -> {
                val days = rule.daysOfMonth.ifEmpty { setOf(rule.startDate.dayOfMonth) }.sorted()
                    .joinToString(", ") { if (it == RecurrenceRule.LAST_DAY_OF_MONTH) "último dia" else "dia $it" }
                if (rule.interval == 1) "Mensal: $days" else "A cada ${rule.interval} meses: $days"
            }
            Frequency.YEARLY -> {
                val month = rule.monthOfYear ?: rule.startDate.monthValue
                val day = rule.dayOfMonth ?: rule.startDate.dayOfMonth
                // 2000 é bissexto: 29 de fevereiro é representável.
                val sample = LocalDate.of(2000, month, day.coerceAtMost(java.time.YearMonth.of(2000, month).lengthOfMonth()))
                val label = DateTimeFormatter.ofPattern("d 'de' MMMM", locale).format(sample)
                if (rule.interval == 1) "Anual: $label" else "A cada ${rule.interval} anos: $label"
            }
        }
        val time = rule.timeOfDay?.let { " às ${time(it)}" }.orEmpty()
        val paused = if (!rule.enabled) " (pausada)" else ""
        return base + time + paused
    }

    fun fileSize(bytes: Long): String = when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        else -> String.format(locale, "%.1f MB", bytes / (1024.0 * 1024.0))
    }
}
