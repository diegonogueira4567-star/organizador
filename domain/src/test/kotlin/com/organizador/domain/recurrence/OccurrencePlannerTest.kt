package com.organizador.domain.recurrence

import com.organizador.domain.model.OccurrenceStatus
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Simula o banco (índice único por data) para validar o comportamento ponta a ponta do planejador:
 * execuções separadas por data, histórico preservado, idempotência e robustez a fuso/horário de verão.
 */
class OccurrencePlannerTest {
    private val saoPaulo = ZoneId.of("America/Sao_Paulo")

    private class Run(val id: String, val date: LocalDate, val total: Int, var checked: Int, var status: OccurrenceStatus = OccurrenceStatus.OPEN, var closed: Boolean = false)

    private class FakeStore(val itemCount: Int) {
        val runs = LinkedHashMap<LocalDate, Run>() // chave única = (template, data)
        var generatedThrough: LocalDate? = null
        var trackingFrom: LocalDate? = null
        private var seq = 0

        fun reconcile(rule: RecurrenceRule, now: ZonedDateTime, reset: LocalTime = LocalTime.MIDNIGHT): ReconcilePlan {
            if (trackingFrom == null) trackingFrom = OccurrencePlanner.initialTrackingFrom(rule, now, reset)
            val open = runs.values.filter { !it.closed }.map { OpenOccurrence(it.id, it.date, it.total, it.checked) }
            val plan = OccurrencePlanner.plan(ReconcileInput(rule, trackingFrom!!, generatedThrough, open, now, reset))
            plan.toClose.forEach { c -> runs.values.first { it.id == c.occurrenceId }.apply { status = c.status; closed = true } }
            plan.missedToCreate.forEach { date -> runs.putIfAbsent(date, Run("o${seq++}", date, itemCount, 0, OccurrenceStatus.MISSED, closed = true)) }
            plan.activeToCreate?.let { date -> runs.putIfAbsent(date, Run("o${seq++}", date, itemCount, 0)) }
            generatedThrough = plan.generatedThrough
            return plan
        }

        fun current(): Run? = runs.values.lastOrNull { !it.closed }
    }

    private fun at(date: String, time: String = "10:00", zone: ZoneId = saoPaulo) =
        ZonedDateTime.of(LocalDateTime.of(LocalDate.parse(date), LocalTime.parse(time)), zone)

    @Test
    fun `rotina de sabado registra progresso parcial e reinicia na proxima execucao`() {
        val rule = RecurrencePreset.SATURDAYS.build("r", LocalDate.parse("2026-11-01"))
        val store = FakeStore(itemCount = 5)

        store.reconcile(rule, at("2026-11-07", "08:00")) // sábado
        val saturday = store.current()!!
        assertEquals(LocalDate.parse("2026-11-07"), saturday.date)
        saturday.checked = 3 // conclui 3 de 5

        store.reconcile(rule, at("2026-11-10")) // terça: mesma execução continua aberta
        assertEquals(saturday.id, store.current()!!.id)

        store.reconcile(rule, at("2026-11-14", "09:00")) // sábado seguinte
        assertEquals(OccurrenceStatus.PARTIAL, saturday.status)
        assertEquals(3, saturday.checked) // histórico preservado
        val next = store.current()!!
        assertEquals(LocalDate.parse("2026-11-14"), next.date)
        assertEquals(0, next.checked) // começa com todos os itens pendentes
        assertEquals(2, store.runs.size)
    }

    @Test
    fun `checklist diario fecha completo ou perdido e nao gera futuro`() {
        val rule = RecurrencePreset.EVERY_DAY.build("r", LocalDate.parse("2026-11-02"))
        val store = FakeStore(itemCount = 2)
        store.reconcile(rule, at("2026-11-02"))
        store.current()!!.checked = 2
        // Usuário fica 3 dias sem abrir o app.
        store.reconcile(rule, at("2026-11-05", "07:00"))
        assertEquals(OccurrenceStatus.COMPLETED, store.runs[LocalDate.parse("2026-11-02")]!!.status)
        assertEquals(OccurrenceStatus.MISSED, store.runs[LocalDate.parse("2026-11-03")]!!.status)
        assertEquals(OccurrenceStatus.MISSED, store.runs[LocalDate.parse("2026-11-04")]!!.status)
        assertEquals(LocalDate.parse("2026-11-05"), store.current()!!.date)
        assertTrue(store.runs.keys.none { it.isAfter(LocalDate.parse("2026-11-05")) })
    }

