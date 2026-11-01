package com.organizador.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.organizador.data.local.OrganizadorDatabase
import com.organizador.data.local.entity.ChecklistOccurrenceEntity
import com.organizador.domain.model.ChecklistDraft
import com.organizador.domain.model.ChecklistItemDraft
import com.organizador.domain.model.ChecklistKind
import com.organizador.domain.model.FixedTimeProvider
import com.organizador.domain.model.OccurrenceStatus
import com.organizador.domain.recurrence.Frequency
import com.organizador.domain.recurrence.RecurrencePreset
import com.organizador.domain.recurrence.RecurrenceRule
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Testes de recorrência ponta a ponta sobre o banco real (Room/SQLite no aparelho). */
@RunWith(AndroidJUnit4::class)
class ChecklistRecurrenceDbTest {
    private lateinit var g: TestGraph

    @Before
    fun setUp() {
        g = TestGraph.inMemory()
    }

    @After
    fun tearDown() = g.db.close()

    private fun items(n: Int) = (1..n).map { ChecklistItemDraft(title = "Atividade $it") }

    private suspend fun createRoutine(rule: RecurrenceRule, itemCount: Int = 5, title: String = "Rotina"): String =
        g.saveChecklist(ChecklistDraft(title = title, kind = ChecklistKind.RECURRING, isRoutine = true, items = items(itemCount), recurrence = rule))

    private suspend fun current(id: String) = g.checklists.observeCurrentOccurrence(id).first()

    @Test
    fun rotinaDeSabado_registraParcialEReiniciaNaProximaExecucao() = runBlocking {
        g.setNow("2026-11-04T09:00") // quarta
        val id = createRoutine(RecurrencePreset.SATURDAYS.build("", LocalDate.parse("2026-11-04")))
        assertNull("antes de sábado não há execução aberta", current(id))

        g.setNow("2026-11-07T08:00") // sábado
        g.reconcile()
        val saturday = current(id)!!
        assertEquals(LocalDate.parse("2026-11-07"), saturday.occurrence.date)
        assertEquals(5, saturday.states.size)
        saturday.states.take(3).forEach { g.checklists.setItemChecked(it.id, true, g.time.now()) }

        g.setNow("2026-11-14T08:00") // sábado seguinte
        g.reconcile()
        val history = g.checklists.observeHistory(id).first()
        val previous = history.first { it.date == LocalDate.parse("2026-11-07") }
        assertEquals(OccurrenceStatus.PARTIAL, previous.status)
        assertEquals(3, previous.checkedItems)
        assertEquals(5, previous.totalItems)
        // Marcações antigas permanecem no histórico.
        val oldStates = g.checklists.observeOccurrence(previous.id).first()!!.states
        assertEquals(3, oldStates.count { it.checked })

        val next = current(id)!!
        assertEquals(LocalDate.parse("2026-11-14"), next.occurrence.date)
        assertEquals(0, next.states.count { it.checked })
        assertEquals(5, next.states.size)
    }

    @Test
    fun checklistDiario_execucoesPerdidasSaoRegistradasEFuturasNao() = runBlocking {
        g.setNow("2026-11-02T10:00")
        val id = createRoutine(RecurrencePreset.EVERY_DAY.build("", LocalDate.parse("2026-11-02")), itemCount = 2)
        current(id)!!.states.forEach { g.checklists.setItemChecked(it.id, true, g.time.now()) }
        assertEquals(OccurrenceStatus.COMPLETED, current(id)!!.occurrence.status)

        g.setNow("2026-11-06T07:00") // 4 dias sem abrir o app
        g.reconcile()
        val history = g.checklists.observeHistory(id).first().associateBy { it.date }
        assertEquals(OccurrenceStatus.COMPLETED, history[LocalDate.parse("2026-11-02")]!!.status)
        assertEquals(OccurrenceStatus.MISSED, history[LocalDate.parse("2026-11-03")]!!.status)
        assertEquals(OccurrenceStatus.MISSED, history[LocalDate.parse("2026-11-05")]!!.status)
        assertEquals(OccurrenceStatus.OPEN, history[LocalDate.parse("2026-11-06")]!!.status)
        assertTrue(history.keys.none { it.isAfter(LocalDate.parse("2026-11-06")) })
    }

