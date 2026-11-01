package com.organizador.data.repository

import androidx.room.withTransaction
import com.organizador.data.local.OrganizadorDatabase
import com.organizador.data.local.dao.TemplateOccurrenceRow
import com.organizador.data.local.entity.ChecklistItemEntity
import com.organizador.data.local.entity.ChecklistItemStateEntity
import com.organizador.data.local.entity.ChecklistOccurrenceEntity
import com.organizador.data.local.entity.ChecklistTemplateEntity
import com.organizador.data.local.invalidationFlow
import com.organizador.data.mapper.toDomain
import com.organizador.data.mapper.toEntity
import com.organizador.domain.model.ActivityType
import com.organizador.domain.model.ChecklistDetails
import com.organizador.domain.model.ChecklistDraft
import com.organizador.domain.model.ChecklistKind
import com.organizador.domain.model.ChecklistOccurrence
import com.organizador.domain.model.ChecklistSummary
import com.organizador.domain.model.ChecklistTemplate
import com.organizador.domain.model.EntityType
import com.organizador.domain.model.Ids
import com.organizador.domain.model.OccurrenceDetails
import com.organizador.domain.model.OccurrenceStatus
import com.organizador.domain.model.TimeProvider
import com.organizador.domain.recurrence.OccurrencePlanner
import com.organizador.domain.recurrence.OpenOccurrence
import com.organizador.domain.recurrence.ReconcilePlan
import com.organizador.domain.recurrence.RecurrenceEngine
import com.organizador.domain.recurrence.RecurrenceRule
import com.organizador.domain.repository.ActivityRepository
import com.organizador.domain.repository.ChecklistRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class ChecklistRepositoryImpl @Inject constructor(
    private val db: OrganizadorDatabase,
    private val activity: ActivityRepository,
    private val time: TimeProvider,
) : ChecklistRepository {
    private val dao = db.checklistDao()
    private val recurrenceDao = db.recurrenceDao()

    override fun observeSummaries(routines: Boolean?, includeArchived: Boolean): Flow<List<ChecklistSummary>> =
        combine(
            dao.observeTemplates(routines, includeArchived),
            dao.observeAllOpen(),
            recurrenceDao.observeAll(),
            time.todayFlow(),
        ) { templates, open, rules, today ->
            val openByTemplate = open.groupBy { it.templateId }
            val rulesById = rules.associateBy { it.id }
            templates.map { row ->
                val template = row.template.toDomain()
                val rule = template.recurrenceRuleId?.let { rulesById[it] }?.toDomain()
                val current = openByTemplate[template.id]?.maxByOrNull { it.date }?.toDomain()
                val next = if (template.kind == ChecklistKind.RECURRING && rule != null && rule.enabled) {
                    RecurrenceEngine.nextAfter(rule, maxOf(current?.date ?: today.minusDays(1), today.minusDays(1)))
                } else {
                    null
                }
                ChecklistSummary(template, rule, row.itemCount, current, next)
            }
        }

    override fun observeTrash(): Flow<List<ChecklistTemplate>> = dao.observeDeleted().map { l -> l.map { it.toDomain() } }

    override fun observeDetails(templateId: String): Flow<ChecklistDetails?> =
        db.invalidationFlow("checklist_templates", "checklist_items", "recurrence_rules").map { getDetails(templateId) }

    override fun observeOccurrence(occurrenceId: String): Flow<OccurrenceDetails?> =
        combine(dao.observeOccurrence(occurrenceId), dao.observeStates(occurrenceId)) { occ, states ->
            occ ?: return@combine null
            val template = dao.template(occ.templateId) ?: return@combine null
            OccurrenceDetails(occ.toDomain(), template.toDomain(), states.map { it.toDomain() })
        }

    override fun observeCurrentOccurrence(templateId: String): Flow<OccurrenceDetails?> =
        dao.observeCurrentOccurrence(templateId).map { it?.id }.flatMapLatest { id ->
            if (id == null) flowOf(null) else observeOccurrence(id)
        }

    override fun observeHistory(templateId: String, limit: Int): Flow<List<ChecklistOccurrence>> =
        dao.observeHistory(templateId, limit).map { l -> l.map { it.toDomain() } }

    override fun observeOccurrencesBetween(from: LocalDate, to: LocalDate): Flow<List<Pair<ChecklistTemplate, ChecklistOccurrence>>> =
        dao.observeOccurrencesBetween(from.toEpochDay(), to.toEpochDay()).map { rows -> rows.map { it.toPair() } }

    override suspend fun getDetails(templateId: String): ChecklistDetails? {
        val template = dao.template(templateId) ?: return null
        return ChecklistDetails(
            template = template.toDomain(),
            items = dao.items(templateId).map { it.toDomain() },
            rule = template.recurrenceRuleId?.let { recurrenceDao.get(it)?.toDomain() },
        )
    }

    override suspend fun save(draft: ChecklistDraft, now: Instant, trackingFrom: LocalDate?): String = db.withTransaction {
        val existing = draft.id?.let { dao.template(it) }
        val id = existing?.id ?: draft.id ?: Ids.new()
        val nowMs = now.toEpochMilli()

        val oldRuleId = existing?.recurrenceRuleId
        val ruleId = draft.recurrence?.let { rule ->
            val withId = if (rule.id.isBlank()) rule.copy(id = oldRuleId ?: Ids.new()) else rule
            recurrenceDao.upsert(withId.toEntity())
            withId.id
        }
        dao.upsertTemplate(
            ChecklistTemplateEntity(
                id = id,
                title = draft.title.trim(),
                description = draft.description,
                kind = draft.kind.name,
                isRoutine = draft.isRoutine,
                projectId = draft.projectId,
                color = draft.color,
                icon = existing?.icon ?: if (draft.isRoutine) "routine" else "checklist",
                recurrenceRuleId = ruleId,
                trackingFrom = trackingFrom?.toEpochDay(),
                generatedThrough = existing?.generatedThrough,
                archived = existing?.archived ?: false,
                deletedAt = existing?.deletedAt,
                sortOrder = existing?.sortOrder ?: (dao.maxOrder() + 1),
                createdAt = existing?.createdAt ?: nowMs,
                updatedAt = nowMs,
            ),
        )
        if (oldRuleId != null && oldRuleId != ruleId) recurrenceDao.deleteIfOrphan(oldRuleId)

        val previous = dao.items(id).associateBy { it.id }
        val keep = draft.items.filter { it.title.isNotBlank() }.mapIndexed { index, item ->
            val itemId = item.id ?: Ids.new()
            dao.upsertItem(ChecklistItemEntity(itemId, id, item.title.trim(), index, previous[itemId]?.createdAt ?: nowMs))
            itemId
        }
        // Itens removidos: estados em execuções fechadas mantêm o título copiado (FK SET NULL).
        dao.deleteItemsExcept(id, keep)

        val items = dao.items(id)
        dao.openOccurrences(id).forEach { syncOpenOccurrence(it, items, nowMs) }
        id
    }

    /** Alinha a execução aberta aos itens atuais do modelo, preservando marcações já feitas. */
    private suspend fun syncOpenOccurrence(occurrence: ChecklistOccurrenceEntity, items: List<ChecklistItemEntity>, nowMs: Long) {
        val states = dao.states(occurrence.id)
        val byItem = states.filter { it.itemId != null }.associateBy { it.itemId }
        val itemIds = items.map { it.id }.toSet()
        states.filter { (it.itemId == null || it.itemId !in itemIds) && !it.checked }.forEach { dao.deleteState(it.id) }
        val newStates = ArrayList<ChecklistItemStateEntity>()
        items.forEach { item ->
            val state = byItem[item.id]
            if (state == null) {
                newStates += ChecklistItemStateEntity(Ids.new(), occurrence.id, item.id, item.title, item.sortOrder, false, null)
            } else if (state.title != item.title || state.sortOrder != item.sortOrder) {
                dao.updateState(state.id, item.title, item.sortOrder)
            }
        }
        if (newStates.isNotEmpty()) dao.insertStates(newStates)
        refreshOpenStatus(occurrence.id, nowMs)
    }

    private suspend fun refreshOpenStatus(occurrenceId: String, nowMs: Long) {
        dao.refreshCounts(occurrenceId, nowMs)
        val occ = dao.occurrence(occurrenceId) ?: return
        if (occ.closedAt != null) {
            if (occ.status != OccurrenceStatus.SKIPPED.name) {
                // Edição explícita de histórico: recalcula o resultado final.
                val status = OccurrencePlanner.closingStatus(occ.totalItems, occ.checkedItems)
                dao.setOccurrenceStatus(occ.id, status.name, occ.closedAt, if (status == OccurrenceStatus.COMPLETED) occ.completedAt ?: nowMs else null, nowMs)
            }
            return
        }
        val complete = occ.totalItems > 0 && occ.checkedItems >= occ.totalItems
        val status = if (complete) OccurrenceStatus.COMPLETED else OccurrenceStatus.OPEN
        if (status.name != occ.status) {
            dao.setOccurrenceStatus(occ.id, status.name, null, if (complete) nowMs else null, nowMs)
        }
    }

    override suspend fun setArchived(templateId: String, archived: Boolean) =
        dao.setArchived(templateId, archived, time.now().toEpochMilli())

    override suspend fun softDelete(templateId: String, at: Instant) =
        dao.setDeleted(templateId, at.toEpochMilli(), at.toEpochMilli())

    override suspend fun restore(templateId: String) {
        // Ao restaurar, não registrar como perdidas as execuções do período em que esteve na lixeira.
        db.withTransaction {
            dao.setDeleted(templateId, null, time.now().toEpochMilli())
            dao.setTracking(templateId, null, null)
        }
    }

    override suspend fun deletePermanently(templateId: String) {
        db.withTransaction {
            val ruleId = dao.template(templateId)?.recurrenceRuleId
            db.noteDao().deleteLinksOf(templateId)
            dao.deleteTemplate(templateId)
            ruleId?.let { recurrenceDao.deleteIfOrphan(it) }
        }
    }

    override suspend fun setItemChecked(stateId: String, checked: Boolean, at: Instant) = db.withTransaction {
        val state = dao.state(stateId) ?: return@withTransaction
        if (state.checked == checked) return@withTransaction
        dao.setChecked(stateId, checked, if (checked) at.toEpochMilli() else null)
        val before = dao.occurrence(state.occurrenceId)?.status
        refreshOpenStatus(state.occurrenceId, at.toEpochMilli())
        val after = dao.occurrence(state.occurrenceId)
        if (after != null && before != OccurrenceStatus.COMPLETED.name && after.status == OccurrenceStatus.COMPLETED.name) {
            dao.template(after.templateId)?.let {
                activity.log(ActivityType.CHECKLIST_COMPLETED, EntityType.CHECKLIST, it.id, it.title, it.projectId, at)
            }
        }
    }

    override suspend fun setOccurrenceStatus(occurrenceId: String, status: OccurrenceStatus, at: Instant) = db.withTransaction {
        val occ = dao.occurrence(occurrenceId) ?: return@withTransaction
        val ms = at.toEpochMilli()
        when (status) {
            OccurrenceStatus.SKIPPED -> dao.setOccurrenceStatus(occurrenceId, status.name, occ.closedAt ?: ms, null, ms)
            OccurrenceStatus.OPEN -> {
                dao.setOccurrenceStatus(occurrenceId, OccurrenceStatus.OPEN.name, null, null, ms)
                refreshOpenStatus(occurrenceId, ms)
            }
            else -> dao.setOccurrenceStatus(occurrenceId, status.name, occ.closedAt ?: ms, if (status == OccurrenceStatus.COMPLETED) ms else null, ms)
        }
    }

    override suspend fun ensureSingleOccurrence(templateId: String, date: LocalDate, now: Instant): String = db.withTransaction {
        dao.firstOccurrence(templateId)?.let { return@withTransaction it.id }
        createOccurrence(templateId, date, OccurrenceStatus.OPEN, dueAt = null, closedAt = null, withStates = true, nowMs = now.toEpochMilli())
            ?: dao.occurrenceAt(templateId, date.toEpochDay())!!.id
    }

    /** Retorna o id criado, ou null se já existia uma execução para (modelo, data). */
    private suspend fun createOccurrence(
        templateId: String,
        date: LocalDate,
        status: OccurrenceStatus,
        dueAt: Instant?,
        closedAt: Long?,
        withStates: Boolean,
        nowMs: Long,
    ): String? {
        val items = dao.items(templateId)
        val id = Ids.new()
        val inserted = dao.insertOccurrence(
            ChecklistOccurrenceEntity(
                id = id, templateId = templateId, date = date.toEpochDay(), status = status.name,
                totalItems = items.size, checkedItems = 0, dueAt = dueAt?.toEpochMilli(), closedAt = closedAt,
                completedAt = null, createdAt = nowMs, updatedAt = nowMs,
            ),
        )
        if (inserted == -1L) return null
        if (withStates) {
            dao.insertStates(items.map { ChecklistItemStateEntity(Ids.new(), id, it.id, it.title, it.sortOrder, false, null) })
        }
        return id
    }

    override suspend fun recurringTemplates(): List<Pair<ChecklistTemplate, RecurrenceRule>> =
        dao.recurringTemplates().mapNotNull { t ->
            val rule = t.recurrenceRuleId?.let { recurrenceDao.get(it) }?.toDomain() ?: return@mapNotNull null
            t.toDomain() to rule
        }

    override suspend fun openOccurrences(templateId: String): List<OpenOccurrence> =
        dao.openOccurrences(templateId).map { OpenOccurrence(it.id, LocalDate.ofEpochDay(it.date), it.totalItems, it.checkedItems) }

    override suspend fun applyPlan(templateId: String, plan: ReconcilePlan, dueAt: (LocalDate) -> Instant?, now: Instant) = db.withTransaction {
        val nowMs = now.toEpochMilli()
        plan.toClose.forEach { dao.closeOccurrence(it.occurrenceId, it.status.name, nowMs, nowMs) }
        plan.missedToCreate.forEach { date ->
            createOccurrence(templateId, date, OccurrenceStatus.MISSED, dueAt(date), closedAt = nowMs, withStates = false, nowMs = nowMs)
        }
        plan.activeToCreate?.let { date ->
            createOccurrence(templateId, date, OccurrenceStatus.OPEN, dueAt(date), closedAt = null, withStates = true, nowMs = nowMs)
        }
        dao.setGeneratedThrough(templateId, plan.generatedThrough?.toEpochDay())
    }

    override suspend fun updateTracking(templateId: String, trackingFrom: LocalDate?, generatedThrough: LocalDate?) =
        dao.setTracking(templateId, trackingFrom?.toEpochDay(), generatedThrough?.toEpochDay())

    override suspend fun discardUntouchedOpenOccurrences(templateId: String, keepDates: Set<LocalDate>) {
        dao.openOccurrences(templateId)
            .filter { LocalDate.ofEpochDay(it.date) !in keepDates && it.checkedItems == 0 }
            .forEach { dao.deleteOccurrence(it.id) }
    }

    override suspend fun openOccurrencesWithDue(): List<Pair<ChecklistTemplate, ChecklistOccurrence>> =
        dao.openWithDue().map { it.toPair() }

    private fun TemplateOccurrenceRow.toPair() = template.toDomain() to occurrence.toDomain()
}
