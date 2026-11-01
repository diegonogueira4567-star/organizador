package com.organizador.domain.recurrence

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

enum class Frequency {
    /** Uma única data (startDate). */
    NONE,

    /** A cada [RecurrenceRule.interval] dias (intervalo 1 = todos os dias). */
    DAILY,

    /** Nos [RecurrenceRule.daysOfWeek], a cada [RecurrenceRule.interval] semanas. */
    WEEKLY,

    /** Nos [RecurrenceRule.daysOfMonth], a cada [RecurrenceRule.interval] meses. */
    MONTHLY,

    /** Em [RecurrenceRule.monthOfYear]/[RecurrenceRule.dayOfMonth], a cada [RecurrenceRule.interval] anos. */
    YEARLY,
}

/**
 * Regra de recorrência declarativa. As datas de ocorrência são DATAS LOCAIS (calendário do usuário);
 * instantes absolutos só são derivados no momento do uso (notificação, janela de execução), o que torna
 * a identidade de uma ocorrência estável diante de mudanças de fuso horário e horário de verão.
 */
data class RecurrenceRule(
    val id: String,
    val frequency: Frequency,
    val interval: Int = 1,
    val daysOfWeek: Set<DayOfWeek> = emptySet(),
    /** 1..31; [LAST_DAY_OF_MONTH] para "último dia". Dias inexistentes no mês são ajustados para o último dia. */
    val daysOfMonth: Set<Int> = emptySet(),
    val monthOfYear: Int? = null,
    val dayOfMonth: Int? = null,
    val startDate: LocalDate,
    val endDate: LocalDate? = null,
    /** Horário de execução/aviso (opcional). */
    val timeOfDay: LocalTime? = null,
    /** Início do "dia" da rotina. null = usa a preferência global. */
    val resetTime: LocalTime? = null,
    /** null = acompanha o fuso atual do dispositivo. */
    val zoneId: String? = null,
    val enabled: Boolean = true,
) {
    init {
        require(interval >= 1) { "interval deve ser >= 1" }
        require(daysOfMonth.all { it == LAST_DAY_OF_MONTH || it in 1..31 }) { "dia do mês inválido" }
        require(monthOfYear == null || monthOfYear in 1..12) { "mês inválido" }
        require(dayOfMonth == null || dayOfMonth in 1..31) { "dia inválido" }
        require(endDate == null || !endDate.isBefore(startDate)) { "endDate anterior a startDate" }
    }

    fun zone(default: ZoneId): ZoneId = zoneId?.let { runCatching { ZoneId.of(it) }.getOrNull() } ?: default

    companion object {
        const val LAST_DAY_OF_MONTH = -1
        val WEEKDAYS: Set<DayOfWeek> = setOf(
            DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY,
        )
        val WEEKEND: Set<DayOfWeek> = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
    }
}

/** Atalhos de configuração oferecidos na interface. */
enum class RecurrencePreset {
    EVERY_DAY, WEEKDAYS, WEEKENDS, SATURDAYS, SUNDAYS, CUSTOM_WEEKDAYS, MONTHLY_DATES, YEARLY, EVERY_N_DAYS;

    fun build(
        id: String,
        startDate: LocalDate,
        customDays: Set<DayOfWeek> = emptySet(),
        monthDays: Set<Int> = emptySet(),
        everyNDays: Int = 2,
    ): RecurrenceRule = when (this) {
        EVERY_DAY -> RecurrenceRule(id, Frequency.DAILY, startDate = startDate)
        WEEKDAYS -> RecurrenceRule(id, Frequency.WEEKLY, daysOfWeek = RecurrenceRule.WEEKDAYS, startDate = startDate)
        WEEKENDS -> RecurrenceRule(id, Frequency.WEEKLY, daysOfWeek = RecurrenceRule.WEEKEND, startDate = startDate)
        SATURDAYS -> RecurrenceRule(id, Frequency.WEEKLY, daysOfWeek = setOf(DayOfWeek.SATURDAY), startDate = startDate)
        SUNDAYS -> RecurrenceRule(id, Frequency.WEEKLY, daysOfWeek = setOf(DayOfWeek.SUNDAY), startDate = startDate)
        CUSTOM_WEEKDAYS -> RecurrenceRule(
            id, Frequency.WEEKLY, daysOfWeek = customDays.ifEmpty { setOf(startDate.dayOfWeek) }, startDate = startDate,
        )
        MONTHLY_DATES -> RecurrenceRule(
            id, Frequency.MONTHLY, daysOfMonth = monthDays.ifEmpty { setOf(startDate.dayOfMonth) }, startDate = startDate,
        )
        YEARLY -> RecurrenceRule(
            id, Frequency.YEARLY, monthOfYear = startDate.monthValue, dayOfMonth = startDate.dayOfMonth, startDate = startDate,
        )
        EVERY_N_DAYS -> RecurrenceRule(id, Frequency.DAILY, interval = everyNDays.coerceAtLeast(1), startDate = startDate)
    }
}
