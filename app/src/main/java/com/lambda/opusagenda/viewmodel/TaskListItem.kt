package com.lambda.opusagenda.viewmodel

/**
 * Modelo de presentacion listo para pintar en la lista.
 *
 * Deriva de la entidad persistida, pero agrega datos ya calculados para UI
 * como la fecha humana y el puntaje de prioridad.
 */
data class TaskListItem(
    val id: Int,
    val text: String,
    val completed: Boolean,
    val importance: Int,
    val dueDate: Long?,
    val dueLabel: String,
    val metaLine: String,
    val description: String?,
    val link: String?,
    val tags: List<String>,
    val attachmentUri: String?,
    val attachmentName: String?,
    val attachmentMimeType: String?,
    val pinned: Boolean,
    val persistentReminder: Boolean,
    val reminderAt: Long?,
    val repeatAmount: Int?,
    val repeatUnit: String?,
    val createdAt: Long,
    val priorityScore: Int,
    val isCategory: Boolean,
    val parentId: Int?,
    val depth: Int,
    val expanded: Boolean,
    val childCount: Int,
    val sortOrder: Long
)
