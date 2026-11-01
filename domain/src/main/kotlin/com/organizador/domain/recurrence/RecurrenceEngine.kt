package com.organizador.domain.recurrence

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/**
 * Cálculo puro de datas de ocorrência. Não gera listas infinitas: toda busca é limitada pelo período
 * máximo da regra, garantindo custo constante independentemente da distância da data inicial.
 */
object RecurrenceEngine {

    /** A data pertence à regra? */
    fun matches(rule: RecurrenceRule, date: LocalDate): Boolean {
        if (date.isBefore(rule.startDate)) return false
        if (rule.endDate != null && date.isAfter(rule.endDate)) return false
        return when (rule.frequency) {
            Frequency.NONE -> date == rule.startDate
            Frequency.DAILY -> Math.floorMod(ChronoUnit.DAYS.between(rule.startDate, date), rule.interval.toLong()) == 0L
            Frequency.WEEKLY -> {
                val days = rule.daysOfWeek.ifEmpty { setOf(rule.startDate.dayOfWeek) }
                date.dayOfWeek in days &&
                    Math.floorMod(ChronoUnit.WEEKS.between(mondayOf(rule.startDate), mondayOf(date)), rule.interval.toLong()) == 0L
            }
            Frequency.MONTHLY -> {
                val months = ChronoUnit.MONTHS.between(YearMonth.from(rule.startDate), YearMonth.from(date))
                if (Math.floorMod(months, rule.interval.toLong()) != 0L) return false
                val length = date.lengthOfMonth()
                val days = rule.daysOfMonth.ifEmpty { setOf(rule.startDate.dayOfMonth) }
                days.any { effectiveDay(it, length) == date.dayOfMonth }
            }
            Frequency.YEARLY -> {
                if (Math.floorMod((date.year - rule.startDate.year).toLong(), rule.interval.toLong()) != 0L) return false
                val month = rule.monthOfYear ?: rule.startDate.monthValue
                val day = rule.dayOfMonth ?: rule.startDate.dayOfMonth
                date.monthValue == month && date.dayOfMonth == effectiveDay(day, YearMonth.of(date.year, month).lengthOfMonth())
            }
        }
    }

    /** Primeira ocorrência em ou após [date], ou null se a regra terminou. */
    fun nextOnOrAfter(rule: RecurrenceRule, date: LocalDate): LocalDate? {
        var cursor = if (date.isBefore(rule.startDate)) rule.startDate else date
        if (rule.frequency == Frequency.NONE) return rule.startDate.takeIf { !cursor.isAfter(it) }
        if (rule.frequency == Frequency.DAILY) {
            // Salto direto para o próximo múltiplo do intervalo.
            val offset = Math.floorMod(ChronoUnit.DAYS.between(rule.startDate, cursor), rule.interval.toLong())
            if (offset != 0L) cursor = cursor.plusDays(rule.interval - offset)
            return cursor.takeIf { rule.endDate == null || !it.isAfter(rule.endDate) }
        }
        val limit = cursor.plusDays(searchHorizonDays(rule))
        while (!cursor.isAfter(limit)) {
            if (rule.endDate != null && cursor.isAfter(rule.endDate)) return null
            if (matches(rule, cursor)) return cursor
            cursor = cursor.plusDays(1)
        }
        return null
    }

    /** Primeira ocorrência estritamente após [date]. */
    fun nextAfter(rule: RecurrenceRule, date: LocalDate): LocalDate? = nextOnOrAfter(rule, date.plusDays(1))

    /** Última ocorrência em ou antes de [date], ou null se não houver. */
    fun previousOnOrBefore(rule: RecurrenceRule, date: LocalDate): LocalDate? {
        if (date.isBefore(rule.startDate)) return null
        var cursor = if (rule.endDate != null && date.isAfter(rule.endDate)) rule.endDate else date
        if (rule.frequency == Frequency.NONE) return rule.startDate
        if (rule.frequency == Frequency.DAILY) {
            val offset = Math.floorMod(ChronoUnit.DAYS.between(rule.startDate, cursor), rule.interval.toLong())
            return cursor.minusDays(offset).takeIf { !it.isBefore(rule.startDate) }
        }
        val limit = cursor.minusDays(searchHorizonDays(rule))
        while (!cursor.isBefore(limit) && !cursor.isBefore(rule.startDate)) {
            if (matches(rule, cursor)) return cursor
            cursor = cursor.minusDays(1)
        }
        return null
    }

    /** Ocorrências em [from, to] (inclusive), limitadas a [limit] itens a partir do início do intervalo. */
    fun occurrencesBetween(rule: RecurrenceRule, from: LocalDate, to: LocalDate, limit: Int = 1000): List<LocalDate> {
        if (to.isBefore(from)) return emptyList()
        val result = ArrayList<LocalDate>()
        var next = nextOnOrAfter(rule, from)
        while (next != null && !next.isAfter(to) && result.size < limit) {
            result += next
            next = nextAfter(rule, next)
        }
        return result
    }

    /**
     * Instâncias futuras previstas de uma tarefa recorrente: datas da regra estritamente após o prazo atual
     * (a instância existente) dentro de [from, to]. Usado só para exibição; nada é gravado.
     */
    fun projectedAfter(rule: RecurrenceRule, currentDue: LocalDate, from: LocalDate, to: LocalDate, limit: Int = 62): List<LocalDate> {
        if (!rule.enabled) return emptyList()
        val start = maxOf(from, currentDue.plusDays(1))
        return occurrencesBetween(rule, start, to, limit)
    }

    /** Ajusta "dia 31" para o último dia de meses curtos; [RecurrenceRule.LAST_DAY_OF_MONTH] = último dia. */
    internal fun effectiveDay(day: Int, lengthOfMonth: Int): Int =
        if (day == RecurrenceRule.LAST_DAY_OF_MONTH) lengthOfMonth else minOf(day, lengthOfMonth)

    private fun mondayOf(date: LocalDate): LocalDate = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    /** Maior distância possível entre duas ocorrências consecutivas da regra. */
    private fun searchHorizonDays(rule: RecurrenceRule): Long = when (rule.frequency) {
        Frequency.NONE -> 0
        Frequency.DAILY -> rule.interval.toLong()
        Frequency.WEEKLY -> 7L * rule.interval + 7
        Frequency.MONTHLY -> 31L * rule.interval + 31
        Frequency.YEARLY -> 366L * rule.interval + 366
    }
}
