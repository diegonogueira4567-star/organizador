package com.organizador.data

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.organizador.data.local.entity.NoteEntity
import com.organizador.data.local.entity.TaskEntity
import com.organizador.domain.model.NoteSort
import com.organizador.domain.model.NoteView
import com.organizador.domain.model.TaskFilter
import com.organizador.domain.model.TaskQuery
import com.organizador.domain.model.TaskView
import com.organizador.domain.note.BlockType
import com.organizador.domain.note.NoteBlock
import com.organizador.domain.note.NoteDocument
import com.organizador.domain.note.NoteDocumentCodec
import java.time.LocalDate
import kotlin.system.measureTimeMillis
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Mede as metas de referência (listas e pesquisa até 500 ms) com um conjunto grande:
 * 5.000 tarefas e 2.000 notas. Os tempos são registrados no Logcat (tag OrganizadorPerf).
 */
@RunWith(AndroidJUnit4::class)
class PerformanceTest {
    private lateinit var g: TestGraph

    @Before
    fun setUp() = runBlocking {
        g = TestGraph.inMemory()
        val today = LocalDate.parse("2026-11-02").toEpochDay()
        val tasks = (0 until 5000).map { i ->
            TaskEntity(
                id = "t$i", title = "Tarefa número $i ${if (i % 50 == 0) "relatório" else "rotina"}", description = "Descrição da tarefa $i",
                notes = "", dueDate = today + (i % 60) - 30, dueTime = null, priority = 1 + i % 4, projectId = null, categoryId = null,
                status = if (i % 3 == 0) "COMPLETED" else "OPEN", archived = false, deletedAt = null, recurrenceRuleId = null,
                linkedNoteId = null, sortOrder = i.toLong(), createdAt = i.toLong(), updatedAt = i.toLong(), completedAt = if (i % 3 == 0) i.toLong() else null,
            )
        }
        g.db.backupDao().insertTasks(tasks)
        val notes = (0 until 2000).map { i ->
            val doc = NoteDocument(blocks = (0 until 20).map { b -> NoteBlock("b$b", BlockType.PARAGRAPH, "Parágrafo $b da nota $i com algum conteúdo ${if (i % 100 == 0) "orçamento" else "texto"}") })
            NoteEntity("n$i", "Nota $i", NoteDocumentCodec.encode(doc), NoteDocumentCodec.plainText(doc), null, null, false, false, false, null, 1, i.toLong(), i.toLong())
        }
        g.db.backupDao().insertNotes(notes)
    }

    @After
    fun tearDown() = g.db.close()

    private fun measure(label: String, block: suspend () -> Int): Long {
        var size = 0
        // Primeira execução aquece o SQLite; a medida é a média das 3 seguintes.
        runBlocking { block() }
        val total = (1..3).sumOf { measureTimeMillis { runBlocking { size = block() } } }
        val avg = total / 3
        Log.i("OrganizadorPerf", "$label: ${avg} ms ($size resultados)")
        return avg
    }

    @Test
    fun listasEPesquisaDentroDaMeta() {
        val results = mapOf(
            "Hoje" to measure("lista Hoje") { g.tasks.observeTasks(TaskQuery(TaskView.Today)).first().size },
            "Próximos" to measure("lista Próximos 7 dias") { g.tasks.observeTasks(TaskQuery(TaskView.Upcoming(7))).first().size },
            "Concluídas" to measure("lista Concluídas (paginada)") { g.tasks.observeTasks(TaskQuery(TaskView.Completed)).first().size },
            "Pesquisa tarefas" to measure("pesquisa tarefas 'relatório'") { g.tasks.observeTasks(TaskQuery(TaskView.All, TaskFilter(text = "relatório", includeCompleted = true))).first().size },
            "Lista notas" to measure("lista de notas") { g.notes.observeNotes(NoteView.All, NoteSort.UPDATED).first().size },
            "Pesquisa notas" to measure("pesquisa notas 'orçamento'") { g.notes.observeNotes(NoteView.All, NoteSort.UPDATED, "orçamento").first().size },
        )
        results.forEach { (label, ms) -> assertTrue("$label levou $ms ms (meta: 500 ms)", ms <= 500) }
    }
}
