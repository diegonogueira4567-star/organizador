package com.organizador.domain.recurrence

import java.time.DayOfWeek
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RecurrenceEngineTest {
    private val start = LocalDate.of(2026, 11, 2) // segunda-feira

    private fun d(day: String) = LocalDate.parse(day)

    @Test
    fun `diario gera todos os dias a partir do inicio`() {
        val rule = RecurrencePreset.EVERY_DAY.build("r", start)
        assertEquals(
            listOf(d("2026-11-02"), d("2026-11-03"), d("2026-11-04")),
            RecurrenceEngine.occurrencesBetween(rule, d("2026-10-30"), d("2026-11-04")),
        )
        assertNull(RecurrenceEngine.previousOnOrBefore(rule, d("2026-11-01")))
    }

    @Test
    fun `segunda a sexta ignora fim de semana`() {
        val rule = RecurrencePreset.WEEKDAYS.build("r", start)
        val dates = RecurrenceEngine.occurrencesBetween(rule, start, start.plusDays(13))
        assertEquals(10, dates.size)
        assertTrue(dates.none { it.dayOfWeek == DayOfWeek.SATURDAY || it.dayOfWeek == DayOfWeek.SUNDAY })
        assertEquals(d("2026-11-09"), RecurrenceEngine.nextAfter(rule, d("2026-11-06")))
    }

    @Test
    fun `finais de semana e sabados`() {
        val weekend = RecurrencePreset.WEEKENDS.build("r", start)
        assertEquals(listOf(d("2026-11-07"), d("2026-11-08")), RecurrenceEngine.occurrencesBetween(weekend, start, start.plusDays(6)))
        val saturday = RecurrencePreset.SATURDAYS.build("r", start)
        assertEquals(
            listOf(d("2026-11-07"), d("2026-11-14"), d("2026-11-21")),
            RecurrenceEngine.occurrencesBetween(saturday, start, d("2026-11-27")),
        )
        assertEquals(d("2026-11-14"), RecurrenceEngine.previousOnOrBefore(saturday, d("2026-11-20")))
        val sunday = RecurrencePreset.SUNDAYS.build("r", start)
        assertEquals(d("2026-11-08"), RecurrenceEngine.nextOnOrAfter(sunday, start))
    }

    @Test
    fun `dias da semana personalizados`() {
        val rule = RecurrencePreset.CUSTOM_WEEKDAYS.build("r", start, customDays = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY))
        assertEquals(
            listOf(d("2026-11-02"), d("2026-11-04"), d("2026-11-06"), d("2026-11-09")),
            RecurrenceEngine.occurrencesBetween(rule, start, d("2026-11-09")),
        )
    }

    @Test
    fun `semanal a cada duas semanas respeita a semana de ancoragem`() {
        val rule = RecurrenceRule("r", Frequency.WEEKLY, interval = 2, daysOfWeek = setOf(DayOfWeek.TUESDAY), startDate = start)
        assertEquals(
            listOf(d("2026-11-03"), d("2026-11-17"), d("2026-12-01")),
            RecurrenceEngine.occurrencesBetween(rule, start, d("2026-12-07")),
        )
        assertFalse(RecurrenceEngine.matches(rule, d("2026-11-10")))
    }

    @Test
    fun `mensal dia 31 ajusta para o ultimo dia de meses curtos`() {
        val rule = RecurrencePreset.MONTHLY_DATES.build("r", d("2027-01-01"), monthDays = setOf(31))
        assertEquals(
            listOf(d("2027-01-31"), d("2027-02-28"), d("2027-03-31"), d("2027-04-30")),
            RecurrenceEngine.occurrencesBetween(rule, d("2027-01-01"), d("2027-04-30")),
        )
    }

    @Test
    fun `mensal com ultimo dia e varias datas`() {
        val rule = RecurrenceRule("r", Frequency.MONTHLY, daysOfMonth = setOf(1, 15, RecurrenceRule.LAST_DAY_OF_MONTH), startDate = d("2028-02-01"))
        assertEquals(
            listOf(d("2028-02-01"), d("2028-02-15"), d("2028-02-29"), d("2028-03-01")),
            RecurrenceEngine.occurrencesBetween(rule, d("2028-02-01"), d("2028-03-01")),
        )
    }

    @Test
    fun `anual em 29 de fevereiro usa 28 em anos nao bissextos`() {
        val rule = RecurrencePreset.YEARLY.build("r", d("2028-02-29"))
        assertEquals(
            listOf(d("2028-02-29"), d("2029-02-28"), d("2030-02-28"), d("2031-02-28"), d("2032-02-29")),
            RecurrenceEngine.occurrencesBetween(rule, d("2028-01-01"), d("2032-12-31")),
        )
    }

    @Test
    fun `a cada N dias e data final`() {
        val rule = RecurrenceRule("r", Frequency.DAILY, interval = 3, startDate = start, endDate = d("2026-11-12"))
        assertEquals(
            listOf(d("2026-11-02"), d("2026-11-05"), d("2026-11-08"), d("2026-11-11")),
            RecurrenceEngine.occurrencesBetween(rule, d("2026-10-01"), d("2027-01-01")),
        )
        assertNull(RecurrenceEngine.nextAfter(rule, d("2026-11-11")))
        assertEquals(d("2026-11-08"), RecurrenceEngine.previousOnOrBefore(rule, d("2026-11-10")))
        assertEquals(d("2026-11-11"), RecurrenceEngine.previousOnOrBefore(rule, d("2027-05-01")))
    }

    @Test
    fun `data unica`() {
        val rule = RecurrenceRule("r", Frequency.NONE, startDate = start)
        assertEquals(start, RecurrenceEngine.nextOnOrAfter(rule, d("2026-01-01")))
        assertNull(RecurrenceEngine.nextAfter(rule, start))
    }

    @Test
    fun `busca de data distante tem custo limitado`() {
        val rule = RecurrencePreset.SATURDAYS.build("r", d("2000-01-01"))
        // 100 anos depois: deve encontrar diretamente, sem materializar milhares de datas.
        val begin = System.nanoTime()
        assertEquals(d("2100-01-02"), RecurrenceEngine.nextOnOrAfter(rule, d("2099-12-28")))
        assertTrue(System.nanoTime() - begin < 50_000_000)
    }

    @Test
    fun `occurrencesBetween respeita limite`() {
        val rule = RecurrencePreset.EVERY_DAY.build("r", start)
        assertEquals(5, RecurrenceEngine.occurrencesBetween(rule, start, start.plusYears(10), limit = 5).size)
    }

    @Test
    fun `projecao de tarefa recorrente comeca depois do prazo atual`() {
        val rule = RecurrencePreset.WEEKDAYS.build("r", start)
        // Instância atual vence na sexta 06/11; o calendário de 02 a 12/11 prevê segunda a quinta seguintes.
        assertEquals(
            listOf(d("2026-11-09"), d("2026-11-10"), d("2026-11-11"), d("2026-11-12")),
            RecurrenceEngine.projectedAfter(rule, d("2026-11-06"), d("2026-11-02"), d("2026-11-12")),
        )
        assertTrue(RecurrenceEngine.projectedAfter(rule.copy(enabled = false), d("2026-11-06"), d("2026-11-02"), d("2026-11-12")).isEmpty())
    }
}