    @Test
    fun `checklist semanal permanece aberto durante a semana`() {
        val rule = RecurrenceRule("r", Frequency.WEEKLY, daysOfWeek = setOf(DayOfWeek.MONDAY), startDate = LocalDate.parse("2026-11-02"))
        val store = FakeStore(itemCount = 4)
        store.reconcile(rule, at("2026-11-02"))
        store.reconcile(rule, at("2026-11-08", "23:59"))
        assertEquals(1, store.runs.size)
        assertTrue(!store.current()!!.closed)
        store.reconcile(rule, at("2026-11-09", "00:00"))
        assertEquals(OccurrenceStatus.MISSED, store.runs[LocalDate.parse("2026-11-02")]!!.status)
        assertEquals(LocalDate.parse("2026-11-09"), store.current()!!.date)
    }

    @Test
    fun `dias personalizados`() {
        val rule = RecurrencePreset.CUSTOM_WEEKDAYS.build("r", LocalDate.parse("2026-11-02"), customDays = setOf(DayOfWeek.TUESDAY, DayOfWeek.THURSDAY))
        val store = FakeStore(itemCount = 1)
        store.reconcile(rule, at("2026-11-02")) // segunda: nada ainda
        assertNull(store.current())
        store.reconcile(rule, at("2026-11-03"))
        assertEquals(LocalDate.parse("2026-11-03"), store.current()!!.date)
        store.reconcile(rule, at("2026-11-06"))
        assertEquals(LocalDate.parse("2026-11-05"), store.current()!!.date)
        assertEquals(setOf(LocalDate.parse("2026-11-03"), LocalDate.parse("2026-11-05")), store.runs.keys)
    }

    @Test
    fun `geracao e idempotente`() {
        val rule = RecurrencePreset.EVERY_DAY.build("r", LocalDate.parse("2026-11-02"))
        val store = FakeStore(itemCount = 1)
        val now = at("2026-11-04")
        store.reconcile(rule, at("2026-11-02"))
        store.reconcile(rule, now)
        val snapshot = store.runs.keys.toList()
        repeat(5) { store.reconcile(rule, now) }
        // Mesmo sem a otimização generatedThrough, o planejador não duplica execuções abertas.
        store.generatedThrough = null
        store.reconcile(rule, now)
        assertEquals(snapshot, store.runs.keys.toList())
    }

    @Test
    fun `horario de reinicializacao mantem o dia anterior ate o horario configurado`() {
        val rule = RecurrencePreset.EVERY_DAY.build("r", LocalDate.parse("2026-11-02"))
        val store = FakeStore(itemCount = 1)
        val reset = LocalTime.of(4, 0)
        store.reconcile(rule, at("2026-11-02", "23:00"), reset)
        store.reconcile(rule, at("2026-11-03", "02:30"), reset)
        assertEquals(LocalDate.parse("2026-11-02"), store.current()!!.date)
        store.reconcile(rule, at("2026-11-03", "04:00"), reset)
        assertEquals(LocalDate.parse("2026-11-03"), store.current()!!.date)
    }

    @Test
    fun `mudanca de fuso horario nao duplica nem perde execucoes`() {
        val rule = RecurrencePreset.EVERY_DAY.build("r", LocalDate.parse("2026-11-02"))
        val store = FakeStore(itemCount = 1)
        store.reconcile(rule, at("2026-11-02", "22:00")) // São Paulo
        // Viagem para Tóquio: já é dia 3 lá (+12h).
        val tokyo = at("2026-11-02", "22:00").withZoneSameInstant(ZoneId.of("Asia/Tokyo"))
        store.reconcile(rule, tokyo)
        assertEquals(setOf(LocalDate.parse("2026-11-02"), LocalDate.parse("2026-11-03")), store.runs.keys)
        // Volta para São Paulo no mesmo instante: data local volta a 2; nada é recriado ou reaberto.
        store.reconcile(rule, at("2026-11-02", "22:00"))
        assertEquals(2, store.runs.size)
        assertEquals(1, store.runs.values.count { !it.closed })
    }

