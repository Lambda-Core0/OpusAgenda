package com.lambda.opusagenda.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.lambda.opusagenda.data.AppDatabase
import com.lambda.opusagenda.util.TaskRepeatCalculator
import com.lambda.opusagenda.widget.WidgetRefresh
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Maneja las acciones directas de las notificaciones persistentes.
 */
class TaskReminderActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_COMPLETE -> handleComplete(context, intent)
            ACTION_SNOOZE -> handleSnooze(context, intent)
        }
    }

    private fun handleComplete(context: Context, intent: Intent) {
        val taskId = intent.getIntExtra(TaskReminderScheduler.EXTRA_TASK_ID, -1)
        if (taskId < 0) return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val appContext = context.applicationContext
                val dao = AppDatabase.getInstance(appContext).taskDao()
                val task = dao.getTaskByIdOnce(taskId)
                val scheduler = TaskReminderScheduler(appContext)
                if (task == null || task.isCategory || task.completed) {
                    scheduler.cancel(taskId)
                    return@launch
                }

                val updated = TaskRepeatCalculator.applyCompletion(task, true)
                dao.update(updated)
                scheduler.cancel(taskId)
                scheduler.sync(updated)
                WidgetRefresh.notifyTaskWidgets(appContext)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun handleSnooze(context: Context, intent: Intent) {
        val taskId = intent.getIntExtra(TaskReminderScheduler.EXTRA_TASK_ID, -1)
        if (taskId < 0) return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val appContext = context.applicationContext
                val dao = AppDatabase.getInstance(appContext).taskDao()
                val task = dao.getTaskByIdOnce(taskId)
                val scheduler = TaskReminderScheduler(appContext)
                if (task == null || task.isCategory || task.completed) {
                    scheduler.cancel(taskId)
                    return@launch
                }

                val snoozed = task.copy(
                    completed = false,
                    reminderAt = System.currentTimeMillis() + SNOOZE_MILLIS
                )
                dao.update(snoozed)
                scheduler.cancel(taskId)
                scheduler.sync(snoozed)
                WidgetRefresh.notifyTaskWidgets(appContext)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_COMPLETE = "com.lambda.opusagenda.notifications.action.COMPLETE"
        const val ACTION_SNOOZE = "com.lambda.opusagenda.notifications.action.SNOOZE"
        private const val SNOOZE_MINUTES = 15L
        private const val SNOOZE_MILLIS = SNOOZE_MINUTES * 60_000L
    }
}
