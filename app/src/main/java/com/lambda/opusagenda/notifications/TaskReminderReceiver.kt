package com.lambda.opusagenda.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.lambda.opusagenda.data.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Receiver que publica la notificacion cuando vence el recordatorio.
 */
class TaskReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TaskReminderScheduler.ACTION_TASK_REMINDER) return
        val taskId = intent.getIntExtra(TaskReminderScheduler.EXTRA_TASK_ID, 0)
        if (taskId <= 0) return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val appContext = context.applicationContext
                val task = AppDatabase.getInstance(appContext).taskDao().getTaskByIdOnce(taskId)
                val scheduler = TaskReminderScheduler(appContext)
                if (task == null || task.isCategory || task.completed) {
                    scheduler.cancel(taskId)
                    return@launch
                }
                scheduler.show(task)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
