package com.lambda.opusagenda.notifications

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.lambda.opusagenda.R
import com.lambda.opusagenda.data.TaskEntity
import com.lambda.opusagenda.ui.MainActivity
import com.lambda.opusagenda.util.TaskRepeatCalculator

/**
 * Programa, cancela y publica recordatorios locales de tareas.
 */
class TaskReminderScheduler(
    private val context: Context
) {

    fun sync(task: TaskEntity) {
        cancelReminder(task.id)
        cancelReactivation(task.id)

        if (task.isCategory) {
            return
        }

        val reminderAt = task.reminderAt
        if (reminderAt == null || reminderAt <= System.currentTimeMillis()) {
            return
        }

        if (task.completed && TaskRepeatCalculator.hasRepeat(task)) {
            scheduleExactAlarm(
                triggerAtMillis = reminderAt,
                pendingIntent = buildReactivationPendingIntent(task.id)
            )
            return
        }

        if (task.completed) {
            return
        }

        createNotificationChannel()
        scheduleExactAlarm(
            triggerAtMillis = reminderAt,
            pendingIntent = buildReminderPendingIntent(task.id, task.text)
        )
    }

    fun cancel(taskId: Int) {
        cancelReminder(taskId)
        cancelReactivation(taskId)
        NotificationManagerCompat.from(context).cancel(taskId)
    }

    fun show(taskId: Int, taskText: String) {
        createNotificationChannel()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentIntent = PendingIntent.getActivity(
            context,
            taskId,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.opus_notification)
            .setContentTitle(context.getString(R.string.reminder_notification_title))
            .setContentText(taskText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(taskText))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .build()

        NotificationManagerCompat.from(context).notify(taskId, notification)
    }

    fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.reminder_channel_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.reminder_channel_description)
        }
        manager.createNotificationChannel(channel)
    }

    private fun scheduleExactAlarm(triggerAtMillis: Long, pendingIntent: PendingIntent) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        alarmManager.cancel(pendingIntent)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
        } else {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
        }
    }

    private fun cancelReminder(taskId: Int) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        val pendingIntent = buildReminderPendingIntent(taskId, "")
        alarmManager.cancel(pendingIntent)
        pendingIntent.cancel()
    }

    private fun cancelReactivation(taskId: Int) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        val pendingIntent = buildReactivationPendingIntent(taskId)
        alarmManager.cancel(pendingIntent)
        pendingIntent.cancel()
    }

    private fun buildReminderPendingIntent(taskId: Int, taskText: String): PendingIntent {
        val intent = Intent(context, TaskReminderReceiver::class.java).apply {
            action = ACTION_TASK_REMINDER
            putExtra(EXTRA_TASK_ID, taskId)
            putExtra(EXTRA_TASK_TEXT, taskText)
        }
        return PendingIntent.getBroadcast(
            context,
            reminderRequestCode(taskId),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun buildReactivationPendingIntent(taskId: Int): PendingIntent {
        val intent = Intent(context, TaskRepeatReceiver::class.java).apply {
            action = ACTION_TASK_REACTIVATION
            putExtra(EXTRA_TASK_ID, taskId)
        }
        return PendingIntent.getBroadcast(
            context,
            reactivationRequestCode(taskId),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    companion object {
        const val CHANNEL_ID = "task_reminders"
        const val ACTION_TASK_REMINDER = "com.lambda.opusagenda.action.TASK_REMINDER"
        const val ACTION_TASK_REACTIVATION = "com.lambda.opusagenda.action.TASK_REACTIVATION"
        const val EXTRA_TASK_ID = "extra_task_id"
        const val EXTRA_TASK_TEXT = "extra_task_text"

        private fun reminderRequestCode(taskId: Int): Int = taskId * 2

        private fun reactivationRequestCode(taskId: Int): Int = (taskId * 2) + 1
    }
}
