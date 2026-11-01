package com.organizador.domain.model

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

object ReminderMath {
    /** Horário usado para lembretes de tarefas sem horário definido (dia inteiro). */
    val ALL_DAY_TIME: LocalTime = LocalTime.of(9, 0)

    fun triggerFor(dueDate: LocalDate, dueTime: LocalTime?, offsetMinutes: Int, zone: ZoneId): Instant =
        ZonedDateTime.of(dueDate, dueTime ?: ALL_DAY_TIME, zone).minusMinutes(offsetMinutes.toLong()).toInstant()

    /** Empurra um disparo que cairia no horário silencioso para o fim desse horário. */
    fun applyQuietHours(trigger: Instant, quiet: QuietHours, zone: ZoneId): Instant {
        if (!quiet.enabled) return trigger
        val local = trigger.atZone(zone)
        if (!quiet.contains(local.toLocalTime())) return trigger
        var end = ZonedDateTime.of(local.toLocalDate(), quiet.end, zone)
        if (!end.isAfter(local)) end = end.plusDays(1)
        return end.toInstant()
    }
}
