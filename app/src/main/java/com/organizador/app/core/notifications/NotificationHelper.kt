package com.organizador.app.core.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.organizador.app.MainActivity
import com.organizador.app.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationHelper @Inject constructor(@ApplicationContext private val context: Context) {
    private val manager = NotificationManagerCompat.from(context)

    fun createChannels() {
        val system = context.getSystemService(NotificationManager::class.java) ?: return
        system.createNotificationChannels(
            listOf(
                NotificationChannel(CH_REMINDERS, context.getString(R.string.channel_reminders), NotificationManager.IMPORTANCE_HIGH)
                    .apply { description = context.getString(R.string.channel_reminders_desc) },
                NotificationChannel(CH_ROUTINES, context.getString(R.string.channel_routines), NotificationManager.IMPORTANCE_DEFAULT)
                    .apply { description = context.getString(R.string.channel_routines_desc) },
                NotificationChannel(CH_SUMMARY, context.getString(R.string.channel_summary), NotificationManager.IMPORTANCE_LOW)
                    .apply { description = context.getString(R.string.channel_summary_desc) },
            ),
        )
    }

    fun canPost(): Boolean =
        manager.areNotificationsEnabled() &&
            (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)

    fun showTaskReminder(taskId: String, reminderId: String, title: String, text: String, snoozeMinutes: Int) {
        val id = notificationId("task", taskId)
        val builder = NotificationCompat.Builder(context, CH_REMINDERS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(openRoute("task/$taskId", id))
            .addAction(0, "Concluir", action(NotificationActionReceiver.ACTION_COMPLETE, id) { putExtra(NotificationActionReceiver.EXTRA_TASK, taskId) })
            .addAction(0, "Adiar ${snoozeMinutes} min", action(NotificationActionReceiver.ACTION_SNOOZE, id) {
                putExtra(NotificationActionReceiver.EXTRA_REMINDER, reminderId)
                putExtra(NotificationActionReceiver.EXTRA_MINUTES, snoozeMinutes)
            })
            .addAction(0, "Amanhã", action(NotificationActionReceiver.ACTION_TOMORROW, id) { putExtra(NotificationActionReceiver.EXTRA_TASK, taskId) })
        post(id, builder)
    }

    fun showChecklist(templateId: String, occurrenceId: String, title: String, text: String) {
        val id = notificationId("occ", occurrenceId)
        val builder = NotificationCompat.Builder(context, CH_ROUTINES)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
            .setContentIntent(openRoute("checklist/run/$templateId", id))
        post(id, builder)
    }

    fun showSummary(title: String, lines: List<String>) {
        val id = notificationId("summary", "daily")
        val style = NotificationCompat.InboxStyle().also { s -> lines.forEach { s.addLine(it) } }
        val builder = NotificationCompat.Builder(context, CH_SUMMARY)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(lines.firstOrNull().orEmpty())
            .setStyle(style)
            .setAutoCancel(true)
            .setContentIntent(openRoute("tasks/today", id))
        post(id, builder)
    }

    fun cancel(id: Int) = manager.cancel(id)

    private fun post(id: Int, builder: NotificationCompat.Builder) {
        if (!canPost()) return
        try {
            manager.notify(id, builder.build())
        } catch (e: SecurityException) {
            // Permissão revogada entre a verificação e o envio: nada a exibir.
            android.util.Log.w("OrganizadorNotif", "permissão de notificação ausente")
        }
    }

    private fun openRoute(route: String, requestCode: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .setAction(Intent.ACTION_VIEW)
            .setData(Uri.parse("organizador://open/$route"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(context, requestCode, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun action(name: String, notificationId: Int, extras: Intent.() -> Unit): PendingIntent {
        val intent = Intent(context, NotificationActionReceiver::class.java)
            .setAction(name)
            .setData(Uri.parse("organizador://action/$name/$notificationId"))
            .putExtra(NotificationActionReceiver.EXTRA_NOTIFICATION, notificationId)
            .apply(extras)
        return PendingIntent.getBroadcast(context, notificationId, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    companion object {
        const val CH_REMINDERS = "reminders"
        const val CH_ROUTINES = "routines"
        const val CH_SUMMARY = "summary"

        fun notificationId(kind: String, id: String): Int = (kind + id).hashCode()
    }
}
