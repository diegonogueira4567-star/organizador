package com.organizador.domain.model

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.UUID

/** Identificadores estáveis (UUID v4 em texto). Nunca reutilizados, sobrevivem a backup/restauração. */
object Ids {
    fun new(): String = UUID.randomUUID().toString()
}

/**
 * Fonte única de tempo. Injetada para que regras de negócio sejam testáveis com relógio fixo
 * e para que mudanças de fuso do sistema sejam observadas a cada chamada.
 */
interface TimeProvider {
    fun now(): Instant
    fun zone(): ZoneId

    fun nowZoned(): ZonedDateTime = now().atZone(zone())
    fun today(): LocalDate = nowZoned().toLocalDate()

    /** Emite a data atual e cada virada de dia (inclusive por mudança de fuso), para consultas relativas a "hoje". */
    fun todayFlow(): Flow<LocalDate> = flow {
        while (true) {
            emit(today())
            delay(30_000)
        }
    }.distinctUntilChanged()
}

class SystemTimeProvider : TimeProvider {
    override fun now(): Instant = Instant.now()
    override fun zone(): ZoneId = ZoneId.systemDefault()
}

class FixedTimeProvider(var instant: Instant, var zoneId: ZoneId) : TimeProvider {
    override fun now(): Instant = instant
    override fun zone(): ZoneId = zoneId
}

/** Prioridade no estilo P1..P4 (P1 = mais urgente, P4 = sem prioridade). */
enum class Priority(val level: Int) {
    P1(1), P2(2), P3(3), P4(4);

    companion object {
        fun fromLevel(level: Int): Priority = entries.firstOrNull { it.level == level } ?: P4
    }
}

/** Paleta de cores disponíveis para projetos, categorias, etiquetas e pastas (ARGB). */
object Palette {
    val colors: List<Long> = listOf(
        0xFFDB4C3F, 0xFFE67E22, 0xFFF1C40F, 0xFF27AE60, 0xFF16A085, 0xFF2980B9,
        0xFF3F51B5, 0xFF8E44AD, 0xFFE91E63, 0xFF795548, 0xFF607D8B, 0xFF9E9E9E,
    )
    val default: Long = colors[5]
}
