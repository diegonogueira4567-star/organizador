package com.organizador.data.local

import androidx.sqlite.db.SimpleSQLiteQuery
import com.organizador.domain.model.NoteSort
import com.organizador.domain.model.NoteView
import com.organizador.domain.model.TaskQuery
import com.organizador.domain.model.TaskSort
import com.organizador.domain.model.TaskView
import java.time.LocalDate

/** SQL + argumentos; separado de SimpleSQLiteQuery para ser testável na JVM. */
data class SqlQuery(val sql: String, val args: List<Any?>) {
    fun toRoom() = SimpleSQLiteQuery(sql, args.toTypedArray())
}

/** Escapa curingas do LIKE para que a pesquisa trate %, _ e \ literalmente. */
internal fun likePattern(text: String): String =
    "%" + text.trim().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%"

object TaskQueryBuilder {
    private const val SELECT = """
        SELECT t.*, p.name AS projectName, p.color AS projectColor,
          (SELECT COUNT(*) FROM subtasks s WHERE s.taskId = t.id) AS subtaskTotal,
          (SELECT COUNT(*) FROM subtasks s WHERE s.taskId = t.id AND s.done = 1) AS subtaskDone,
          EXISTS(SELECT 1 FROM reminders r WHERE r.taskId = t.id AND r.fired = 0) AS hasReminder,
          (SELECT group_concat(g.id || char(31) || g.name || char(31) || g.color, char(30))
             FROM task_tags tt JOIN tags g ON g.id = tt.tagId WHERE tt.taskId = t.id) AS tagData
        FROM tasks t LEFT JOIN projects p ON p.id = t.projectId
    """

    /** Limite de segurança para listas históricas (concluídas/lixeira); o restante é carregado sob demanda. */
    const val HISTORY_PAGE = 300

    fun build(query: TaskQuery, today: LocalDate, limit: Int? = null): SqlQuery {
        val where = ArrayList<String>()
        val args = ArrayList<Any?>()
        val todayDay = today.toEpochDay()
        val view = query.view
        val filter = query.filter

        if (view == TaskView.Trash) where += "t.deletedAt IS NOT NULL" else where += "t.deletedAt IS NULL"
        when (view) {
            TaskView.Archived -> where += "t.archived = 1"
            TaskView.Trash -> Unit
            else -> where += "t.archived = 0"
        }
        val openOnly = view != TaskView.Completed && view != TaskView.Trash && view != TaskView.Archived && !filter.includeCompleted
        if (openOnly) where += "t.status = 'OPEN'"

        when (view) {
            TaskView.Inbox -> where += "t.projectId IS NULL"
            TaskView.Today -> { where += "t.dueDate <= ?"; args += todayDay }
            is TaskView.Upcoming -> { where += "t.dueDate BETWEEN ? AND ?"; args += todayDay; args += todayDay + view.days }
            TaskView.Overdue -> { where += "t.dueDate < ?"; args += todayDay }
            TaskView.Completed -> where += "t.status = 'COMPLETED'"
            is TaskView.ByProject -> { where += "t.projectId = ?"; args += view.projectId }
            is TaskView.ByCategory -> { where += "t.categoryId = ?"; args += view.categoryId }
            is TaskView.ByTag -> { where += "EXISTS(SELECT 1 FROM task_tags x WHERE x.taskId = t.id AND x.tagId = ?)"; args += view.tagId }
            is TaskView.ByPriority -> { where += "t.priority = ?"; args += view.priority.level }
            TaskView.All, TaskView.Archived, TaskView.Trash -> Unit
        }

        if (filter.text.isNotBlank()) {
            where += "(t.title LIKE ? ESCAPE '\\' OR t.description LIKE ? ESCAPE '\\' OR t.notes LIKE ? ESCAPE '\\')"
            val pattern = likePattern(filter.text)
            repeat(3) { args += pattern }
        }
        if (filter.priorities.isNotEmpty()) {
            where += "t.priority IN (${filter.priorities.joinToString(",") { "?" }})"
            args.addAll(filter.priorities.map { it.level })
        }
        if (filter.tagIds.isNotEmpty()) {
            where += "EXISTS(SELECT 1 FROM task_tags y WHERE y.taskId = t.id AND y.tagId IN (${filter.tagIds.joinToString(",") { "?" }}))"
            args.addAll(filter.tagIds)
        }
        filter.projectId?.let { where += "t.projectId = ?"; args += it }
        filter.categoryId?.let { where += "t.categoryId = ?"; args += it }

        val order = when {
            view == TaskView.Completed -> "t.completedAt DESC"
            view == TaskView.Trash -> "t.deletedAt DESC"
            else -> when (query.sort) {
                TaskSort.MANUAL -> "t.sortOrder, t.createdAt"
                TaskSort.DUE_DATE -> "t.dueDate IS NULL, t.dueDate, t.dueTime IS NULL, t.dueTime, t.priority, t.sortOrder"
                TaskSort.PRIORITY -> "t.priority, t.dueDate IS NULL, t.dueDate, t.dueTime"
                TaskSort.CREATED -> "t.createdAt DESC"
                TaskSort.UPDATED -> "t.updatedAt DESC"
                TaskSort.TITLE -> "t.title COLLATE NOCASE"
            }
        }
        val effectiveLimit = query.limit ?: limit ?: if (view == TaskView.Completed || view == TaskView.Trash) HISTORY_PAGE else null
        val sql = buildString {
            append(SELECT.trimIndent())
            append("\nWHERE ").append(where.joinToString(" AND "))
            append("\nORDER BY ").append(order)
            if (effectiveLimit != null) append("\nLIMIT ").append(effectiveLimit)
        }
        return SqlQuery(sql, args)
    }

