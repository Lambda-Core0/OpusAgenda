package com.lambda.opusagenda.widget

import android.content.Intent
import android.widget.RemoteViewsService

class QuickLinksWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        return QuickLinksRemoteViewsFactory(applicationContext)
    }
}
