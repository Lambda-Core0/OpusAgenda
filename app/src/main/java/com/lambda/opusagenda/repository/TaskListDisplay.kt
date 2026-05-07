package com.lambda.opusagenda.repository

import android.content.Context
import com.lambda.opusagenda.data.TaskEntity
import com.lambda.opusagenda.util.TaskHierarchyManager
import com.lambda.opusagenda.viewmodel.TaskFilterMode
import com.lambda.opusagenda.viewmodel.TaskListItem

/**
 * Construye la lista visible de tareas con el mismo criterio que la pantalla principal.
 */
object TaskListDisplay {

    fun buildVisibleTasks(
        context: Context,
        currentTasks: List<TaskEntity>,
        filterMode: TaskFilterMode,
        query: String = ""
    ): List<TaskListItem> {
        return TaskHierarchyManager.buildVisibleItems(
            context = context,
            currentTasks = currentTasks,
            filterMode = filterMode,
            query = query
        )
    }
}
