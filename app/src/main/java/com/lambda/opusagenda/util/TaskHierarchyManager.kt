package com.lambda.opusagenda.util

import android.content.Context
import com.lambda.opusagenda.R
import com.lambda.opusagenda.data.TaskEntity
import com.lambda.opusagenda.repository.TaskPriorityCalculator
import com.lambda.opusagenda.viewmodel.TaskFilterMode
import com.lambda.opusagenda.viewmodel.TaskListItem

/**
 * Operaciones compartidas para listas jerarquicas de tareas/categorias.
 */
object TaskHierarchyManager {

    enum class DropMode {
        REORDER_BEFORE,
        REORDER_AFTER,
        MERGE_INTO_TASK,
        MOVE_INTO_CATEGORY
    }

    fun buildVisibleItems(
        context: Context,
        currentTasks: List<TaskEntity>,
        filterMode: TaskFilterMode,
        query: String
    ): List<TaskListItem> {
        val normalizedQuery = query.trim().lowercase()
        val now = System.currentTimeMillis()
        val childrenByParent = currentTasks
            .groupBy { it.parentId }
            .mapValues { (_, children) ->
                // Compute a priority score for each child. For categories use the highest priority among descendants.
                val priorityMap = children.associateWith { child ->
                    if (child.isCategory) {
                        val subtree = collectSubtree(currentTasks, child.id)
                        val maxChildPriority = subtree
                            .filterNot(TaskEntity::isCategory)
                            .map { TaskPriorityCalculator.calculate(it, now) }
                            .maxOrNull()
                        maxChildPriority ?: TaskPriorityCalculator.calculate(child, now)
                    } else {
                        TaskPriorityCalculator.calculate(child, now)
                    }
                }
                children.sortedWith(
                    compareByDescending<TaskEntity> { priorityMap[it] ?: Int.MIN_VALUE }
                        .thenBy { it.sortOrder }
                        .thenBy { it.createdAt }
                )
            }

        fun buildBranch(parentId: Int?, depth: Int, forceExpanded: Boolean = false): List<TaskListItem> {
            val branch = mutableListOf<TaskListItem>()
            val children = childrenByParent[parentId].orEmpty()
            for (entity in children) {
                if (entity.isCategory) {
                    val descendants = buildBranch(
                        parentId = entity.id,
                        depth = depth + 1,
                        forceExpanded = forceExpanded || normalizedQuery.isNotBlank()
                    )
                    val categoryMatches = normalizedQuery.isBlank() || matchesQuery(entity, normalizedQuery)
                    val shouldShowCategory = when {
                        normalizedQuery.isNotBlank() -> categoryMatches || descendants.isNotEmpty()
                        filterMode == TaskFilterMode.TODAY -> descendants.isNotEmpty()
                        else -> true
                    }
                    if (!shouldShowCategory) continue

                    val subtree = collectSubtree(currentTasks, entity.id)
                    val highestImportance = subtree
                        .filterNot(TaskEntity::isCategory)
                        .maxOfOrNull(TaskEntity::importance)
                        ?: entity.importance
                    val directChildCount = childrenByParent[entity.id].orEmpty().size
                    branch += TaskListItem(
                        id = entity.id,
                        text = entity.text,
                        completed = false,
                        importance = highestImportance,
                        dueDate = null,
                        dueLabel = context.getString(R.string.category_due_label),
                        metaLine = context.getString(
                            R.string.category_meta_format,
                            directChildCount,
                            highestImportance
                        ),
                        description = entity.description,
                        link = entity.link,
                        tags = TaskTagUtils.parseTags(entity.tags),
                        attachmentUri = entity.attachmentUri,
                        attachmentName = entity.attachmentName,
                        attachmentMimeType = entity.attachmentMimeType,
                        pinned = false,
                        persistentReminder = false,
                        reminderAt = null,
                        repeatAmount = null,
                        repeatUnit = null,
                        createdAt = entity.createdAt,
                        priorityScore = highestImportance,
                        isCategory = true,
                        parentId = entity.parentId,
                        depth = depth,
                        expanded = entity.expanded,
                        childCount = directChildCount,
                        sortOrder = entity.sortOrder
                    )

                    if (entity.expanded || normalizedQuery.isNotBlank() || forceExpanded) {
                        branch += descendants
                    }
                } else {
                    if (!matchesFilter(entity, filterMode, now)) continue
                    if (normalizedQuery.isNotBlank() && !matchesQuery(entity, normalizedQuery)) continue
                    branch += TaskListItem(
                        id = entity.id,
                        text = entity.text,
                        completed = entity.completed,
                        importance = entity.importance,
                        dueDate = entity.dueDate,
                        dueLabel = TaskDateFormatter.formatForPicker(context, entity.dueDate),
                        metaLine = context.getString(
                            R.string.task_meta_format,
                            entity.importance,
                            TaskDateFormatter.formatForPicker(context, entity.dueDate),
                            TaskPriorityCalculator.calculate(entity, now)
                        ),
                        description = entity.description,
                        link = entity.link,
                        tags = TaskTagUtils.parseTags(entity.tags),
                        attachmentUri = entity.attachmentUri,
                        attachmentName = entity.attachmentName,
                        attachmentMimeType = entity.attachmentMimeType,
                        pinned = entity.pinned,
                        persistentReminder = entity.persistentReminder,
                        reminderAt = entity.reminderAt,
                        repeatAmount = entity.repeatAmount,
                        repeatUnit = entity.repeatUnit,
                        createdAt = entity.createdAt,
                        priorityScore = TaskPriorityCalculator.calculate(entity, now),
                        isCategory = false,
                        parentId = entity.parentId,
                        depth = depth,
                        expanded = false,
                        childCount = 0,
                        sortOrder = entity.sortOrder
                    )
                }
            }
            return branch
        }

        return buildBranch(parentId = null, depth = 0)
    }

