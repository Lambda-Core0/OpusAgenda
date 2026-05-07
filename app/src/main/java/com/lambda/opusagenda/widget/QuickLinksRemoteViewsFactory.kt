package com.lambda.opusagenda.widget

import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.lambda.opusagenda.R
import com.lambda.opusagenda.ui.QuickLink
import com.lambda.opusagenda.ui.QuickLinksStore
import java.util.Locale

class QuickLinksRemoteViewsFactory(
    private val context: Context
) : RemoteViewsService.RemoteViewsFactory {

    private var links: List<QuickLink> = emptyList()

    override fun onCreate() {}

    override fun onDataSetChanged() {
        links = QuickLinksStore(context).load()
    }

    override fun onDestroy() {}

    override fun getCount(): Int = links.size

    override fun getViewAt(position: Int): RemoteViews {
        val link = links.getOrNull(position)
            ?: return RemoteViews(context.packageName, R.layout.widget_quick_link_item)
        val rv = RemoteViews(context.packageName, R.layout.widget_quick_link_item)
        rv.setTextViewText(R.id.widget_link_row, link.label.uppercase(Locale.getDefault()))

        val openIntent = Intent(context, QuickLinksWidgetActionReceiver::class.java).apply {
            action = QuickLinksWidgetActionReceiver.ACTION_OPEN_LINK
            putExtra(QuickLinksWidgetActionReceiver.EXTRA_URL, link.url)
        }
        rv.setOnClickFillInIntent(R.id.widget_link_row, openIntent)
        return rv
    }

    override fun getLoadingView(): RemoteViews? = null

    override fun getViewTypeCount(): Int = 1

    override fun getItemId(position: Int): Long =
        links.getOrNull(position)?.let { link ->
            (link.label + "|" + link.url).hashCode().toLong()
        } ?: position.toLong()

    override fun hasStableIds(): Boolean = true
}
