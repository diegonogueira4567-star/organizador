package com.organizador.data.local.dao

import androidx.room.Dao
import androidx.room.Query

data class StatusCountRow(val status: String, val count: Int)
data class RoutineRateRow(val templateId: String, val title: String, val total: Int, val completed: Int, val partial: Int, val missed: Int)

/**
 * Consultas agregadas para estatísticas, sempre sobre dados reais do banco.
 */
@Dao
interface StatsDao {
    @Query(
        """SELECT COUNT(*) FROM tasks WHERE deletedAt IS NULL AND status = 'COMPLETED'
           AND completedAt BETWEEN :fromMillis AND :toMillis AND (:projectId IS NULL OR projectId = :projectId)""",
    )
    suspend fun completedBetween(fromMillis: Long, toMillis: Long, projectId: String?): Int

    @Query(
        """SELECT COUNT(*) FROM tasks WHERE deletedAt IS NULL
           AND createdAt BETWEEN :fromMillis AND :toMillis AND (:projectId IS NULL OR projectId = :projectId)""",
    )
    suspend fun createdBetween(fromMillis: Long, toMillis: Long, projectId: String?): Int

    @Query("SELECT COUNT(*) FROM tasks WHERE deletedAt IS NULL AND archived = 0 AND status = 'OPEN' AND (:projectId IS NULL OR projectId = :projectId)")
    suspend fun pending(projectId: String?): Int

    @Query("SELECT COUNT(*) FROM tasks WHERE deletedAt IS NULL AND archived = 0 AND status = 'OPEN' AND dueDate < :today AND (:projectId IS NULL OR projectId = :projectId)")
    suspend fun overdue(today: Long, projectId: String?): Int

    @Query("SELECT COUNT(*) FROM tasks WHERE deletedAt IS NULL AND archived = 0 AND dueDate = :today AND (:projectId IS NULL OR projectId = :projectId)")
    suspend fun dueOn(today: Long, projectId: String?): Int

    /** Instantes brutos: o agrupamento por dia local é feito no repositório com o fuso real (horário de verão). */
    @Query(
        """SELECT completedAt FROM tasks WHERE deletedAt IS NULL AND status = 'COMPLETED'
           AND completedAt BETWEEN :fromMillis AND :toMillis AND (:projectId IS NULL OR projectId = :projectId)""",
    )
    suspend fun completedInstants(fromMillis: Long, toMillis: Long, projectId: String?): List<Long>

    @Query(
        """SELECT createdAt FROM tasks WHERE deletedAt IS NULL
           AND createdAt BETWEEN :fromMillis AND :toMillis AND (:projectId IS NULL OR projectId = :projectId)""",
    )
    suspend fun createdInstants(fromMillis: Long, toMillis: Long, projectId: String?): List<Long>

    @Query(
        """SELECT o.status AS status, COUNT(*) AS count FROM checklist_occurrences o
           JOIN checklist_templates t ON t.id = o.templateId
           WHERE t.deletedAt IS NULL AND o.date BETWEEN :fromDay AND :toDay AND (:projectId IS NULL OR t.projectId = :projectId)
             AND t.kind = 'RECURRING'
           GROUP BY o.status""",
    )
    suspend fun occurrenceStatusCounts(fromDay: Long, toDay: Long, projectId: String?): List<StatusCountRow>

    @Query(
        """SELECT t.id AS templateId, t.title AS title, COUNT(o.id) AS total,
                  SUM(CASE WHEN o.status = 'COMPLETED' THEN 1 ELSE 0 END) AS completed,
                  SUM(CASE WHEN o.status = 'PARTIAL' THEN 1 ELSE 0 END) AS partial,
                  SUM(CASE WHEN o.status = 'MISSED' THEN 1 ELSE 0 END) AS missed
           FROM checklist_templates t JOIN checklist_occurrences o ON o.templateId = t.id
           WHERE t.deletedAt IS NULL AND t.kind = 'RECURRING' AND o.date BETWEEN :fromDay AND :toDay
             AND (:projectId IS NULL OR t.projectId = :projectId)
           GROUP BY t.id ORDER BY t.title COLLATE NOCASE""",
    )
    suspend fun routineRates(fromDay: Long, toDay: Long, projectId: String?): List<RoutineRateRow>
}
