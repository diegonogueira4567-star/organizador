package com.organizador.app

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import com.organizador.app.core.notifications.AlarmScheduler
import com.organizador.app.core.notifications.NotificationHelper
import com.organizador.domain.model.TaskDraft
import com.organizador.domain.repository.ReminderRepository
import com.organizador.domain.repository.TaskRepository
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import java.time.ZonedDateTime
import javax.inject.Inject
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** Disparo real: o AlarmManager entrega o alarme, o receiver exibe a notificação e marca o lembrete. */
@HiltAndroidTest
class ReminderDeliveryTest {
    @get:Rule(order = 0)
    val hilt = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val notificationPermission: GrantPermissionRule =
        if (Build.VERSION.SDK_INT >= 33) GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS) else GrantPermissionRule.grant()

    @Inject lateinit var scheduler: AlarmScheduler
    @Inject lateinit var tasks: TaskRepository
    @Inject lateinit var reminders: ReminderRepository
    @Inject lateinit var notifications: NotificationHelper

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        hilt.inject()
        notifications.createChannels()
        // Permite alarmes exatos para o pacote em teste (equivale ao usuário conceder a permissão).
        InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand("appops set ${context.packageName} SCHEDULE_EXACT_ALARM allow").close()
    }

    @Test
    fun lembreteDisparaENotificacaoAparece() = runBlocking {
        // Prazo daqui a ~70 s com lembrete "no horário": a precisão de minuto do prazo é respeitada.
        val due = ZonedDateTime.now().plusSeconds(70)
        val id = tasks.save(TaskDraft(title = "Teste de lembrete", dueDate = due.toLocalDate(), dueTime = due.toLocalTime().withSecond(0).withNano(0), reminderOffsets = listOf(0)))
        val reminder = reminders.forOwner(id).single()
        scheduler.syncAll()

        val manager = context.getSystemService(NotificationManager::class.java)
        val deadline = System.currentTimeMillis() + 150_000
        var shown = false
        while (System.currentTimeMillis() < deadline && !shown) {
            Thread.sleep(2_000)
            shown = manager.activeNotifications.any { it.notification.extras.getString("android.title") == "Teste de lembrete" }
        }
        assertTrue("a notificação do lembrete deveria ter sido exibida", shown)
        assertTrue("lembrete deve ficar marcado como disparado", reminders.get(reminder.id)!!.fired)
        manager.cancelAll()
    }
}
