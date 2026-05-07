package com.lambda.opusagenda.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import com.lambda.opusagenda.R
import com.lambda.opusagenda.ui.MainActivity
import com.lambda.opusagenda.viewmodel.TaskFilterMode

class TasksWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (id in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, id)
        }
        if (appWidgetIds.isNotEmpty()) {
            appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetIds, R.id.widget_tasks_list)
        }
    }

    companion object {

        fun refreshAll(context: Context) {
            val mgr = AppWidgetManager.getInstance(context)
            val ids = mgr.getAppWidgetIds(ComponentName(context, TasksWidgetProvider::class.java))
            for (id in ids) {
                updateAppWidget(context, mgr, id)
            }
            if (ids.isNotEmpty()) {
                mgr.notifyAppWidgetViewDataChanged(ids, R.id.widget_tasks_list)
            }
        }

        @Suppress("DEPRECATION")
        fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            val views = RemoteViews(context.packageName, R.layout.widget_tasks)
            val mode = TasksWidgetPreferences.getFilterMode(context)

            val allColor = ContextCompat.getColor(
                context,
                if (mode == TaskFilterMode.ALL) R.color.terminal_selected else R.color.terminal_black
            )
            val todayColor = ContextCompat.getColor(
                context,
                if (mode == TaskFilterMode.TODAY) R.color.terminal_selected else R.color.terminal_black
            )
            views.setTextColor(R.id.widget_btn_all, allColor)
            views.setTextColor(R.id.widget_btn_today, todayColor)

            val allIntent = Intent(context, TasksWidgetActionReceiver::class.java).apply {
                action = TasksWidgetActionReceiver.ACTION_SET_FILTER
                putExtra(TasksWidgetActionReceiver.EXTRA_FILTER, TaskFilterMode.ALL.name)
                data = Uri.parse("opusagenda://widget/tasks/$appWidgetId/filter/all")
            }
            views.setOnClickPendingIntent(
                R.id.widget_btn_all,
                PendingIntent.getBroadcast(
                    context,
                    210 + appWidgetId,
                    allIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )

            val todayIntent = Intent(context, TasksWidgetActionReceiver::class.java).apply {
                action = TasksWidgetActionReceiver.ACTION_SET_FILTER
                putExtra(TasksWidgetActionReceiver.EXTRA_FILTER, TaskFilterMode.TODAY.name)
                data = Uri.parse("opusagenda://widget/tasks/$appWidgetId/filter/today")
            }
            views.setOnClickPendingIntent(
                R.id.widget_btn_today,
                PendingIntent.getBroadcast(
                    context,
                    211 + appWidgetId,
                    todayIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )

            val openApp = PendingIntent.getActivity(
                context,
                212,
                Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_tasks_title, openApp)

            val svcIntent = Intent(context, TasksWidgetService::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                data = Uri.parse(toUri(Intent.URI_INTENT_SCHEME) + "#tasklist$appWidgetId")
            }
            views.setRemoteAdapter(R.id.widget_tasks_list, svcIntent)
            views.setEmptyView(R.id.widget_tasks_list, R.id.widget_tasks_empty)
            val toggleTemplateIntent = Intent(context, TasksWidgetActionReceiver::class.java)
            views.setPendingIntentTemplate(
                R.id.widget_tasks_list,
                PendingIntent.getBroadcast(
                    context,
                    213 + appWidgetId,
                    toggleTemplateIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
                )
            )

            val emptyRes = if (mode == TaskFilterMode.TODAY) {
                R.string.empty_state_today
            } else {
                R.string.empty_state_all
            }
            views.setTextViewText(R.id.widget_tasks_empty, context.getString(emptyRes))

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}