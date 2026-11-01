package com.organizador.data.local

import com.organizador.domain.model.NoteSort
import com.organizador.domain.model.NoteView
import com.organizador.domain.model.Priority
import com.organizador.domain.model.TaskFilter
import com.organizador.domain.model.TaskQuery
import com.organizador.domain.model.TaskView
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QueryBuilderTest {
    private val today = LocalDate.parse("2026-11-01")

    @Test
    fun `placeholders correspondem aos argumentos`() {
        val queries = listOf(
            TaskQuery(TaskView.Today),
            TaskQuery(TaskView.Upcoming(7)),
            TaskQuery(TaskView.ByTag("t"), TaskFilter(text = "x", priorities = setOf(Priority.P1, Priority.P2), tagIds = setOf("a", "b"))),
            TaskQuery(TaskView.Trash),
            TaskQuery(TaskView.Completed, TaskFilter(projectId = "p", categoryId = "c")),
        ).map { TaskQueryBuilder.build(it, today) } + NoteQueryBuilder.build(NoteView.Folder("f"), NoteSort.TITLE, "abc")
        queries.forEach { q -> assertEquals(q.sql, q.sql.count { it == '?' }, q.args.size) }
    }

    @Test
    fun `curingas da pesquisa sao escapados`() {
        assertEquals("%50\\% de\\_desconto%", likePattern("50% de_desconto"))
    }

    @Test
    fun `lixeira inclui somente excluidas e caixa de entrada exclui concluidas`() {
        assertTrue(TaskQueryBuilder.build(TaskQuery(TaskView.Trash), today).sql.contains("t.deletedAt IS NOT NULL"))
        val inbox = TaskQueryBuilder.build(TaskQuery(TaskView.Inbox), today).sql
        assertTrue(inbox.contains("t.status = 'OPEN'") && inbox.contains("t.projectId IS NULL") && inbox.contains("t.deletedAt IS NULL"))
    }

    @Test
    fun `listas historicas sao paginadas`() {
        assertTrue(TaskQueryBuilder.build(TaskQuery(TaskView.Completed), today).sql.contains("LIMIT ${TaskQueryBuilder.HISTORY_PAGE}"))
    }
}
