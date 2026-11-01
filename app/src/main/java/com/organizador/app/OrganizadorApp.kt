package com.organizador.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.organizador.app.core.notifications.NotificationHelper
import com.organizador.app.core.sync.SyncCoordinator
import com.organizador.app.core.sync.ReconcileWorker
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class OrganizadorApp : Application(), Configuration.Provider {
    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var notifications: NotificationHelper
    @Inject lateinit var sync: SyncCoordinator

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        notifications.createChannels()
        // Rede de segurança: reconciliação periódica mesmo que o app nunca seja aberto.
        ReconcileWorker.schedulePeriodic(this)
        // Reage a qualquer alteração de lembretes (criação, edição, conclusão) reagendando alarmes.
        sync.start()
    }
}
