package com.organizador.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.organizador.data.local.OrganizadorDatabase
import com.organizador.data.local.entity.NoteEntity
import com.organizador.data.local.entity.TaskEntity
import com.organizador.domain.note.BlockType
import com.organizador.domain.note.NoteBlock
import com.organizador.domain.note.NoteDocument
import com.organizador.domain.note.NoteDocumentCodec
import dagger.hilt.android.AndroidEntryPoint
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.launch

/**
 * Existe apenas na variante "benchmark". Carrega um volume realista (600 tarefas, 300 notas) uma única vez para
 * que as medições de rolagem usem listas cheias. Chamada pelo módulo :benchmark via `am start`.
 */
@AndroidEntryPoint
class BenchmarkSeedActivity : ComponentActivity() {
    @Inject lateinit var db: OrganizadorDatabase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycleScope.launch {
            val dao = db.backupDao()
            if (dao.tasks().size < TASKS) {
                val today = LocalDate.now().toEpochDay()
                dao.insertTasks(
                    (0 until TASKS).map { i ->
                        TaskEntity(
                            id = "bench-t$i", title = "Tarefa de carga $i", description = if (i % 3 == 0) "Descrição da tarefa $i" else "",
                            notes = "", dueDate = today + (i % 8) - 1, dueTime = if (i % 4 == 0) (8 * 60 + i % 600) else null,
                            priority = 1 + i % 4, projectId = null, categoryId = null, status = "OPEN", archived = false, deletedAt = null,
                            recurrenceRuleId = null, linkedNoteId = null, sortOrder = i.toLong(), createdAt = i.toLong(), updatedAt = i.toLong(), completedAt = null,
                        )
                    },
                )
                dao.insertNotes(
                    (0 until NOTES).map { i ->
                        val doc = NoteDocument(blocks = (0 until 8).map { b -> NoteBlock("b$b", BlockType.PARAGRAPH, "Parágrafo $b da nota de carga $i") })
                        NoteEntity("bench-n$i", "Nota de carga $i", NoteDocumentCodec.encode(doc), NoteDocumentCodec.plainText(doc), null, null, false, false, false, null, 1, i.toLong(), i.toLong())
                    },
                )
            }
            finish()
        }
    }

    private companion object {
        const val TASKS = 600
        const val NOTES = 300
    }
}