    @Test
    fun `horario de verao com lacuna a meia-noite`() {
        // Em 04/11/2018 o horário de verão de São Paulo começou às 00:00 (00:00 -> 01:00).
        val zone = saoPaulo
        val start = OccurrencePlanner.windowStart(LocalDate.parse("2018-11-04"), LocalTime.MIDNIGHT, zone)
        assertEquals(LocalTime.of(1, 0), start.toLocalTime())
        val rule = RecurrencePreset.EVERY_DAY.build("r", LocalDate.parse("2018-11-03"))
        val store = FakeStore(itemCount = 1)
        store.reconcile(rule, ZonedDateTime.of(LocalDate.parse("2018-11-03"), LocalTime.of(23, 30), zone))
        store.reconcile(rule, ZonedDateTime.of(LocalDate.parse("2018-11-04"), LocalTime.of(1, 30), zone))
        assertEquals(setOf(LocalDate.parse("2018-11-03"), LocalDate.parse("2018-11-04")), store.runs.keys)
        assertEquals(LocalDate.parse("2018-11-04"), store.current()!!.date)
    }

    @Test
    fun `regra desativada nao gera novas execucoes mas fecha as vencidas`() {
        val rule = RecurrencePreset.EVERY_DAY.build("r", LocalDate.parse("2026-11-02"))
        val store = FakeStore(itemCount = 1)
        store.reconcile(rule, at("2026-11-02"))
        val disabled = rule.copy(enabled = false)
        store.reconcile(disabled, at("2026-11-06"))
        assertEquals(1, store.runs.size)
        assertEquals(OccurrenceStatus.MISSED, store.runs.values.single().status)
        assertNull(store.current())
    }

    @Test
    fun `recuperacao retroativa e limitada`() {
        val rule = RecurrencePreset.EVERY_DAY.build("r", LocalDate.parse("2020-01-01"))
        val store = FakeStore(itemCount = 1)
        store.trackingFrom = LocalDate.parse("2020-01-01")
        val plan = store.reconcile(rule, at("2026-11-02"))
        assertTrue(plan.truncated)
        assertTrue(store.runs.size <= OccurrencePlanner.MAX_LOOKBACK_DAYS + 1)
    }

    @Test
    fun `inicio do acompanhamento nao registra perdas antes da criacao`() {
        val rule = RecurrencePreset.EVERY_DAY.build("r", LocalDate.parse("2026-10-01"))
        val store = FakeStore(itemCount = 1)
        store.reconcile(rule, at("2026-11-02"))
        assertEquals(listOf(LocalDate.parse("2026-11-02")), store.runs.keys.toList())

        val weekly = RecurrenceRule("w", Frequency.WEEKLY, daysOfWeek = setOf(DayOfWeek.MONDAY), startDate = LocalDate.parse("2026-11-02"))
        // Criado na quarta: a execução de segunda (janela ainda aberta) já fica disponível.
        assertEquals(LocalDate.parse("2026-11-02"), OccurrencePlanner.initialTrackingFrom(weekly, at("2026-11-04"), LocalTime.MIDNIGHT))
    }

    @Test
    fun `fim da regra fecha a ultima execucao no dia seguinte`() {
        val rule = RecurrenceRule("r", Frequency.WEEKLY, daysOfWeek = setOf(DayOfWeek.MONDAY), startDate = LocalDate.parse("2026-11-02"), endDate = LocalDate.parse("2026-11-04"))
        val end = OccurrencePlanner.windowEnd(rule, LocalDate.parse("2026-11-02"), LocalTime.MIDNIGHT, saoPaulo)
        assertEquals(LocalDate.parse("2026-11-05"), end.toLocalDate())
    }
}
