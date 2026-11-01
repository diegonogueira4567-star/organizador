package com.organizador.app.core.sync

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.organizador.app.core.notifications.AlarmScheduler
import com.organizador.domain.repository.ReminderRepository
import com.organizador.domain.usecase.ReconcileChecklistsUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Ponto único que mantém o estado derivado em dia: execuções de checklists (reconciliação) e alarmes.
 * Disparado na abertura do app, pelo WorkManager, por alarmes de virada de janela e por eventos do sistema.
 */
@Singleton
class SyncCoordinator @Inject constructor(
    private val reconcile: ReconcileChecklistsUseCase,
    private val scheduler: AlarmScheduler,
    private val reminders: ReminderRepository,
) {
    private val mutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @OptIn(FlowPreview::class)
    fun start() {
        scope.launch { fullSync() }
        scope.launch {
            reminders.observePending().drop(1).debounce(300).collect {
                mutex.withLock { scheduler.syncAll() }
            }
        }
    }

    fun requestSync() {
        scope.launch { fullSync() }
    }

    suspend fun fullSync() = mutex.withLock {
        try {
            val report = reconcile()
            Log.i(TAG, "reconciliação: ${report.templates} modelos, ${report.created} novas, ${report.missed} perdidas, ${report.closed} fechadas")
        } catch (e: Exception) {
            // Falha na reconciliação não pode impedir o reagendamento dos lembretes; o erro é registrado
            // e a próxima execução (periódica ou por evento) tenta novamente.
            Log.e(TAG, "falha na reconciliação de checklists", e)
        }
        scheduler.syncAll()
    }

    companion object {
        const val TAG = "OrganizadorSync"
    }
}

@HiltWorker
class ReconcileWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val sync: SyncCoordinator,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = try {
        sync.fullSync()
        Result.success()
    } catch (e: Exception) {
        Log.e(SyncCoordinator.TAG, "ReconcileWorker falhou", e)
        if (runAttemptCount < 3) Result.retry() else Result.failure()
    }

    companion object {
        private const val PERIODIC = "reconcile-periodic"
        private const val NOW = "reconcile-now"

        fun schedulePeriodic(context: Context) {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC,
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<ReconcileWorker>(6, TimeUnit.HOURS).build(),
            )
        }

        fun runNow(context: Context) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                NOW,
                ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<ReconcileWorker>().build(),
            )
        }
    }
}
