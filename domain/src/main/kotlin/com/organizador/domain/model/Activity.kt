package com.organizador.domain.model

import java.time.Instant
import java.time.LocalDate

enum class ActivityType {
    TASK_CREATED, TASK_UPDATED, TASK_COMPLETED, TASK_REOPENED, TASK_DELETED, TASK_RESTORED, TASK_ARCHIVED,
    CHECKLIST_CREATED, CHECKLIST_ITEM_CHECKED, CHECKLIST_COMPLETED, CHECKLIST_MISSED,
    NOTE_CREATED, NOTE_UPDATED, NOTE_DELETED,
    PROJECT_CREATED, BACKUP_EXPORTED, BACKUP_RESTORED,
}

enum class EntityType { TASK, CHECKLIST, OCCURRENCE, NOTE, PROJECT, SYSTEM }

data class ActivityEntry(
    val id: String,
    val type: ActivityType,
    val entityType: EntityType,
    val entityId: String?,
    /** Título resumido (nunca o conteúdo completo de notas). */
    val title: String,
    val projectId: String? = null,
    val createdAt: Instant,
)

/** Período de estatísticas (datas locais inclusivas). */
data class StatsPeriod(val from: LocalDate, val to: LocalDate)

data class DailyCount(val date: LocalDate, val completed: Int, val created: Int)

data class RoutineRate(val templateId: String, val title: String, val total: Int, val completed: Int, val partial: Int, val missed: Int) {
    val rate: Float get() = if (total == 0) 0f else completed.toFloat() / total
}

data class Statistics(
    val period: StatsPeriod,
    val completedTasks: Int,
    val pendingTasks: Int,
    val overdueTasks: Int,
    val createdTasks: Int,
    val completedToday: Int,
    val dueToday: Int,
    val daily: List<DailyCount>,
    val checklistRuns: Int,
    val checklistCompleted: Int,
    val checklistPartial: Int,
    val checklistMissed: Int,
    val routines: List<RoutineRate>,
) {
    val completionRate: Float get() = (completedTasks + pendingTasks).let { if (it == 0) 0f else completedTasks.toFloat() / it }
    val checklistRate: Float get() = if (checklistRuns == 0) 0f else checklistCompleted.toFloat() / checklistRuns
}
