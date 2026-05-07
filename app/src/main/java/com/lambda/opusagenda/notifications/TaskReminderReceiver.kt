package com.lambda.opusagenda.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Receiver que publica la notificacion cuando vence el recordatorio.
 */
class TaskReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TaskReminderScheduler.ACTION_TASK_REMINDER) return
        val taskId = intent.getIntExtra(TaskReminderScheduler.EXTRA_TASK_ID, 0)
        val taskText = intent.getStringExtra(TaskReminderScheduler.EXTRA_TASK_TEXT).orEmpty()
        TaskReminderScheduler(context).show(taskId, taskText)
    }
}
