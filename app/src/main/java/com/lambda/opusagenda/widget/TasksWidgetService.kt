package com.lambda.opusagenda.widget

import android.content.Intent
import android.widget.RemoteViewsService

class TasksWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        return TasksWidgetRemoteViewsFactory(applicationContext)
    }
}
