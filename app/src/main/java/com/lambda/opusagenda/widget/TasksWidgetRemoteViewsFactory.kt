package com.lambda.opusagenda.widget

import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.os.Binder
import android.util.Log
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.lambda.opusagenda.R
import com.lambda.opusagenda.data.AppDatabase
import com.lambda.opusagenda.repository.TaskListDisplay
import com.lambda.opusagenda.viewmodel.TaskListItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

class TasksWidgetRemoteViewsFactory(
    private val context: Context
) : RemoteViewsService.RemoteViewsFactory {

    private var items: List<TaskListItem> = emptyList()

    override fun onCreate() = reloadItems()

    override fun onDataSetChanged() = reloadItems()

    private fun reloadItems() {
        val identityToken = Binder.clearCallingIdentity()
        items = try {
            runBlocking {
                withContext(Dispatchers.IO) {
                    val dao = AppDatabase.getInstance(context).taskDao()
                    val all = dao.getAllTasksOnce()
                    val mode = TasksWidgetPreferences.getFilterMode(context)
                    TaskListDisplay.buildVisibleTasks(context, all, mode)
                }
            }.also { loadedItems ->
                Log.d("TasksWidgetFactory", "Loaded ${loadedItems.size} tasks for widget")
            }
        } catch (exception: Exception) {
            Log.e("TasksWidgetFactory", "Failed to load widget tasks", exception)
            emptyList()
        } finally {
            Binder.restoreCallingIdentity(identityToken)
        }
    }

    override fun onDestroy() {}

    override fun getCount(): Int = items.size

    override fun getViewAt(position: Int): RemoteViews {
        val item = items.getOrNull(position)
            ?: return RemoteViews(context.packageName, R.layout.widget_task_item)
        return try {
            val rv = RemoteViews(context.packageName, R.layout.widget_task_item)
            val density = context.resources.displayMetrics.density
            val basePaddingPx = (10 * density).toInt()
            val indentPx = (item.depth * 18 * density).toInt()
            val statusMarginPx = (34 * density).toInt()
            rv.setViewPadding(R.id.widget_task_row, basePaddingPx + indentPx, basePaddingPx, basePaddingPx, basePaddingPx)
            rv.setViewPadding(R.id.widget_task_meta, statusMarginPx + indentPx, 0, 0, 0)

            rv.setTextViewText(
                R.id.widget_task_status,
                when {
                    item.isCategory && item.expanded -> "[-]"
                    item.isCategory -> "[+]"
                    item.completed -> "[x]"
                    else -> "[ ]"
                }
            )
            rv.setTextViewText(R.id.widget_task_text, item.text)
            rv.setTextViewText(R.id.widget_task_meta, item.metaLine)
            val paintFlags = if (!item.isCategory && item.completed) {
                Paint.STRIKE_THRU_TEXT_FLAG or Paint.ANTI_ALIAS_FLAG
            } else {
                Paint.ANTI_ALIAS_FLAG
            }
            rv.setInt(R.id.widget_task_text, "setPaintFlags", paintFlags)

            val toggleIntent = Intent(context, TasksWidgetActionReceiver::class.java).apply {
                action = if (item.isCategory) {
                    TasksWidgetActionReceiver.ACTION_TOGGLE_CATEGORY
                } else {
                    TasksWidgetActionReceiver.ACTION_TOGGLE_TASK
                }
                putExtra(TasksWidgetActionReceiver.EXTRA_TASK_ID, item.id)
            }
            rv.setOnClickFillInIntent(R.id.widget_task_row, toggleIntent)
            rv
        } catch (exception: Exception) {
            Log.e("TasksWidgetFactory", "Failed to bind task row at position $position", exception)
            RemoteViews(context.packageName, R.layout.widget_task_item).apply {
                setTextViewText(R.id.widget_task_status, "[!]")
                setTextViewText(R.id.widget_task_text, item.text)
                setTextViewText(R.id.widget_task_meta, context.getString(R.string.task_failed))
            }
        }
    }

    override fun getLoadingView(): RemoteViews? = null

    override fun getViewTypeCount(): Int = 1

    override fun getItemId(position: Int): Long =
        items.getOrNull(position)?.id?.toLong() ?: position.toLong()

    override fun hasStableIds(): Boolean = true
}