package com.organizador.data.local

import androidx.room.InvalidationTracker
import androidx.room.RoomDatabase
import com.organizador.data.mapper.toDomain
import com.organizador.domain.model.Category
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate

/** Emite imediatamente e a cada invalidação das tabelas informadas (para agregados de várias tabelas). */
fun RoomDatabase.invalidationFlow(vararg tables: String): Flow<Unit> = callbackFlow {
    val observer = object : InvalidationTracker.Observer(tables) {
        override fun onInvalidated(tables: Set<String>) {
            trySend(Unit)
        }
    }
    invalidationTracker.addObserver(observer)
    trySend(Unit)
    awaitClose { invalidationTracker.removeObserver(observer) }
}.conflate()

suspend fun OrganizadorDatabase.categoryById(id: String): Category? = labelDao().category(id)?.toDomain()
