package com.organizador.domain.usecase

import com.organizador.domain.model.ActivityType
import com.organizador.domain.model.ChecklistDraft
import com.organizador.domain.model.ChecklistKind
import com.organizador.domain.model.EntityType
import com.organizador.domain.model.TimeProvider
import com.organizador.domain.recurrence.OccurrencePlanner
import com.organizador.domain.recurrence.RecurrenceEngine
import com.organizador.domain.recurrence.RecurrenceRule
import com.organizador.domain.recurrence.ReconcileInput
import com.organizador.domain.repository.ActivityRepository
import com.organizador.domain.repository.ChecklistRepository
import com.organizador.domain.repository.SettingsRepository
import com.organizador.domain.repository.TransactionRunner
import java.time.LocalDate
import java.time.ZonedDateTime
import javax.inject.Inject

data class ReconcileReport(val templates: Int, val created: Int, val missed: Int, val closed: Int, val truncated: Int)

/**
 * Materializa as execuções devidas de todos os checklists recorrentes. Chamado na abertura do app,
 * periodicamente pelo WorkManager, por alarmes na virada de cada janela e após reinicialização ou
 * mudança de data/fuso. Seguro para execuções concorrentes e repetidas (idempotente).
 */
class ReconcileChecklistsUseCase @Inject constructor(
    private val checklists: ChecklistRepository,
    private val settings: SettingsRepository,
    private val activity: ActivityRepository,
    private val tx: TransactionRunner,
    private val time: TimeProvider,
) {
    suspend operator fun invoke(): ReconcileReport {
        val defaultReset = settings.current().routineResetTime
        var created = 0
        var missed = 0
        var closed = 0
        var truncated = 0
        val templates = checklists.recurringTemplates()
        for ((template, rule) in templates) {
            val zone = rule.zone(time.zone())
            val now = time.now().atZone(zone)
            val reset = rule.resetTime ?: defaultReset
            tx.inTransaction {
                val trackingFrom = template.trackingFrom ?: OccurrencePlanner.initialTrackingFrom(rule, now, reset).also {
                    checklists.updateTracking(template.id, it, template.generatedThrough)
                }
                val plan = OccurrencePlanner.plan(
                    ReconcileInput(rule, trackingFrom, template.generatedThrough, checklists.openOccurrences(template.id), now, reset),
                )
                if (!plan.isEmpty || plan.generatedThrough != template.generatedThrough) {
                    checklists.applyPlan(
                        template.id,
                        plan,
                        dueAt = { date -> rule.timeOfDay?.let { ZonedDateTime.of(date, it, zone).toInstant() } },
                        now = time.now(),
                    )
                    plan.missedToCreate.lastOrNull()?.let {
                        activity.log(ActivityType.CHECKLIST_MISSED, EntityType.CHECKLIST, template.id, template.title, template.projectId, time.now())
                    }
                }
                if (plan.activeToCreate != null) created++
                missed += plan.missedToCreate.size
                closed += plan.toClose.size
                if (plan.truncated) truncated++
            }
        }
        return ReconcileReport(templates.size, created, missed, closed, truncated)
    }
}

/** Cria/edita um checklist mantendo o histórico imutável e recalculando o acompanhamento quando a regra muda. */
class SaveChecklistUseCase @Inject constructor(
    private val checklists: ChecklistRepository,
    private val settings: SettingsRepository,
    private val activity: ActivityRepository,
    private val reconcile: ReconcileChecklistsUseCase,
    private val tx: TransactionRunner,
    private val time: TimeProvider,
) {
    suspend operator fun invoke(draft: ChecklistDraft): String {
        require(draft.title.isNotBlank()) { "Título obrigatório" }
        val existing = draft.id?.let { checklists.getDetails(it) }
        val rule = draft.recurrence.takeIf { draft.kind == ChecklistKind.RECURRING }
        require(draft.kind != ChecklistKind.RECURRING || rule != null) { "Checklist recorrente exige regra" }
        val now = time.now()
        val defaultReset = settings.current().routineResetTime

        val ruleChanged = existing == null || existing.template.kind != draft.kind || !sameSchedule(existing.rule, rule)
        val newTracking: LocalDate? = if (rule != null && ruleChanged) {
            OccurrencePlanner.initialTrackingFrom(rule, now.atZone(rule.zone(time.zone())), rule.resetTime ?: defaultReset)
        } else {
            existing?.template?.trackingFrom
        }

        val id = tx.inTransaction {
            val id = checklists.save(draft.copy(recurrence = rule), now, newTracking)
            if (existing != null && ruleChanged) {
                // Execução aberta que não pertence mais à nova regra e não teve progresso é descartada;
                // execuções com progresso e todo o histórico fechado são preservados.
                val keep = rule?.let { r ->
                    checklists.openOccurrences(id).map { it.date }.filter { RecurrenceEngine.matches(r, it) }.toSet()
                }.orEmpty()
                checklists.discardUntouchedOpenOccurrences(id, keep)
                checklists.updateTracking(id, newTracking, null)
            }
            if (draft.kind == ChecklistKind.SIMPLE || draft.kind == ChecklistKind.PERMANENT) {
                checklists.ensureSingleOccurrence(id, time.today(), now)
            }
            if (existing == null) {
                activity.log(ActivityType.CHECKLIST_CREATED, EntityType.CHECKLIST, id, draft.title, draft.projectId, now)
            }
            id
        }
        if (draft.kind == ChecklistKind.RECURRING) reconcile()
        return id
    }

    private fun sameSchedule(a: RecurrenceRule?, b: RecurrenceRule?): Boolean =
        a?.copy(id = "") == b?.copy(id = "")
}

/** Cria um checklist simples a partir de um modelo reutilizável. */
class InstantiateTemplateUseCase @Inject constructor(
    private val checklists: ChecklistRepository,
    private val save: SaveChecklistUseCase,
) {
    suspend operator fun invoke(templateId: String, title: String? = null): String? {
        val details = checklists.getDetails(templateId) ?: return null
        return save(
            ChecklistDraft(
                title = title ?: details.template.title,
                description = details.template.description,
                kind = ChecklistKind.SIMPLE,
                projectId = details.template.projectId,
                color = details.template.color,
                items = details.items.map { com.organizador.domain.model.ChecklistItemDraft(title = it.title) },
            ),
        )
    }
}
