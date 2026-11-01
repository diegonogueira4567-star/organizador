package com.organizador.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.organizador.data.backup.BackupManager
import com.organizador.data.local.OrganizadorDatabase
import com.organizador.data.prefs.SettingsRepositoryImpl
import com.organizador.data.repository.ActivityRepositoryImpl
import com.organizador.data.repository.ChecklistRepositoryImpl
import com.organizador.data.repository.NoteRepositoryImpl
import com.organizador.data.repository.RoomTransactionRunner
import com.organizador.data.repository.StatisticsRepositoryImpl
import com.organizador.data.repository.TaskRepositoryImpl
import com.organizador.data.storage.AttachmentStorage
import com.organizador.domain.model.FixedTimeProvider
import com.organizador.domain.usecase.CompleteTaskUseCase
import com.organizador.domain.usecase.ConvertNoteItemsToTasksUseCase
import com.organizador.domain.usecase.ReconcileChecklistsUseCase
import com.organizador.domain.usecase.SaveChecklistUseCase
import com.organizador.domain.usecase.UndoCompletionUseCase
import java.time.LocalDateTime
import java.time.ZoneId

/** Monta o grafo real (Room + repositórios + casos de uso) com relógio controlado, sem Hilt. */
class TestGraph(
    val db: OrganizadorDatabase,
    val time: FixedTimeProvider,
) {
    val context: Context = ApplicationProvider.getApplicationContext()
    val storage = AttachmentStorage(context)
    val activity = ActivityRepositoryImpl(db)
    val settings = SettingsRepositoryImpl(context)
    val tx = RoomTransactionRunner(db)
    val tasks = TaskRepositoryImpl(db, time, activity, storage)
    val checklists = ChecklistRepositoryImpl(db, activity, time)
    val notes = NoteRepositoryImpl(db, storage, activity, time)
    val stats = StatisticsRepositoryImpl(db, time)
    val reconcile = ReconcileChecklistsUseCase(checklists, settings, activity, tx, time)
    val saveChecklist = SaveChecklistUseCase(checklists, settings, activity, reconcile, tx, time)
    val completeTask = CompleteTaskUseCase(tasks, activity, tx, time)
    val undoCompletion = UndoCompletionUseCase(tasks, activity, tx, time)
    val convertItems = ConvertNoteItemsToTasksUseCase(notes, tasks, tx, time)
    val backup = BackupManager(context, db, storage, settings, activity, time)

    fun setNow(dateTime: String, zone: String = "America/Sao_Paulo") {
        time.zoneId = ZoneId.of(zone)
        time.instant = LocalDateTime.parse(dateTime).atZone(time.zoneId).toInstant()
    }

    companion object {
        val SAO_PAULO: ZoneId = ZoneId.of("America/Sao_Paulo")

        fun inMemory(start: String = "2026-11-02T10:00"): TestGraph {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val db = Room.inMemoryDatabaseBuilder(context, OrganizadorDatabase::class.java).build()
            val time = FixedTimeProvider(LocalDateTime.parse(start).atZone(SAO_PAULO).toInstant(), SAO_PAULO)
            return TestGraph(db, time)
        }
    }
}
