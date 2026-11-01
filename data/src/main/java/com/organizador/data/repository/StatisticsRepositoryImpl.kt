package com.organizador.data.repository

import com.organizador.data.local.OrganizadorDatabase
import com.organizador.domain.model.DailyCount
import com.organizador.domain.model.OccurrenceStatus
import com.organizador.domain.model.RoutineRate
import com.organizador.domain.model.Statistics
import com.organizador.domain.model.StatsPeriod
import com.organizador.domain.model.TimeProvider
import com.organizador.domain.repository.StatisticsRepository
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StatisticsRepositoryImpl @Inject constructor(
    db: OrganizadorDatabase,
    private val time: TimeProvider,
) : StatisticsRepository {
    private val dao = db.statsDao()

    override suspend fun compute(period: StatsPeriod, projectId: String?, today: LocalDate): Statistics {
        val zone = time.zone()
        val from = startOf(period.from, zone)
        val to = startOf(period.to.plusDays(1), zone) - 1
        val todayFrom = startOf(today, zone)
        val todayTo = startOf(today.plusDays(1), zone) - 1

        val completedPerDay = dao.completedInstants(from, to, projectId).groupingBy { dayOf(it, zone) }.eachCount()
        val createdPerDay = dao.createdInstants(from, to, projectId).groupingBy { dayOf(it, zone) }.eachCount()
        val days = generateSequence(period.from) { it.plusDays(1) }
            .takeWhile { !it.isAfter(period.to) }
            .take(MAX_DAYS)
            .map { DailyCount(it, completedPerDay[it] ?: 0, createdPerDay[it] ?: 0) }
            .toList()

        val statusCounts = dao.occurrenceStatusCounts(period.from.toEpochDay(), period.to.toEpochDay(), projectId)
            .associate { it.status to it.count }
        return Statistics(
            period = period,
            completedTasks = completedPerDay.values.sum(),
            pendingTasks = dao.pending(projectId),
            overdueTasks = dao.overdue(today.toEpochDay(), projectId),
            createdTasks = createdPerDay.values.sum(),
            completedToday = dao.completedBetween(todayFrom, todayTo, projectId),
            dueToday = dao.dueOn(today.toEpochDay(), projectId),
            daily = days,
            checklistRuns = statusCounts.values.sum(),
            checklistCompleted = statusCounts[OccurrenceStatus.COMPLETED.name] ?: 0,
            checklistPartial = statusCounts[OccurrenceStatus.PARTIAL.name] ?: 0,
            checklistMissed = statusCounts[OccurrenceStatus.MISSED.name] ?: 0,
            routines = dao.routineRates(period.from.toEpochDay(), period.to.toEpochDay(), projectId)
                .map { RoutineRate(it.templateId, it.title, it.total, it.completed, it.partial, it.missed) },
        )
    }

    private fun startOf(date: LocalDate, zone: ZoneId): Long = date.atStartOfDay(zone).toInstant().toEpochMilli()
    private fun dayOf(millis: Long, zone: ZoneId): LocalDate = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()

    companion object {
        const val MAX_DAYS = 366
    }
}
