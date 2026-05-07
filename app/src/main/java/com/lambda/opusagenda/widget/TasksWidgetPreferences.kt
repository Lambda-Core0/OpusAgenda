package com.lambda.opusagenda.widget

import android.content.Context
import com.lambda.opusagenda.viewmodel.TaskFilterMode

internal object TasksWidgetPreferences {

    private const val PREFS = "opus_widget_tasks"
    private const val KEY_FILTER = "filter_mode"

    fun getFilterMode(context: Context): TaskFilterMode {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_FILTER, TaskFilterMode.ALL.name)
        return try {
            TaskFilterMode.valueOf(raw ?: TaskFilterMode.ALL.name)
        } catch (_: IllegalArgumentException) {
            TaskFilterMode.ALL
        }
    }

    fun setFilterMode(context: Context, mode: TaskFilterMode) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_FILTER, mode.name)
            .apply()
    }
}
