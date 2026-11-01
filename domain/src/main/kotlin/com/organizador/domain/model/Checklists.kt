package com.organizador.domain.model

import com.organizador.domain.recurrence.RecurrenceRule
import java.time.Instant
import java.time.LocalDate

enum class ChecklistKind {
    /** Lista única; concluída quando todos os itens forem marcados. */
    SIMPLE,

    /** Gera uma execução por ocorrência da regra (diário, semanal, mensal, anual, intervalado...). */
    RECURRING,

    /** Lista fixa que nunca reinicia nem fecha. */
    PERMANENT,

    /** Modelo reutilizável: não possui execuções; gera cópias sob demanda. */
    TEMPLATE,
}

/** A. Modelo do checklist. */
data class ChecklistTemplate(
    val id: String,
    val title: String,
    val description: String = "",
    val kind: ChecklistKind,
    /** Exibido em "Rotinas" em vez de "Checklists". */
    val isRoutine: Boolean = false,
    val projectId: String? = null,
    val color: Long = Palette.default,
    val icon: String = "checklist",
    val recurrenceRuleId: String? = null,
    /** Execuções perdidas só são registradas a partir desta data (criação/reativação/alteração da regra). */
    val trackingFrom: LocalDate? = null,
    /** Última data de ocorrência já materializada (otimização; a unicidade é garantida pelo banco). */
    val generatedThrough: LocalDate? = null,
    val archived: Boolean = false,
    val deletedAt: Instant? = null,
    val sortOrder: Int = 0,
    val createdAt: Instant,
    val updatedAt: Instant,
)

/** B. Item pertencente ao modelo. */
data class ChecklistItem(
    val id: String,
    val templateId: String,
    val title: String,
    val sortOrder: Int,
    val createdAt: Instant,
)

enum class OccurrenceStatus {
    /** Janela aberta, ainda não concluída. */
    OPEN,

    /** Todos os itens marcados. */
    COMPLETED,

    /** Janela encerrada com parte dos itens marcados. */
    PARTIAL,

    /** Janela encerrada sem nenhum item marcado (execução perdida). */
    MISSED,

    /** Pulada explicitamente pelo usuário. */
    SKIPPED;

    val isFinal: Boolean get() = this != OPEN
}

/** D. Execução individual do checklist (identificada por modelo + data). */
data class ChecklistOccurrence(
    val id: String,
    val templateId: String,
    val date: LocalDate,
    val status: OccurrenceStatus,
    val totalItems: Int,
    val checkedItems: Int,
    val dueAt: Instant? = null,
    val closedAt: Instant? = null,
    val completedAt: Instant? = null,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    val isOpen: Boolean get() = closedAt == null
    val progress: Float get() = if (totalItems == 0) 0f else checkedItems.toFloat() / totalItems
}

/** E. Estado de um item em uma execução. O título é copiado para manter o histórico imutável. */
data class ChecklistItemState(
    val id: String,
    val occurrenceId: String,
    val itemId: String?,
    val title: String,
    val sortOrder: Int,
    val checked: Boolean,
    val checkedAt: Instant? = null,
)

data class ChecklistSummary(
    val template: ChecklistTemplate,
    val rule: RecurrenceRule?,
    val itemCount: Int,
    /** Execução aberta mais recente (ou a única, para listas simples/permanentes). */
    val current: ChecklistOccurrence?,
    val nextDate: LocalDate?,
)

data class ChecklistDetails(
    val template: ChecklistTemplate,
    val items: List<ChecklistItem>,
    val rule: RecurrenceRule?,
)

data class OccurrenceDetails(
    val occurrence: ChecklistOccurrence,
    val template: ChecklistTemplate,
    val states: List<ChecklistItemState>,
)

data class ChecklistDraft(
    val id: String? = null,
    val title: String,
    val description: String = "",
    val kind: ChecklistKind,
    val isRoutine: Boolean = false,
    val projectId: String? = null,
    val color: Long = Palette.default,
    val items: List<ChecklistItemDraft>,
    val recurrence: RecurrenceRule? = null,
    val remind: Boolean = false,
)

data class ChecklistItemDraft(val id: String? = null, val title: String)
