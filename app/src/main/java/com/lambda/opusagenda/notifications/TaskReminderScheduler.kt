package com.lambda.opusagenda.notifications

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import com.lambda.opusagenda.R
import com.lambda.opusagenda.data.TaskEntity
import com.lambda.opusagenda.ui.MainActivity
import com.lambda.opusagenda.util.TaskRepeatCalculator
import com.lambda.opusagenda.util.TaskTagUtils

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
        if (reminderAt == null) {
            return
        }

        if (reminderAt <= System.currentTimeMillis()) {
            if (!task.completed && task.persistentReminder) {
                show(task)
            }
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

    fun show(task: TaskEntity) {
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
            task.id,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val accentColor = resolveAccentColor(task)
        val builder = buildBaseBuilder(task, contentIntent, accentColor)
            .setStyle(NotificationCompat.BigTextStyle().bigText(task.text))

        NotificationManagerCompat.from(context).notify(task.id, builder.build())
    }

    private fun buildBaseBuilder(
        task: TaskEntity,
        contentIntent: PendingIntent,
        accentColor: Int
    ): NotificationCompat.Builder {
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.opus_notification)
            .setLargeIcon(loadNotificationLogo())
            .setContentTitle(
                if (task.persistentReminder) {
                    context.getString(R.string.persistent_reminder_label)
                } else {
                    context.getString(R.string.reminder_notification_title)
                }
            )
            .setContentText(task.text)
            .setPriority(if (task.persistentReminder) NotificationCompat.PRIORITY_MAX else NotificationCompat.PRIORITY_HIGH)
            .setOnlyAlertOnce(true)
            .setContentIntent(contentIntent)
            .setColor(accentColor)
            .setColorized(true)
            .setCategory(if (task.persistentReminder) NotificationCompat.CATEGORY_CALL else NotificationCompat.CATEGORY_REMINDER)
            .apply {
                if (task.persistentReminder) {
                    setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                    setFullScreenIntent(contentIntent, true)
                    setAutoCancel(false)
                    setOngoing(true)
                    addAction(
                        android.R.drawable.checkbox_on_background,
                        context.getString(R.string.notification_action_complete),
                        buildActionPendingIntent(task.id, TaskReminderActionReceiver.ACTION_COMPLETE)
                    )
                    addAction(
                        android.R.drawable.ic_lock_idle_alarm,
                        context.getString(R.string.notification_action_snooze),
                        buildActionPendingIntent(task.id, TaskReminderActionReceiver.ACTION_SNOOZE)
                    )
                } else {
                    setAutoCancel(true)
                }
            }
    }

    private fun resolveAccentColor(task: TaskEntity): Int {
        if (task.persistentReminder) {
            return ContextCompat.getColor(context, R.color.terminal_red)
        }

        val tags = TaskTagUtils.parseTags(task.tags)
        if (tags.isNotEmpty()) {
            return TaskTagUtils.colorForTag(context, tags.first())
        }

        return when (task.importance) {
            4 -> ContextCompat.getColor(context, R.color.terminal_red)
            3 -> ContextCompat.getColor(context, R.color.terminal_yellow)
            2 -> ContextCompat.getColor(context, R.color.terminal_green)
            else -> ContextCompat.getColor(context, R.color.terminal_green_dim)
        }
    }

    private fun loadNotificationLogo(): Bitmap? {
        val drawable = AppCompatResources.getDrawable(context, R.drawable.opus_notification) ?: return null
        val width = if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else 96
        val height = if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else 96
        return drawable.toBitmap(width, height, Bitmap.Config.ARGB_8888)
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

    private fun buildActionPendingIntent(taskId: Int, action: String): PendingIntent {
        val intent = Intent(context, TaskReminderActionReceiver::class.java).apply {
            this.action = action
            putExtra(EXTRA_TASK_ID, taskId)
        }
        val requestCode = when (action) {
            TaskReminderActionReceiver.ACTION_COMPLETE -> actionRequestCode(taskId, 1)
            TaskReminderActionReceiver.ACTION_SNOOZE -> actionRequestCode(taskId, 2)
            else -> actionRequestCode(taskId, 0)
        }
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    companion object {
        const val CHANNEL_ID = "task_reminders_v2"
        const val ACTION_TASK_REMINDER = "com.lambda.opusagenda.action.TASK_REMINDER"
        const val ACTION_TASK_REACTIVATION = "com.lambda.opusagenda.action.TASK_REACTIVATION"
        const val EXTRA_TASK_ID = "extra_task_id"
        const val EXTRA_TASK_TEXT = "extra_task_text"

        private fun reminderRequestCode(taskId: Int): Int = taskId * 2

        private fun reactivationRequestCode(taskId: Int): Int = (taskId * 2) + 1

        private fun actionRequestCode(taskId: Int, offset: Int): Int = (taskId * 10) + offset
    }
}
