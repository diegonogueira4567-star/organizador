package com.organizador.domain.note

/**
 * Manutenção dos intervalos de estilo quando o texto de um bloco é editado.
 * O editor trabalha com texto puro + spans; após cada alteração, os spans são deslocados conforme a
 * diferença entre o texto antigo e o novo (prefixo/sufixo comuns), sem depender de HTML.
 */
object SpanEditing {

    fun adjust(oldText: String, newText: String, spans: List<TextSpan>): List<TextSpan> {
        if (oldText == newText || spans.isEmpty()) return spans
        var prefix = 0
        val minLen = minOf(oldText.length, newText.length)
        while (prefix < minLen && oldText[prefix] == newText[prefix]) prefix++
        var suffix = 0
        while (suffix < minLen - prefix &&
            oldText[oldText.length - 1 - suffix] == newText[newText.length - 1 - suffix]
        ) suffix++
        val removedEnd = oldText.length - suffix // trecho removido: [prefix, removedEnd)
        val inserted = newText.length - suffix - prefix
        val delta = inserted - (removedEnd - prefix)

        // Inserção exatamente no início de um span fica fora dele; no fim, estende o estilo
        // (comportamento usual de editores). Posições dentro do trecho substituído colapsam para a inserção.
        fun mapStart(pos: Int): Int = if (pos < prefix) pos else if (pos >= removedEnd) pos + delta else prefix
        fun mapEnd(pos: Int): Int = if (pos < prefix) pos else if (pos >= removedEnd) pos + delta else prefix + inserted

        return spans.mapNotNull { span ->
            val start = mapStart(span.start)
            val end = mapEnd(span.end)
            val clampedEnd = end.coerceAtMost(newText.length)
            val clampedStart = start.coerceIn(0, clampedEnd)
            if (clampedEnd > clampedStart) span.copy(start = clampedStart, end = clampedEnd) else null
        }.let(::normalize)
    }

    /** Aplica ou remove um estilo no intervalo selecionado. */
    fun toggle(spans: List<TextSpan>, start: Int, end: Int, style: SpanStyle, value: String? = null): List<TextSpan> {
        if (end <= start) return spans
        val fullyCovered = isFullyCovered(spans, start, end, style)
        val withoutRange = removeRange(spans, start, end, style)
        return if (fullyCovered && (style != SpanStyle.HIGHLIGHT && style != SpanStyle.LINK || value == null)) {
            normalize(withoutRange)
        } else {
            normalize(withoutRange + TextSpan(start, end, style, value))
        }
    }

    fun isFullyCovered(spans: List<TextSpan>, start: Int, end: Int, style: SpanStyle): Boolean {
        if (end <= start) return false
        val relevant = spans.filter { it.style == style && it.end > start && it.start < end }.sortedBy { it.start }
        var cursor = start
        for (span in relevant) {
            if (span.start > cursor) return false
            cursor = maxOf(cursor, span.end)
            if (cursor >= end) return true
        }
        return cursor >= end
    }

    private fun removeRange(spans: List<TextSpan>, start: Int, end: Int, style: SpanStyle): List<TextSpan> =
        spans.flatMap { span ->
            if (span.style != style || span.end <= start || span.start >= end) {
                listOf(span)
            } else {
                buildList {
                    if (span.start < start) add(span.copy(end = start))
                    if (span.end > end) add(span.copy(start = end))
                }
            }
        }

    /** Une spans adjacentes/sobrepostos do mesmo estilo e valor e remove intervalos vazios. */
    fun normalize(spans: List<TextSpan>): List<TextSpan> {
        val result = ArrayList<TextSpan>()
        spans.filter { it.end > it.start }
            .groupBy { it.style to it.value }
            .forEach { (_, group) ->
                val sorted = group.sortedBy { it.start }
                var current = sorted.first()
                for (span in sorted.drop(1)) {
                    current = if (span.start <= current.end) current.copy(end = maxOf(current.end, span.end)) else {
                        result += current
                        span
                    }
                }
                result += current
            }
        return result.sortedWith(compareBy({ it.start }, { it.style.ordinal }))
    }
}