    fun collectSubtree(tasks: List<TaskEntity>, rootId: Int): List<TaskEntity> {
        val childrenByParent = tasks.groupBy { it.parentId }
        val collected = mutableListOf<TaskEntity>()

        fun traverse(nodeId: Int) {
            val node = tasks.firstOrNull { it.id == nodeId } ?: return
            collected += node
            childrenByParent[nodeId].orEmpty().forEach { child ->
                traverse(child.id)
            }
        }

        traverse(rootId)
        return collected
    }

    fun wouldCreateCycle(tasks: List<TaskEntity>, movingId: Int, targetCategoryId: Int): Boolean {
        if (movingId == targetCategoryId) return true
        return collectSubtree(tasks, movingId).any { it.id == targetCategoryId }
    }

    fun renumberSiblings(tasks: List<TaskEntity>, parentId: Int?): List<TaskEntity> {
        val siblings = tasks
            .filter { it.parentId == parentId }
            .sortedWith(compareBy<TaskEntity> { it.sortOrder }.thenBy { it.createdAt })

        val updates = mutableListOf<TaskEntity>()
        siblings.forEachIndexed { index, task ->
            val desiredOrder = index.toLong()
            if (task.sortOrder != desiredOrder) {
                updates += task.copy(sortOrder = desiredOrder)
            }
        }
        return updates
    }

    fun normalizeCategoryImportance(tasks: List<TaskEntity>, categoryId: Int): List<TaskEntity> {
        val subtree = collectSubtree(tasks, categoryId)
        val maxImportance = subtree
            .filterNot(TaskEntity::isCategory)
            .maxOfOrNull(TaskEntity::importance)
            ?: return emptyList()

        // Only update the category node's importance to reflect the highest child importance.
        val categoryNode = tasks.firstOrNull { it.id == categoryId && it.isCategory } ?: return emptyList()
        return if (categoryNode.importance != maxImportance) listOf(categoryNode.copy(importance = maxImportance)) else emptyList()
    }

    private fun matchesFilter(task: TaskEntity, filterMode: TaskFilterMode, now: Long): Boolean {
        return filterMode == TaskFilterMode.ALL ||
            (!task.completed && TaskDateFormatter.isTodayBucket(task.dueDate, now))
    }

    private fun matchesQuery(task: TaskEntity, normalizedQuery: String): Boolean {
        return task.text.lowercase().contains(normalizedQuery) ||
            task.description.orEmpty().lowercase().contains(normalizedQuery) ||
            task.link.orEmpty().lowercase().contains(normalizedQuery) ||
            task.attachmentName.orEmpty().lowercase().contains(normalizedQuery) ||
            TaskTagUtils.matchesQuery(task.tags, normalizedQuery)
    }
}
