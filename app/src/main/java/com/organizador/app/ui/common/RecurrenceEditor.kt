package com.organizador.app.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.organizador.app.ui.theme.LocalAppSettings
import com.organizador.domain.recurrence.Frequency
import com.organizador.domain.recurrence.RecurrenceEngine
import com.organizador.domain.recurrence.RecurrencePreset
import com.organizador.domain.recurrence.RecurrenceRule
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId

private enum class Choice(val label: String) {
    NONE("Não repetir"), DAILY("Todos os dias"), WEEKDAYS("Seg. a sex."), WEEKENDS("Fins de semana"),
    SATURDAYS("Sábados"), SUNDAYS("Domingos"), CUSTOM_DAYS("Dias da semana"), MONTH_DAYS("Dias do mês"),
    YEARLY("Anual"), INTERVAL("A cada N dias"), ADVANCED("Personalizado"),
}

private fun choiceOf(rule: RecurrenceRule?): Choice = when {
    rule == null || rule.frequency == Frequency.NONE -> Choice.NONE
    rule.frequency == Frequency.DAILY && rule.interval == 1 -> Choice.DAILY
    rule.frequency == Frequency.DAILY -> Choice.INTERVAL
    rule.frequency == Frequency.WEEKLY && rule.interval == 1 && rule.daysOfWeek == RecurrenceRule.WEEKDAYS -> Choice.WEEKDAYS
    rule.frequency == Frequency.WEEKLY && rule.interval == 1 && rule.daysOfWeek == RecurrenceRule.WEEKEND -> Choice.WEEKENDS
    rule.frequency == Frequency.WEEKLY && rule.interval == 1 && rule.daysOfWeek == setOf(DayOfWeek.SATURDAY) -> Choice.SATURDAYS
    rule.frequency == Frequency.WEEKLY && rule.interval == 1 && rule.daysOfWeek == setOf(DayOfWeek.SUNDAY) -> Choice.SUNDAYS
    rule.frequency == Frequency.WEEKLY && rule.interval == 1 -> Choice.CUSTOM_DAYS
    rule.frequency == Frequency.MONTHLY && rule.interval == 1 -> Choice.MONTH_DAYS
    rule.frequency == Frequency.YEARLY && rule.interval == 1 -> Choice.YEARLY
    else -> Choice.ADVANCED
}

