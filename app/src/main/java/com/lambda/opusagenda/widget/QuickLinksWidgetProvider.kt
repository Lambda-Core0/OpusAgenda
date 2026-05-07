package com.lambda.opusagenda.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.RemoteViews
import com.lambda.opusagenda.R
import com.lambda.opusagenda.ui.MainActivity

class QuickLinksWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
        if (appWidgetIds.isNotEmpty()) {
            appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetIds, R.id.widget_quick_links_list)
        }
    }

    companion object {
        @Suppress("DEPRECATION")
        fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            val views = RemoteViews(context.packageName, R.layout.widget_quick_links)

            val openApp = PendingIntent.getActivity(
                context,
                310 + appWidgetId,
                Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_quick_links_title, openApp)

            val svcIntent = Intent(context, QuickLinksWidgetService::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                data = Uri.parse(toUri(Intent.URI_INTENT_SCHEME) + "#quicklinks$appWidgetId")
            }
            views.setRemoteAdapter(R.id.widget_quick_links_list, svcIntent)
            views.setEmptyView(R.id.widget_quick_links_list, R.id.widget_quick_links_empty)
            val openLinkTemplate = Intent(context, QuickLinksWidgetActionReceiver::class.java).apply {
                action = QuickLinksWidgetActionReceiver.ACTION_OPEN_LINK
            }
            views.setPendingIntentTemplate(
                R.id.widget_quick_links_list,
                PendingIntent.getBroadcast(
                    context,
                    311 + appWidgetId,
                    openLinkTemplate,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
                )
            )

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}
