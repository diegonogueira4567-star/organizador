package com.organizador.domain.model

/** Visões de tarefas disponíveis na navegação. */
sealed interface TaskView {
    data object Inbox : TaskView
    data object Today : TaskView
    data class Upcoming(val days: Int = 7) : TaskView
    data object Overdue : TaskView
    data object Completed : TaskView
    data object Archived : TaskView
    data object Trash : TaskView
    data object All : TaskView
    data class ByProject(val projectId: String) : TaskView
    data class ByCategory(val categoryId: String) : TaskView
    data class ByTag(val tagId: String) : TaskView
    data class ByPriority(val priority: Priority) : TaskView
}

enum class TaskSort { MANUAL, DUE_DATE, PRIORITY, CREATED, UPDATED, TITLE }

/** Filtros adicionais aplicáveis sobre qualquer visão. */
data class TaskFilter(
    val text: String = "",
    val priorities: Set<Priority> = emptySet(),
    val tagIds: Set<String> = emptySet(),
    val projectId: String? = null,
    val categoryId: String? = null,
    val includeCompleted: Boolean = false,
)

data class TaskQuery(
    val view: TaskView,
    val filter: TaskFilter = TaskFilter(),
    val sort: TaskSort = TaskSort.DUE_DATE,
    /** Máximo de itens (paginação "carregar mais"); null = padrão da visão. */
    val limit: Int? = null,
) {
    val isHistory: Boolean get() = view == TaskView.Completed || view == TaskView.Trash || view == TaskView.Archived

    companion object {
        const val PAGE_SIZE = 100
    }
}
