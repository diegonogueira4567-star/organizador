package com.organizador.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.organizador.domain.model.Priority
import com.organizador.domain.model.SaveResult
import com.organizador.domain.model.SubtaskDraft
import com.organizador.domain.model.TaskDraft
import com.organizador.domain.model.TaskFilter
import com.organizador.domain.model.TaskQuery
import com.organizador.domain.model.TaskView
import com.organizador.domain.model.NoteDraft
import com.organizador.domain.note.BlockType
import com.organizador.domain.note.NoteBlock
import com.organizador.domain.note.NoteDocument
import com.organizador.domain.recurrence.RecurrencePreset
import java.time.LocalDate
import java.time.LocalTime
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

@RunWith(AndroidJUnit4::class)
class TaskNoteDbTest {
    private lateinit var g: TestGraph

    @Before
    fun setUp() {
        g = TestGraph.inMemory()
    }

    @After
    fun tearDown() = g.db.close()

    private suspend fun list(view: TaskView, filter: TaskFilter = TaskFilter()) = g.tasks.observeTasks(TaskQuery(view, filter)).first()

    @Test
    fun crudCompletoComSubtarefasEtiquetasELembretes() = runBlocking {
        val tag = g.db.labelDao().let { com.organizador.data.repository.LabelRepositoryImpl(g.db, g.time).findOrCreateTag("casa") }
        val id = g.tasks.save(
            TaskDraft(
                title = "Pagar luz", dueDate = LocalDate.parse("2026-11-02"), dueTime = LocalTime.of(18, 0), priority = Priority.P1,
                tagIds = setOf(tag.id), subtasks = listOf(SubtaskDraft(title = "Separar boleto"), SubtaskDraft(title = "Pagar")),
                reminderOffsets = listOf(0, 60),
            ),
        )
        val details = g.tasks.getDetails(id)!!
        assertEquals(2, details.subtasks.size)
        assertEquals(listOf("casa"), details.tags.map { it.name })
        assertEquals(2, details.reminders.size)
        val today = list(TaskView.Today).single()
        assertEquals(2, today.subtaskTotal)
        assertTrue(today.hasReminder)

        // Edição remove uma subtarefa e troca o lembrete.
        g.tasks.save(TaskDraft(id = id, title = "Pagar conta de luz", dueDate = details.task.dueDate, subtasks = listOf(SubtaskDraft(details.subtasks[0].id, "Separar boleto", true)), reminderOffsets = listOf(10)))
        val edited = g.tasks.getDetails(id)!!
        assertEquals("Pagar conta de luz", edited.task.title)
        assertEquals(1, edited.subtasks.size)
        assertTrue(edited.subtasks.single().done)
        assertEquals(listOf(10), edited.reminders.map { it.offsetMinutes })
    }

    @Test
    fun exclusaoERecuperacaoDeTarefas() = runBlocking {
        val id = g.tasks.save(TaskDraft(title = "Excluir-me", subtasks = listOf(SubtaskDraft(title = "s"))))
        g.tasks.softDelete(id, g.time.now())
        assertTrue(list(TaskView.Inbox).isEmpty())
        assertEquals(1, list(TaskView.Trash).size)
        g.tasks.restore(id)
        assertEquals(1, list(TaskView.Inbox).size)
        assertEquals(1, g.tasks.getDetails(id)!!.subtasks.size) // nada perdido na ida e volta
        g.tasks.softDelete(id, g.time.now())
        assertEquals(1, g.tasks.emptyTrash())
        assertNull(g.tasks.getTask(id))
        assertTrue(g.db.taskDao().subtasks(id).isEmpty()) // cascata
    }

    @Test
    fun tarefaRecorrenteConcluidaGeraProximaEPodeSerDesfeita() = runBlocking {
        val id = g.tasks.save(
            TaskDraft(title = "Regar plantas", dueDate = LocalDate.parse("2026-11-02"), recurrence = RecurrencePreset.EVERY_N_DAYS.build("", LocalDate.parse("2026-11-02"), everyNDays = 3), subtasks = listOf(SubtaskDraft(title = "Vasos", done = true))),
        )
        val result = g.completeTask(id)!!
        assertEquals(LocalDate.parse("2026-11-05"), result.nextDueDate)
        val next = g.tasks.getDetails(result.nextTaskId!!)!!
        assertNotNull(next.recurrence)
        assertTrue(next.subtasks.none { it.done })
        assertTrue(g.tasks.getTask(id)!!.isCompleted)
        assertNull(g.tasks.getTask(id)!!.recurrenceRuleId)

        g.undoCompletion(result)
        assertNull(g.tasks.getTask(result.nextTaskId!!))
        val reopened = g.tasks.getDetails(id)!!
        assertTrue(!reopened.task.isCompleted)
        assertNotNull(reopened.recurrence)
    }