/**
 * Editor completo de recorrência: presets, dias da semana, datas do mês, intervalo, início/fim,
 * horário de execução, horário de reinicialização, fuso e ativação.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RecurrenceEditor(
    rule: RecurrenceRule?,
    defaultStart: LocalDate,
    onChange: (RecurrenceRule?) -> Unit,
    allowNone: Boolean = true,
    showResetTime: Boolean = false,
    showTime: Boolean = true,
) {
    val settings = LocalAppSettings.current
    val choice = choiceOf(rule)
    var pickStart by remember { mutableStateOf(false) }
    var pickEnd by remember { mutableStateOf(false) }
    var pickTime by remember { mutableStateOf(false) }
    var pickReset by remember { mutableStateOf(false) }

    fun keep(new: RecurrenceRule): RecurrenceRule = rule?.let {
        new.copy(id = it.id, endDate = it.endDate?.takeIf { e -> !e.isBefore(new.startDate) }, timeOfDay = it.timeOfDay, resetTime = it.resetTime, zoneId = it.zoneId, enabled = it.enabled)
    } ?: new

    fun select(c: Choice) {
        val start = rule?.startDate ?: defaultStart
        val id = rule?.id ?: ""
        val built: RecurrenceRule? = when (c) {
            Choice.NONE -> null
            Choice.DAILY -> RecurrencePreset.EVERY_DAY.build(id, start)
            Choice.WEEKDAYS -> RecurrencePreset.WEEKDAYS.build(id, start)
            Choice.WEEKENDS -> RecurrencePreset.WEEKENDS.build(id, start)
            Choice.SATURDAYS -> RecurrencePreset.SATURDAYS.build(id, start)
            Choice.SUNDAYS -> RecurrencePreset.SUNDAYS.build(id, start)
            Choice.CUSTOM_DAYS -> RecurrencePreset.CUSTOM_WEEKDAYS.build(id, start, customDays = rule?.daysOfWeek.orEmpty())
            Choice.MONTH_DAYS -> RecurrencePreset.MONTHLY_DATES.build(id, start, monthDays = rule?.daysOfMonth.orEmpty())
            Choice.YEARLY -> RecurrencePreset.YEARLY.build(id, start)
            Choice.INTERVAL -> RecurrencePreset.EVERY_N_DAYS.build(id, start, everyNDays = rule?.interval?.coerceAtLeast(2) ?: 2)
            Choice.ADVANCED -> (rule ?: RecurrencePreset.EVERY_DAY.build(id, start)).copy(interval = maxOf(2, rule?.interval ?: 2))
        }
        onChange(built?.let { keep(it) })
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Choice.entries.filter { allowNone || it != Choice.NONE }.forEach { c ->
                FilterChip(selected = c == choice, onClick = { select(c) }, label = { Text(c.label) })
            }
        }
        if (rule == null) return@Column

        if (choice == Choice.ADVANCED) {
            Text("Frequência", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(Frequency.DAILY to "Dias", Frequency.WEEKLY to "Semanas", Frequency.MONTHLY to "Meses", Frequency.YEARLY to "Anos").forEach { (f, label) ->
                    FilterChip(selected = rule.frequency == f, onClick = { onChange(rule.copy(frequency = f)) }, label = { Text(label) })
                }
            }
        }
        if (choice == Choice.INTERVAL || choice == Choice.ADVANCED) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("A cada", modifier = Modifier.weight(1f))
                IconButton(onClick = { onChange(rule.copy(interval = (rule.interval - 1).coerceAtLeast(1))) }) { Icon(Icons.Default.Remove, "Diminuir") }
                Text("${rule.interval}", style = MaterialTheme.typography.titleMedium)
                IconButton(onClick = { onChange(rule.copy(interval = (rule.interval + 1).coerceAtMost(365))) }) { Icon(Icons.Default.Add, "Aumentar") }
                Text(
                    when (rule.frequency) {
                        Frequency.DAILY -> "dia(s)"; Frequency.WEEKLY -> "semana(s)"; Frequency.MONTHLY -> "mês(es)"; else -> "ano(s)"
                    },
                    modifier = Modifier.width(72.dp),
                )
            }
        }
        if (rule.frequency == Frequency.WEEKLY && (choice == Choice.CUSTOM_DAYS || choice == Choice.ADVANCED)) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                orderedWeek(settings.firstDayOfWeek).forEach { day ->
                    val selected = day in rule.daysOfWeek
                    FilterChip(
                        selected = selected,
                        onClick = {
                            val days = if (selected) rule.daysOfWeek - day else rule.daysOfWeek + day
                            if (days.isNotEmpty()) onChange(rule.copy(daysOfWeek = days))
                        },
                        label = { Text(Formats.weekdayLetter(day).take(1)) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        if (rule.frequency == Frequency.MONTHLY && (choice == Choice.MONTH_DAYS || choice == Choice.ADVANCED)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                ((1..31).toList() + RecurrenceRule.LAST_DAY_OF_MONTH).forEach { day ->
                    val selected = day in rule.daysOfMonth
                    FilterChip(
                        selected = selected,
                        onClick = {
                            val days = if (selected) rule.daysOfMonth - day else rule.daysOfMonth + day
                            if (days.isNotEmpty()) onChange(rule.copy(daysOfMonth = days))
                        },
                        label = { Text(if (day == RecurrenceRule.LAST_DAY_OF_MONTH) "Último" else "$day") },
                    )
                }
            }
        }

        Text(Formats.recurrence(rule), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
        val next = RecurrenceEngine.occurrencesBetween(rule, maxOf(rule.startDate, LocalDate.now()), LocalDate.now().plusYears(2), limit = 3)
        if (next.isNotEmpty()) {
            Text(
                "Próximas: " + next.joinToString(", ") { Formats.shortDate(it, LocalDate.now()) },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        SettingLine("Início", Formats.date(rule.startDate, settings.dateFormat)) { pickStart = true }
        SettingLine("Término", rule.endDate?.let { Formats.date(it, settings.dateFormat) } ?: "Sem data final") { pickEnd = true }
        if (showTime) {
            SettingLine("Horário de execução", rule.timeOfDay?.let { Formats.time(it, settings.timeFormat) } ?: "Sem horário") { pickTime = true }
        }
        if (showResetTime) {
            SettingLine(
                "Reinicia às",
                rule.resetTime?.let { Formats.time(it, settings.timeFormat) } ?: "Padrão (${Formats.time(settings.routineResetTime, settings.timeFormat)})",
            ) { pickReset = true }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Fixar fuso horário")
                Text(
                    rule.zoneId ?: "Acompanha o fuso do aparelho (${ZoneId.systemDefault().id})",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = rule.zoneId != null, onCheckedChange = { onChange(rule.copy(zoneId = if (it) ZoneId.systemDefault().id else null)) })
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Recorrência ativa", modifier = Modifier.weight(1f))
            Switch(checked = rule.enabled, onCheckedChange = { onChange(rule.copy(enabled = it)) })
        }
    }

    if (pickStart) DatePickerModal(rule?.startDate, { d -> if (d != null && rule != null) onChange(rule.copy(startDate = d, endDate = rule.endDate?.takeIf { !it.isBefore(d) })) }, { pickStart = false }, allowClear = false)
    if (pickEnd) DatePickerModal(rule?.endDate, { d -> rule?.let { r -> onChange(r.copy(endDate = d?.takeIf { !it.isBefore(r.startDate) })) } }, { pickEnd = false })
    if (pickTime) TimePickerModal(rule?.timeOfDay, { t -> rule?.let { onChange(it.copy(timeOfDay = t)) } }, { pickTime = false })
    if (pickReset) TimePickerModal(rule?.resetTime ?: settings.routineResetTime, { t -> rule?.let { onChange(it.copy(resetTime = t)) } }, { pickReset = false })
}

@Composable
fun SettingLine(label: String, value: String, onClick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(label, modifier = Modifier.weight(1f))
        TextButton(onClick = onClick) { Text(value) }
    }
}

fun orderedWeek(first: DayOfWeek): List<DayOfWeek> = (0L until 7L).map { first.plus(it) }
