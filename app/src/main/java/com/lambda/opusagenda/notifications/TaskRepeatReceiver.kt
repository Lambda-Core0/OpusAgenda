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
 * Reactiva tareas recurrentes cuando llega su siguiente ocurrencia.
 */
class TaskRepeatReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TaskReminderScheduler.ACTION_TASK_REACTIVATION) return
        val taskId = intent.getIntExtra(TaskReminderScheduler.EXTRA_TASK_ID, -1)
        if (taskId < 0) return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val appContext = context.applicationContext
                val dao = AppDatabase.getInstance(appContext).taskDao()
                val task = dao.getTaskByIdOnce(taskId) ?: return@launch
                if (!task.completed || !TaskRepeatCalculator.hasRepeat(task)) return@launch

                val updated = task.copy(completed = false)
                dao.update(updated)

                val scheduler = TaskReminderScheduler(appContext)
                scheduler.show(updated)
                scheduler.sync(updated)
                WidgetRefresh.notifyTaskWidgets(appContext)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