    @Test
    fun checklistSemanal_ficaAbertoDuranteASemana() = runBlocking {
        g.setNow("2026-11-02T10:00") // segunda
        val rule = RecurrenceRule("", Frequency.WEEKLY, daysOfWeek = setOf(DayOfWeek.MONDAY), startDate = LocalDate.parse("2026-11-02"))
        val id = createRoutine(rule, itemCount = 3)
        g.setNow("2026-11-08T22:00")
        g.reconcile()
        assertEquals(LocalDate.parse("2026-11-02"), current(id)!!.occurrence.date)
        g.setNow("2026-11-09T06:00")
        g.reconcile()
        assertEquals(LocalDate.parse("2026-11-09"), current(id)!!.occurrence.date)
    }

    @Test
    fun diasPersonalizados() = runBlocking {
        g.setNow("2026-11-02T10:00")
        val rule = RecurrencePreset.CUSTOM_WEEKDAYS.build("", LocalDate.parse("2026-11-02"), customDays = setOf(DayOfWeek.TUESDAY, DayOfWeek.FRIDAY))
        val id = createRoutine(rule)
        assertNull(current(id))
        g.setNow("2026-11-03T10:00"); g.reconcile()
        assertEquals(LocalDate.parse("2026-11-03"), current(id)!!.occurrence.date)
        g.setNow("2026-11-07T10:00"); g.reconcile()
        assertEquals(LocalDate.parse("2026-11-06"), current(id)!!.occurrence.date)
        assertEquals(2, g.checklists.observeHistory(id).first().size)
    }

    @Test
    fun geracaoIdempotenteERestricaoDeDuplicidadeNoBanco() = runBlocking {
        g.setNow("2026-11-02T10:00")
        val id = createRoutine(RecurrencePreset.EVERY_DAY.build("", LocalDate.parse("2026-11-02")))
        repeat(10) { g.reconcile() }
        assertEquals(1, g.checklists.observeHistory(id).first().size)
        // Mesmo forçando o reprocessamento, o índice único (modelo, data) impede duplicatas.
        g.checklists.updateTracking(id, LocalDate.parse("2026-11-02"), null)
        g.reconcile()
        assertEquals(1, g.checklists.observeHistory(id).first().size)
        val inserted = g.db.checklistDao().insertOccurrence(
            ChecklistOccurrenceEntity("dup", id, LocalDate.parse("2026-11-02").toEpochDay(), "OPEN", 0, 0, null, null, null, 0, 0),
        )
        assertEquals(-1L, inserted)
    }

    @Test
    fun mudancaDeFusoNaoDuplica() = runBlocking {
        g.setNow("2026-11-02T22:00")
        val id = createRoutine(RecurrencePreset.EVERY_DAY.build("", LocalDate.parse("2026-11-02")))
        val instant = g.time.now()
        g.time.zoneId = java.time.ZoneId.of("Asia/Tokyo") // já é dia 3 em Tóquio
        g.time.instant = instant
        g.reconcile()
        g.time.zoneId = TestGraph.SAO_PAULO
        g.reconcile()
        val dates = g.checklists.observeHistory(id).first().map { it.date }.sorted()
        assertEquals(listOf(LocalDate.parse("2026-11-02"), LocalDate.parse("2026-11-03")), dates)
    }

    @Test
    fun reinicializacaoDoAparelho_reconciliaAPartirDoBancoPersistido() = runBlocking {
        val context = g.context
        val name = "reboot-test.db"
        context.deleteDatabase(name)
        val time = FixedTimeProvider(LocalDateTime.parse("2026-11-07T08:00").atZone(TestGraph.SAO_PAULO).toInstant(), TestGraph.SAO_PAULO)
        var db = Room.databaseBuilder(context, OrganizadorDatabase::class.java, name).build()
        var graph = TestGraph(db, time)
        val id = graph.saveChecklist(
            ChecklistDraft(title = "Sábado", kind = ChecklistKind.RECURRING, items = items(4), recurrence = RecurrencePreset.SATURDAYS.build("", LocalDate.parse("2026-11-07"))),
        )
        graph.checklists.observeCurrentOccurrence(id).first()!!.states.take(2).forEach { graph.checklists.setItemChecked(it.id, true, time.now()) }
        db.close() // "desligou o aparelho"

        time.instant = LocalDateTime.parse("2026-11-21T09:00").atZone(TestGraph.SAO_PAULO).toInstant() // duas semanas depois
        db = Room.databaseBuilder(context, OrganizadorDatabase::class.java, name).build()
        graph = TestGraph(db, time)
        graph.reconcile() // o que BOOT_COMPLETED dispara
        val history = graph.checklists.observeHistory(id).first().associateBy { it.date }
        assertEquals(OccurrenceStatus.PARTIAL, history[LocalDate.parse("2026-11-07")]!!.status)
        assertEquals(OccurrenceStatus.MISSED, history[LocalDate.parse("2026-11-14")]!!.status)
        assertEquals(OccurrenceStatus.OPEN, history[LocalDate.parse("2026-11-21")]!!.status)
        db.close()
        context.deleteDatabase(name)
        Unit
    }

