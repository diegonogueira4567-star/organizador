package com.organizador.data

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.organizador.data.backup.BackupException
import com.organizador.data.backup.BackupSection
import com.organizador.data.backup.RestoreMode
import com.organizador.data.local.OrganizadorDatabase
import com.organizador.domain.model.AttachmentOwner
import com.organizador.domain.model.ChecklistDraft
import com.organizador.domain.model.ChecklistItemDraft
import com.organizador.domain.model.ChecklistKind
import com.organizador.domain.model.SubtaskDraft
import com.organizador.domain.model.TaskDraft
import com.organizador.domain.note.NoteDocument
import com.organizador.domain.recurrence.RecurrencePreset
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BackupRestoreTest {
    private lateinit var g: TestGraph

    @Before
    fun setUp() {
        g = TestGraph.inMemory()
    }

    @After
    fun tearDown() = g.db.close()

    private suspend fun seed(): Map<String, Int> {
        g.tasks.save(TaskDraft(title = "Tarefa 1", dueDate = LocalDate.parse("2026-11-02"), subtasks = listOf(SubtaskDraft(title = "sub"))))
        val t2 = g.tasks.save(TaskDraft(title = "Tarefa 2"))
        g.attachmentsRepo().add(AttachmentOwner.TASK, t2, "foto.png", "image/png", ByteArrayInputStream(ByteArray(2048) { it.toByte() }), g.time.now())
        g.notes.create("Nota", com.organizador.domain.note.NoteDocumentCodec.fromPlainText("conteúdo privado"), null, null, g.time.now())
        val c = g.saveChecklist(
            ChecklistDraft(title = "Diário", kind = ChecklistKind.RECURRING, items = listOf(ChecklistItemDraft(title = "a"), ChecklistItemDraft(title = "b")), recurrence = RecurrencePreset.EVERY_DAY.build("", LocalDate.parse("2026-11-02"))),
        )
        g.checklists.observeCurrentOccurrence(c).first()!!.states.first().let { g.checklists.setItemChecked(it.id, true, g.time.now()) }
        return counts()
    }

    private fun TestGraph.attachmentsRepo() = com.organizador.data.repository.AttachmentRepositoryImpl(db, storage)

    private suspend fun counts(): Map<String, Int> {
        val b = g.db.backupDao()
        return mapOf(
            "tasks" to b.tasks().size, "subtasks" to b.subtasks().size, "notes" to b.notes().size,
            "templates" to b.templates().size, "occurrences" to b.occurrences().size,
            "checked" to b.states().count { it.checked }, "attachments" to b.taskAttachments().size,
        )
    }

    private suspend fun export(password: String? = null, sections: Set<BackupSection> = BackupSection.entries.toSet()): ByteArray {
        val out = ByteArrayOutputStream()
        g.backup.export(out, sections, password?.toCharArray())
        return out.toByteArray()
    }

    @Test
    fun backupCompletoERestauracaoSubstituindo() = runBlocking {
        val before = seed()
        val zip = export()
        // Dados mudam depois do backup...
        g.tasks.save(TaskDraft(title = "Criada depois"))
        g.tasks.softDelete(g.db.backupDao().tasks().first().id, g.time.now())
        // ...e a restauração em modo substituir volta exatamente ao estado salvo.
        val preview = g.backup.inspect({ ByteArrayInputStream(zip) }, null)
        assertEquals(2, preview.counts["tarefas"])
        val report = g.backup.restore({ ByteArrayInputStream(zip) }, null, RestoreMode.REPLACE, BackupSection.entries.toSet())
        assertEquals(before, counts())
        assertTrue(g.db.backupDao().tasks().none { it.deletedAt != null })
        assertTrue(report.preventiveBackup != null)
        // O anexo físico foi restaurado.
        val a = g.db.backupDao().taskAttachments().single()
        assertEquals(2048L, g.storage.resolve(a.relativePath).length())
    }

    @Test
    fun mesclarAdicionaSemApagar() = runBlocking {
        seed()
        val zip = export()
        g.backup.restore({ ByteArrayInputStream(zip) }, null, RestoreMode.REPLACE, BackupSection.entries.toSet())
        g.tasks.save(TaskDraft(title = "Só local"))
        g.backup.restore({ ByteArrayInputStream(zip) }, null, RestoreMode.MERGE, BackupSection.entries.toSet())
        val titles = g.db.backupDao().tasks().map { it.title }.sorted()
        assertEquals(listOf("Só local", "Tarefa 1", "Tarefa 2"), titles)
    }

    @Test
    fun backupCriptografadoExigeSenhaCorreta() = runBlocking {
        seed()
        val zip = export(password = "senha-super-forte")
        val preview = g.backup.inspect({ ByteArrayInputStream(zip) }, null)
        assertTrue(preview.needsPassword)
        try {
            g.backup.inspect({ ByteArrayInputStream(zip) }, "errada".toCharArray())
            fail("senha errada deveria falhar")
        } catch (e: BackupException.WrongPassword) {
            // esperado
        }
        g.backup.restore({ ByteArrayInputStream(zip) }, "senha-super-forte".toCharArray(), RestoreMode.REPLACE, BackupSection.entries.toSet())
        assertEquals(2, g.db.backupDao().tasks().size)
    }

    @Test
    fun arquivoCorrompidoNaoAlteraDados() = runBlocking {
        val before = seed()
        val zip = export()
        val corrupted = zip.copyOf().also { it[it.size / 2] = (it[it.size / 2] + 7).toByte() }
        try {
            g.backup.restore({ ByteArrayInputStream(corrupted) }, null, RestoreMode.REPLACE, BackupSection.entries.toSet())
            fail("backup corrompido deveria ser recusado")
        } catch (e: Exception) {
            // esperado: qualquer falha de validação acontece antes de tocar no banco
        }
        assertEquals(before, counts())
    }

    @Test
    fun backupSeletivoSoDeNotas() = runBlocking {
        seed()
        val zip = export(sections = setOf(BackupSection.NOTES))
        g.backup.restore({ ByteArrayInputStream(zip) }, null, RestoreMode.REPLACE, setOf(BackupSection.NOTES))
        assertEquals(0, g.db.backupDao().tasks().size)
        assertEquals(1, g.db.backupDao().notes().size)
    }
}

@RunWith(AndroidJUnit4::class)
class MigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), OrganizadorDatabase::class.java)

    /**
     * Valida que o esquema exportado da versão atual (schemas/…/N.json) é exatamente o que o Room cria e que
     * todas as migrações registradas levam da versão 1 à atual preservando dados. Ao criar a versão 2, este
     * teste passa a exercitar a migração 1→2 sem alterações.
     */
    @Test
    fun migraDaVersao1AteAAtualPreservandoDados() {
        val name = "migration-test.db"
        helper.createDatabase(name, 1).use { db ->
            db.execSQL(
                "INSERT INTO tasks (id, title, description, notes, dueDate, dueTime, priority, projectId, categoryId, status, archived, deletedAt, recurrenceRuleId, linkedNoteId, sortOrder, createdAt, updatedAt, completedAt) " +
                    "VALUES ('t1', 'Antiga', '', '', NULL, NULL, 4, NULL, NULL, 'OPEN', 0, NULL, NULL, NULL, 0, 0, 0, NULL)",
            )
        }
        helper.runMigrationsAndValidate(name, OrganizadorDatabase.VERSION, true, *OrganizadorDatabase.MIGRATIONS).use { db ->
            db.query("SELECT title FROM tasks WHERE id = 't1'").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("Antiga", c.getString(0))
            }
        }
    }
}
