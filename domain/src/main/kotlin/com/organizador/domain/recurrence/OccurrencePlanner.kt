package com.organizador.domain.recurrence

import com.organizador.domain.model.OccurrenceStatus
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/** Execução ainda aberta conhecida pelo banco. */
data class OpenOccurrence(val id: String, val date: LocalDate, val totalItems: Int, val checkedItems: Int)

data class ReconcileInput(
    val rule: RecurrenceRule,
    /** Não registrar execuções perdidas antes desta data. */
    val trackingFrom: LocalDate,
    val generatedThrough: LocalDate?,
    val openOccurrences: List<OpenOccurrence>,
    /** Instante atual já convertido para o fuso efetivo da regra. */
    val now: ZonedDateTime,
    val resetTime: LocalTime,
)

data class OccurrenceClosure(val occurrenceId: String, val status: OccurrenceStatus)

data class ReconcilePlan(
    /** Execução da janela atual a ser criada (com estados de itens pendentes). */
    val activeToCreate: LocalDate?,
    /** Execuções cuja janela já terminou sem registro: gravadas como MISSED (sem estados de itens). */
    val missedToCreate: List<LocalDate>,
    val toClose: List<OccurrenceClosure>,
    val generatedThrough: LocalDate?,
    /** true se o período sem reconciliação excedeu o limite e execuções muito antigas foram ignoradas. */
    val truncated: Boolean,
) {
    val isEmpty: Boolean get() = activeToCreate == null && missedToCreate.isEmpty() && toClose.isEmpty()
}

/**
 * Decide, de forma pura e determinística, quais execuções devem existir em um dado instante.
 *
 * Semântica da janela: a execução da data D fica aberta de D@reset até o início da próxima ocorrência
 * (ou até o dia seguinte ao fim da regra). Assim, uma rotina de sábado pode ser concluída até a véspera
 * do sábado seguinte; ao abrir a nova execução, a anterior é fechada como COMPLETED/PARTIAL/MISSED.
 *
 * Idempotência: o plano depende só da entrada; aplicá-lo duas vezes não cria duplicatas porque o banco
 * possui índice único (template, data) e as inserções ignoram conflitos.
 */
object OccurrencePlanner {
    /** Limite de recuperação retroativa (dias) quando o app/sistema ficou muito tempo sem reconciliar. */
    const val MAX_LOOKBACK_DAYS = 400L

    /** Data "lógica" atual: antes do horário de reinicialização ainda vale o dia anterior. */
    fun effectiveDate(now: ZonedDateTime, resetTime: LocalTime): LocalDate =
        if (now.toLocalTime().isBefore(resetTime)) now.toLocalDate().minusDays(1) else now.toLocalDate()

    /** java.time ajusta horários inexistentes (lacuna do horário de verão) para frente. */
    fun windowStart(date: LocalDate, resetTime: LocalTime, zone: ZoneId): ZonedDateTime =
        ZonedDateTime.of(date, resetTime, zone)

    fun windowEnd(rule: RecurrenceRule, date: LocalDate, resetTime: LocalTime, zone: ZoneId): ZonedDateTime {
        val endDate = RecurrenceEngine.nextAfter(rule, date)
            ?: (rule.endDate?.takeIf { !it.isBefore(date) } ?: date).plusDays(1)
        return windowStart(endDate, resetTime, zone)
    }

    fun isWindowOpen(rule: RecurrenceRule, date: LocalDate, now: ZonedDateTime, resetTime: LocalTime): Boolean =
        now.isBefore(windowEnd(rule, date, resetTime, now.zone))

    /**
     * Ponto de partida do acompanhamento quando a regra é criada, alterada ou reativada: a ocorrência
     * cuja janela está aberta agora (se a regra já começou), senão o dia atual. Evita registrar dezenas
     * de execuções "perdidas" só porque a data inicial escolhida está no passado.
     */
    fun initialTrackingFrom(rule: RecurrenceRule, now: ZonedDateTime, resetTime: LocalTime): LocalDate {
        val effective = effectiveDate(now, resetTime)
        return RecurrenceEngine.previousOnOrBefore(rule, effective)
            ?.takeIf { isWindowOpen(rule, it, now, resetTime) }
            ?: effective
    }

    fun closingStatus(total: Int, checked: Int): OccurrenceStatus = when {
        total > 0 && checked >= total -> OccurrenceStatus.COMPLETED
        checked > 0 -> OccurrenceStatus.PARTIAL
        else -> OccurrenceStatus.MISSED
    }

    fun plan(input: ReconcileInput): ReconcilePlan {
        val rule = input.rule
        val effective = effectiveDate(input.now, input.resetTime)

        val closures = input.openOccurrences
            .filter { !isWindowOpen(rule, it.date, input.now, input.resetTime) }
            .map { OccurrenceClosure(it.id, closingStatus(it.totalItems, it.checkedItems)) }

        if (!rule.enabled) {
            return ReconcilePlan(null, emptyList(), closures, input.generatedThrough, truncated = false)
        }

        var lower = maxOf(rule.startDate, input.trackingFrom)
        input.generatedThrough?.let { lower = maxOf(lower, it.plusDays(1)) }
        val lookbackLimit = effective.minusDays(MAX_LOOKBACK_DAYS)
        val truncated = lower.isBefore(lookbackLimit) &&
            RecurrenceEngine.nextOnOrAfter(rule, lower)?.isBefore(lookbackLimit) == true
        if (lower.isBefore(lookbackLimit)) lower = lookbackLimit

        val dates = RecurrenceEngine.occurrencesBetween(rule, lower, effective, limit = Int.MAX_VALUE)
        val existing = input.openOccurrences.mapTo(HashSet()) { it.date }
        var active: LocalDate? = null
        val missed = ArrayList<LocalDate>()
        for (date in dates) {
            if (date in existing) continue
            if (isWindowOpen(rule, date, input.now, input.resetTime)) active = date else missed += date
        }
        val through = dates.lastOrNull()?.let { last -> input.generatedThrough?.let { maxOf(it, last) } ?: last }
            ?: input.generatedThrough
        return ReconcilePlan(active, missed, closures, through, truncated)
    }
}