    @Test
    fun alterarRegraPreservaHistoricoEAjustaExecucaoAberta() = runBlocking {
        g.setNow("2026-11-02T10:00")
        val id = createRoutine(RecurrencePreset.EVERY_DAY.build("", LocalDate.parse("2026-11-02")))
        g.setNow("2026-11-03T10:00"); g.reconcile()
        val details = g.checklists.getDetails(id)!!
        // Passa a ser só aos sábados: a execução de hoje (terça, sem progresso) é descartada; a de ontem fica.
        g.saveChecklist(
            ChecklistDraft(
                id = id, title = "Rotina", kind = ChecklistKind.RECURRING, isRoutine = true,
                items = details.items.map { ChecklistItemDraft(it.id, it.title) },
                recurrence = details.rule!!.copy(frequency = Frequency.WEEKLY, daysOfWeek = setOf(DayOfWeek.SATURDAY)),
            ),
        )
        val history = g.checklists.observeHistory(id).first()
        assertEquals(listOf(LocalDate.parse("2026-11-02")), history.map { it.date })
        assertNull(current(id))
    }

    @Test
    fun rotinaDesativadaNaoGeraNovasExecucoes() = runBlocking {
        g.setNow("2026-11-02T10:00")
        val id = createRoutine(RecurrencePreset.EVERY_DAY.build("", LocalDate.parse("2026-11-02")))
        val d = g.checklists.getDetails(id)!!
        g.saveChecklist(ChecklistDraft(id = id, title = "Rotina", kind = ChecklistKind.RECURRING, items = d.items.map { ChecklistItemDraft(it.id, it.title) }, recurrence = d.rule!!.copy(enabled = false)))
        g.setNow("2026-11-05T10:00"); g.reconcile()
        val history = g.checklists.observeHistory(id).first()
        assertEquals(1, history.size)
        assertEquals(OccurrenceStatus.MISSED, history.single().status)
    }

    @Test
    fun editarItensDoModeloAtualizaSoAExecucaoAberta() = runBlocking {
        g.setNow("2026-11-02T10:00")
        val id = createRoutine(RecurrencePreset.EVERY_DAY.build("", LocalDate.parse("2026-11-02")), itemCount = 2)
        val first = current(id)!!
        g.checklists.setItemChecked(first.states[0].id, true, g.time.now())
        g.setNow("2026-11-03T10:00"); g.reconcile()
        val d = g.checklists.getDetails(id)!!
        g.saveChecklist(
            ChecklistDraft(
                id = id, title = "Rotina", kind = ChecklistKind.RECURRING, isRoutine = true,
                items = listOf(ChecklistItemDraft(d.items[0].id, "Renomeado"), ChecklistItemDraft(title = "Novo item")),
                recurrence = d.rule,
            ),
        )
        val open = current(id)!!
        assertEquals(listOf("Renomeado", "Novo item"), open.states.map { it.title })
        // Execução fechada mantém os títulos originais e as marcações.
        val closed = g.checklists.observeOccurrence(first.occurrence.id).first()!!
        assertEquals(listOf("Atividade 1", "Atividade 2"), closed.states.map { it.title })
        assertEquals(1, closed.states.count { it.checked })
    }

    @Test
    fun checklistSimplesEPermanenteTemExecucaoUnica() = runBlocking {
        val simple = g.saveChecklist(ChecklistDraft(title = "Mala", kind = ChecklistKind.SIMPLE, items = items(3)))
        val permanent = g.saveChecklist(ChecklistDraft(title = "Compras", kind = ChecklistKind.PERMANENT, items = items(2)))
        assertNotNull(current(simple))
        assertNotNull(current(permanent))
        g.setNow("2026-12-25T10:00"); g.reconcile()
        assertEquals(1, g.checklists.observeHistory(simple).first().size)
        assertEquals(1, g.checklists.observeHistory(permanent).first().size)
    }
}
