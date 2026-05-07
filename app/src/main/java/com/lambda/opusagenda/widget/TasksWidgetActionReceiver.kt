package com.lambda.opusagenda.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.lambda.opusagenda.data.AppDatabase
import com.lambda.opusagenda.notifications.TaskReminderScheduler
import com.lambda.opusagenda.util.TaskRepeatCalculator
import com.lambda.opusagenda.viewmodel.TaskFilterMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

class TasksWidgetActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val appContext = context.applicationContext
        when (intent.action) {
            ACTION_SET_FILTER -> {
                val name = intent.getStringExtra(EXTRA_FILTER) ?: return
                val mode = try {
                    TaskFilterMode.valueOf(name)
                } catch (_: IllegalArgumentException) {
                    TaskFilterMode.ALL
                }
                TasksWidgetPreferences.setFilterMode(appContext, mode)
                TasksWidgetProvider.refreshAll(appContext)
            }
            ACTION_TOGGLE_TASK -> {
                val taskId = intent.getIntExtra(EXTRA_TASK_ID, -1)
                if (taskId < 0) return
                runBlocking(Dispatchers.IO) {
                    val dao = AppDatabase.getInstance(appContext).taskDao()
                    val task = dao.getTaskByIdOnce(taskId) ?: return@runBlocking
                    if (task.isCategory) return@runBlocking
                    val updated = TaskRepeatCalculator.applyCompletion(task, !task.completed)
                    dao.update(updated)
                    TaskReminderScheduler(appContext).sync(updated)
                }
                WidgetRefresh.notifyTaskWidgets(appContext)
            }
            ACTION_TOGGLE_CATEGORY -> {
                val taskId = intent.getIntExtra(EXTRA_TASK_ID, -1)
                if (taskId < 0) return
                runBlocking(Dispatchers.IO) {
                    val dao = AppDatabase.getInstance(appContext).taskDao()
                    val task = dao.getTaskByIdOnce(taskId) ?: return@runBlocking
                    if (!task.isCategory) return@runBlocking
                    dao.update(task.copy(expanded = !task.expanded))
                }
                WidgetRefresh.notifyTaskWidgets(appContext)
            }
        }
    }

    companion object {
        const val ACTION_SET_FILTER = "com.lambda.opusagenda.widget.ACTION_SET_FILTER"
        const val ACTION_TOGGLE_TASK = "com.lambda.opusagenda.widget.ACTION_TOGGLE_TASK"
        const val ACTION_TOGGLE_CATEGORY = "com.lambda.opusagenda.widget.ACTION_TOGGLE_CATEGORY"
        const val EXTRA_FILTER = "filter"
        const val EXTRA_TASK_ID = "task_id"
    }
}
