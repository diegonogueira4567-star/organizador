package com.organizador.app

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.organizador.app.core.notifications.AlarmReceiver
import com.organizador.app.core.notifications.AlarmScheduler
import com.organizador.domain.model.TaskDraft
import com.organizador.domain.repository.ReminderRepository
import com.organizador.domain.repository.TaskRepository
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** Testes de notificações/lembretes: alarmes são criados, reagendados e cancelados a partir do banco. */
@HiltAndroidTest
class AlarmSchedulerTest {
    @get:Rule
    val hilt = HiltAndroidRule(this)

    @Inject lateinit var scheduler: AlarmScheduler
    @Inject lateinit var tasks: TaskRepository
    @Inject lateinit var reminders: ReminderRepository

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() = hilt.inject()

    private fun pendingFor(reminderId: String): PendingIntent? = PendingIntent.getBroadcast(
        context, 0,
        Intent(context, AlarmReceiver::class.java).setAction(AlarmReceiver.ACTION_ALARM).setData(Uri.parse("organizador://alarm/REMINDER/$reminderId")),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE,
    )

    @Test
    fun lembreteDeTarefaEhAgendadoECanceladoAoConcluir() = runBlocking {
        val id = tasks.save(TaskDraft(title = "Consulta", dueDate = LocalDate.now().plusDays(2), dueTime = LocalTime.of(15, 0), reminderOffsets = listOf(30)))
        val reminder = reminders.forOwner(id).single()
        scheduler.syncAll()
        assertNotNull("alarme deveria existir", pendingFor(reminder.id))

        // Sincronizar de novo (ex.: após reinicialização) não duplica nem perde o alarme.
        scheduler.syncAll()
        assertNotNull(pendingFor(reminder.id))

        tasks.setCompleted(id, true, java.time.Instant.now())
        scheduler.syncAll()
        assertNull("alarme de tarefa concluída deve ser cancelado", pendingFor(reminder.id))
    }

    @Test
    fun adiarReagendaOLembrete() = runBlocking {
        val id = tasks.save(TaskDraft(title = "Remédio", dueDate = LocalDate.now().plusDays(1), dueTime = LocalTime.of(8, 0), reminderOffsets = listOf(0)))
        val reminder = reminders.forOwner(id).single()
        reminders.markFired(reminder.id)
        scheduler.syncAll()
        assertNull(pendingFor(reminder.id))
        reminders.snooze(reminder.id, java.time.Instant.now().plusSeconds(600))
        scheduler.syncAll()
        assertNotNull(pendingFor(reminder.id))
        assertEquals(false, reminders.get(reminder.id)!!.fired)
    }
}