    fun dueBetween(from: LocalDate, to: LocalDate): SqlQuery = SqlQuery(
        SELECT.trimIndent() + "\nWHERE t.deletedAt IS NULL AND t.archived = 0 AND t.dueDate BETWEEN ? AND ?\nORDER BY t.dueDate, t.dueTime IS NULL, t.dueTime, t.priority",
        listOf(from.toEpochDay(), to.toEpochDay()),
    )
}

object NoteQueryBuilder {
    fun build(view: NoteView, sort: NoteSort, text: String, limit: Int? = null): SqlQuery {
        val where = ArrayList<String>()
        val args = ArrayList<Any?>()
        if (view == NoteView.Trash) where += "n.deletedAt IS NOT NULL" else where += "n.deletedAt IS NULL"
        when (view) {
            NoteView.All -> where += "n.archived = 0"
            NoteView.Favorites -> where += "n.archived = 0 AND n.favorite = 1"
            NoteView.Archived -> where += "n.archived = 1"
            NoteView.Trash -> Unit
            is NoteView.Folder -> { where += "n.archived = 0 AND n.folderId = ?"; args += view.folderId }
            is NoteView.ByTag -> { where += "n.archived = 0 AND EXISTS(SELECT 1 FROM note_tags x WHERE x.noteId = n.id AND x.tagId = ?)"; args += view.tagId }
            is NoteView.ByProject -> { where += "n.archived = 0 AND n.projectId = ?"; args += view.projectId }
        }
        if (text.isNotBlank()) {
            where += "(n.title LIKE ? ESCAPE '\\' OR n.plainText LIKE ? ESCAPE '\\')"
            val pattern = likePattern(text)
            args += pattern
            args += pattern
        }
        val order = when (sort) {
            NoteSort.UPDATED -> "n.pinned DESC, n.updatedAt DESC"
            NoteSort.CREATED -> "n.pinned DESC, n.createdAt DESC"
            NoteSort.TITLE -> "n.pinned DESC, n.title COLLATE NOCASE"
        }
        val sql = """
            SELECT n.id, n.title, substr(n.plainText, 1, 200) AS preview, n.folderId, n.favorite, n.pinned, n.archived,
                   n.deletedAt, n.createdAt, n.updatedAt,
                   (SELECT group_concat(g.id || char(31) || g.name || char(31) || g.color, char(30))
                      FROM note_tags nt JOIN tags g ON g.id = nt.tagId WHERE nt.noteId = n.id) AS tagData
            FROM notes n
        """.trimIndent() + "\nWHERE " + where.joinToString(" AND ") + "\nORDER BY " + order +
            (limit?.let { "\nLIMIT $it" } ?: "")
        return SqlQuery(sql, args)
    }
}
