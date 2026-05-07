package com.lambda.opusagenda.widget

import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri

class QuickLinksWidgetActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_OPEN_LINK) return
        val raw = intent.getStringExtra(EXTRA_URL) ?: return
        val normalized = normalizeUrl(raw)
        val viewIntent = Intent(Intent.ACTION_VIEW, Uri.parse(normalized)).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        try {
            context.applicationContext.startActivity(viewIntent)
        } catch (_: ActivityNotFoundException) {
        }
    }

    private fun normalizeUrl(rawUrl: String): String {
        return if (rawUrl.startsWith("http://") || rawUrl.startsWith("https://")) {
            rawUrl
        } else {
            "https://$rawUrl"
        }
    }

    companion object {
        const val ACTION_OPEN_LINK = "com.lambda.opusagenda.widget.ACTION_OPEN_LINK"
        const val EXTRA_URL = "url"
    }
}
