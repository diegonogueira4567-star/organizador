package com.organizador.app.ui.tasks

import com.organizador.domain.model.TimeProvider
import com.organizador.domain.repository.TaskRepository
import com.organizador.domain.usecase.CompleteTaskUseCase
import com.organizador.domain.usecase.CompletionResult
import com.organizador.domain.usecase.ReopenTaskUseCase
import com.organizador.domain.usecase.UndoCompletionUseCase
import javax.inject.Inject

/** Operações sobre tarefas compartilhadas por várias telas (lista, painel, calendário, projeto, pesquisa). */
class TaskActions @Inject constructor(
    private val tasks: TaskRepository,
    private val complete: CompleteTaskUseCase,
    private val undoCompletion: UndoCompletionUseCase,
    private val reopen: ReopenTaskUseCase,
    private val time: TimeProvider,
) {
    /** Retorna o resultado da conclusão (para desfazer) ou null se a tarefa foi reaberta. */
    suspend fun toggle(taskId: String, completed: Boolean): CompletionResult? =
        if (completed) {
            reopen(taskId)
            null
        } else {
            complete(taskId)
        }

    suspend fun undo(result: CompletionResult) = undoCompletion(result)
    suspend fun delete(id: String) = tasks.softDelete(id, time.now())
    suspend fun restore(id: String) = tasks.restore(id)
    suspend fun deleteForever(id: String) = tasks.deletePermanently(id)
    suspend fun duplicate(id: String) = tasks.duplicate(id, time.now())
    suspend fun archive(id: String, archived: Boolean) = tasks.setArchived(id, archived)
    suspend fun move(id: String, projectId: String?) = tasks.moveToProject(id, projectId)
    suspend fun emptyTrash() = tasks.emptyTrash()
}
