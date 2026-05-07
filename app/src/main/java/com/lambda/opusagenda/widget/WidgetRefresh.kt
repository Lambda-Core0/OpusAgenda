package com.lambda.opusagenda.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import com.lambda.opusagenda.R

object WidgetRefresh {

    fun notifyTaskWidgets(context: Context) {
        val ctx = context.applicationContext
        val mgr = AppWidgetManager.getInstance(ctx)
        val ids = mgr.getAppWidgetIds(ComponentName(ctx, TasksWidgetProvider::class.java))
        if (ids.isNotEmpty()) {
            mgr.notifyAppWidgetViewDataChanged(ids, R.id.widget_tasks_list)
        }
    }

    fun notifyQuickLinkWidgets(context: Context) {
        val ctx = context.applicationContext
        val mgr = AppWidgetManager.getInstance(ctx)
        val ids = mgr.getAppWidgetIds(ComponentName(ctx, QuickLinksWidgetProvider::class.java))
        if (ids.isNotEmpty()) {
            mgr.notifyAppWidgetViewDataChanged(ids, R.id.widget_quick_links_list)
        }
    }
}