    @Test
    fun duplicarEMoverEPesquisar() = runBlocking {
        val project = com.organizador.domain.model.Project(com.organizador.domain.model.Ids.new(), "Trabalho", createdAt = g.time.now(), updatedAt = g.time.now())
        com.organizador.data.repository.ProjectRepositoryImpl(g.db, g.activity, g.time).save(project)
        val id = g.tasks.save(TaskDraft(title = "Relatório 50% pronto"))
        val copy = g.tasks.duplicate(id, g.time.now())!!
        g.tasks.moveToProject(copy, project.id)
        assertEquals(1, list(TaskView.ByProject(project.id)).size)
        assertEquals(2, list(TaskView.All, TaskFilter(text = "50%")).size)
        assertEquals(0, list(TaskView.All, TaskFilter(text = "_")).size) // curinga escapado
    }

    @Test
    fun salvamentoDeNotaComControleDeVersaoERascunho() = runBlocking {
        val id = g.notes.create("Ideias", NoteDocument.empty(), null, null, g.time.now())
        val v1 = g.notes.getNote(id)!!.version
        val doc = NoteDocument(blocks = listOf(NoteBlock("a", BlockType.PARAGRAPH, "primeira versão")))
        val saved = g.notes.saveContent(id, "Ideias", doc, v1, g.time.now())
        assertTrue(saved is SaveResult.Saved)

        // Outro editor ainda com a versão antiga: não sobrescreve, cria cópia de conflito.
        val stale = g.notes.saveContent(id, "Ideias", NoteDocument(blocks = listOf(NoteBlock("a", BlockType.PARAGRAPH, "edição concorrente"))), v1, g.time.now())
        assertTrue(stale is SaveResult.Conflict)
        assertEquals("primeira versão", g.notes.getNote(id)!!.plainText)
        val copy = g.notes.getNote((stale as SaveResult.Conflict).conflictCopyId)!!
        assertEquals("edição concorrente", copy.plainText)

        // Rascunho sobrevive a "encerramento inesperado" e pode ser recuperado.
        val draftDoc = NoteDocument(blocks = listOf(NoteBlock("b", BlockType.TODO, "não salvo", checked = true)))
        g.notes.saveDraft(NoteDraft(id, "Ideias", draftDoc, (saved as SaveResult.Saved).version, g.time.now().plusSeconds(5)))
        val recovered = g.notes.getDraft(id)!!
        assertEquals(draftDoc, recovered.document)
        g.notes.deleteDraft(id)
        assertNull(g.notes.getDraft(id))
    }

    @Test
    fun pesquisaDeNotasPorConteudo() = runBlocking {
        g.notes.create("Receita", NoteDocument(blocks = listOf(NoteBlock("a", BlockType.PARAGRAPH, "farinha e ovos"))), null, null, g.time.now())
        g.notes.create("Outra", NoteDocument.empty(), null, null, g.time.now())
        val found = g.notes.observeNotes(com.organizador.domain.model.NoteView.All, com.organizador.domain.model.NoteSort.UPDATED, "ovos").first()
        assertEquals(listOf("Receita"), found.map { it.title })
    }

    @Test
    fun itensDaNotaViramTarefasPreservandoOTexto() = runBlocking {
        val doc = NoteDocument(
            blocks = listOf(
                NoteBlock("t", BlockType.HEADING1, "Mercado"),
                NoteBlock("1", BlockType.TODO, "Leite"),
                NoteBlock("2", BlockType.TODO, "Pão", checked = true),
            ),
        )
        val noteId = g.notes.create("Lista", doc, null, null, g.time.now())
        val created = g.convertItems(noteId, setOf("1", "2"), null)
        assertEquals(2, created)
        val note = g.notes.getNote(noteId)!!
        assertEquals(listOf("Mercado", "Leite", "Pão"), note.document.blocks.map { it.text })
        assertTrue(note.document.blocks.drop(1).all { it.taskId != null })
        assertEquals(2, g.notes.observeLinks(noteId).first().size)
        // Repetir não duplica.
        assertEquals(0, g.convertItems(noteId, setOf("1", "2"), null))
    }

    @Test
    fun estatisticasUsamDadosReais() = runBlocking {
        val a = g.tasks.save(TaskDraft(title = "A", dueDate = LocalDate.parse("2026-11-01")))
        g.tasks.save(TaskDraft(title = "B", dueDate = LocalDate.parse("2026-11-02")))
        g.completeTask(a)
        val stats = g.stats.compute(com.organizador.domain.model.StatsPeriod(LocalDate.parse("2026-10-27"), LocalDate.parse("2026-11-02")), null, LocalDate.parse("2026-11-02"))
        assertEquals(1, stats.completedTasks)
        assertEquals(1, stats.pendingTasks)
        assertEquals(2, stats.createdTasks)
        assertEquals(1, stats.daily.last().completed)
    }
}
