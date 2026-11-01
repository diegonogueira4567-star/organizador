package com.organizador.domain.usecase

import com.organizador.domain.model.QuietHours
import com.organizador.domain.recurrence.RecurrencePreset
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CompleteTaskRulesTest {
    @Test
    fun `proxima data parte do prazo atual`() {
        val rule = RecurrencePreset.WEEKDAYS.build("r", LocalDate.parse("2026-11-02"))
        assertEquals(
            LocalDate.parse("2026-11-09"),
            CompleteTaskUseCase.nextDueDate(rule, LocalDate.parse("2026-11-06"), today = LocalDate.parse("2026-11-06")),
        )
    }

    @Test
    fun `tarefa muito atrasada vai para hoje em diante`() {
        val rule = RecurrencePreset.EVERY_DAY.build("r", LocalDate.parse("2026-10-01"))
        assertEquals(
            LocalDate.parse("2026-11-20"),
            CompleteTaskUseCase.nextDueDate(rule, LocalDate.parse("2026-10-05"), today = LocalDate.parse("2026-11-20")),
        )
    }

    @Test
    fun `regra encerrada nao gera nova instancia`() {
        val rule = RecurrencePreset.EVERY_DAY.build("r", LocalDate.parse("2026-11-01")).copy(endDate = LocalDate.parse("2026-11-03"))
        assertNull(CompleteTaskUseCase.nextDueDate(rule, LocalDate.parse("2026-11-03"), LocalDate.parse("2026-11-03")))
    }

    @Test
    fun `horario silencioso atravessando a meia-noite`() {
        val quiet = QuietHours(enabled = true, start = LocalTime.of(22, 0), end = LocalTime.of(7, 0))
        assertTrue(quiet.contains(LocalTime.of(23, 0)))
        assertTrue(quiet.contains(LocalTime.of(6, 59)))
        assertFalse(quiet.contains(LocalTime.of(7, 0)))
        assertFalse(quiet.contains(LocalTime.of(12, 0)))
        assertFalse(quiet.copy(enabled = false).contains(LocalTime.of(23, 0)))
    }
}
