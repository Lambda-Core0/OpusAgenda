package com.lambda.opusagenda.viewmodel

import android.content.Context
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.lambda.opusagenda.R
import com.lambda.opusagenda.data.TaskEntity
import com.lambda.opusagenda.notifications.TaskReminderScheduler
import com.lambda.opusagenda.repository.TaskListDisplay
import com.lambda.opusagenda.repository.TaskRepository
import com.lambda.opusagenda.util.Event
import com.lambda.opusagenda.util.TaskDateFormatter
import com.lambda.opusagenda.util.TaskHierarchyManager
import com.lambda.opusagenda.util.TaskHierarchyManager.DropMode
import com.lambda.opusagenda.util.TaskRepeatCalculator
import com.lambda.opusagenda.widget.WidgetRefresh
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * ViewModel principal de la pantalla.
 *
 * Esta clase transforma datos crudos de Room en estado listo para la UI:
 * - lista visible y ordenada
 * - resumen superior
 * - mensajes temporales
 * - operaciones de escritura
 *
 * No depende de widgets concretos ni conoce detalles de layout.
 */
class MainViewModel(
    private val repository: TaskRepository,
    private val appContext: Context
) : ViewModel() {

    private val reminderScheduler = TaskReminderScheduler(appContext)

    /** Fuente observable primaria proveniente del repositorio/Room. */
    private val tasksSource = repository.tasks

    /** Texto de busqueda ingresado por el usuario. */
    private val query = MutableLiveData("")

    /** Filtro seleccionado entre lista completa y vista "Today". */
    private val filterMode = MutableLiveData(TaskFilterMode.ALL)

    /** Mensajes efimeros que la Activity consumira una sola vez. */
    private val statusEvent = MutableLiveData<Event<String>>()

    /**
     * Copia en memoria de las entidades crudas.
     *
     * Se usa para recomputar rapidamente listas derivadas cuando cambian
     * busqueda o filtro sin pedir otra consulta a Room.
     */
    private val currentTasks = mutableListOf<TaskEntity>()

    /**
     * Lista final visible para la UI.
     *
     * Se recalcula cada vez que cambia:
     * - el contenido real de la base
     * - la busqueda
     * - el filtro activo
     */
    private val visibleTasksMediator = MediatorLiveData<List<TaskListItem>>().apply {
        addSource(tasksSource) { tasks ->
            currentTasks.clear()
            currentTasks.addAll(tasks)
            value = buildVisibleTasks()
        }
        addSource(query) {
            value = buildVisibleTasks()
        }
        addSource(filterMode) {
            value = buildVisibleTasks()
        }
    }

    /** Lista lista para consumir desde el RecyclerView. */
    val visibleTasks: LiveData<List<TaskListItem>> = visibleTasksMediator

    /** Filtro actual para que la UI pueda reflejarlo visualmente. */
    val mode: LiveData<TaskFilterMode> = filterMode

    /** Query observable por si otra capa quisiera espejarla mas adelante. */
    val searchQuery: LiveData<String> = query

    /** Canal de mensajes temporales de un solo uso. */
    val statusMessage: LiveData<Event<String>> = statusEvent

    /**
     * Resumen superior con contadores basicos.
     *
     * Se alimenta directamente de la fuente de Room porque resume el conjunto
     * total real y no la lista filtrada visible.
     */
    val summary: LiveData<String> = MediatorLiveData<String>().apply {
        fun update(tasks: List<TaskEntity>) {
            val plainTasks = tasks.filterNot(TaskEntity::isCategory)
            val allOpen = plainTasks.count { !it.completed }
            val todayCount = tasks.count {
                !it.isCategory && !it.completed && TaskDateFormatter.isTodayBucket(it.dueDate)
            }
            value = appContext.getString(
                R.string.summary_format,
                allOpen,
                todayCount,
                plainTasks.size
            )
        }

        addSource(tasksSource) { tasks -> update(tasks) }
    }

    /** Actualiza el texto usado para filtrar la lista visible. */
    fun setSearchQuery(newQuery: String) {
        query.value = newQuery
    }

    /** Cambia entre la vista completa y la vista resumida "Today". */
    fun setFilterMode(newMode: TaskFilterMode) {
        filterMode.value = newMode
    }

    /** Inserta una nueva tarea a partir de datos crudos que vienen de la UI. */
    fun addTask(
        text: String,
        importance: Int,
        dueDate: Long?,
        link: String?,
        pinned: Boolean,
        reminderAt: Long?,
        repeatAmount: Int?,
        repeatUnit: String?,
        parentId: Int? = null,
        isCategory: Boolean = false
    ) {
        val cleanText = text.trim()
        if (cleanText.isBlank()) return

        launchDatabaseAction(successMessage = appContext.getString(R.string.task_saved)) {
            val entity = TaskEntity(
                text = cleanText,
                isCategory = isCategory,
                importance = importance.coerceIn(1, 4),
                parentId = parentId,
                sortOrder = nextSortOrder(parentId = parentId),
                expanded = if (isCategory) true else true,
                dueDate = if (isCategory) null else dueDate,
                link = if (isCategory) null else link?.trim()?.ifBlank { null },
                pinned = if (isCategory) false else pinned,
                reminderAt = if (isCategory) null else reminderAt,
                repeatAmount = if (isCategory) null else repeatAmount,
                repeatUnit = if (isCategory) null else repeatUnit,
                createdAt = System.currentTimeMillis()
            )
            val insertedId = repository.insert(entity).toInt()
            reminderScheduler.sync(entity.copy(id = insertedId))
        }
    }

    /** Actualiza una tarea existente manteniendo su identidad y fecha de creacion. */
    fun updateTask(
        id: Int,
        text: String,
        importance: Int,
        dueDate: Long?,
        link: String?,
        pinned: Boolean,
        completed: Boolean,
        reminderAt: Long?,
        repeatAmount: Int?,
        repeatUnit: String?,
        createdAt: Long,
        isCategory: Boolean,
        parentId: Int?,
        sortOrder: Long,
        expanded: Boolean
    ) {
        val cleanText = text.trim()
        if (cleanText.isBlank()) return

        launchDatabaseAction(successMessage = appContext.getString(R.string.task_saved)) {
            val entity = TaskEntity(
                id = id,
                text = cleanText,
                isCategory = isCategory,
                completed = if (isCategory) false else completed,
                importance = importance.coerceIn(1, 4),
                parentId = parentId,
                sortOrder = sortOrder,
                expanded = expanded,
                dueDate = if (isCategory) null else dueDate,
                link = if (isCategory) null else link?.trim()?.ifBlank { null },
                pinned = if (isCategory) false else pinned,
                reminderAt = if (isCategory) null else reminderAt,
                repeatAmount = if (isCategory) null else repeatAmount,
                repeatUnit = if (isCategory) null else repeatUnit,
                createdAt = createdAt
            )
            repository.update(entity)
            reminderScheduler.sync(entity)
        }
    }

    /** Marca o desmarca una tarea como completada. */
    fun toggleCompleted(item: TaskListItem, completed: Boolean) {
        if (item.isCategory) return
        launchDatabaseAction(successMessage = appContext.getString(R.string.task_completed)) {
            val updated = TaskRepeatCalculator.applyCompletion(item.toEntity(), completed)
            repository.update(updated)
            reminderScheduler.sync(updated)
        }
    }

    /** Invierte el estado de fijado de una tarea visible. */
    fun togglePinned(item: TaskListItem) {
        if (item.isCategory) return
        launchDatabaseAction {
            repository.update(item.toEntity().copy(pinned = !item.pinned))
        }
    }

    /** Elimina una tarea por completo. */
    fun deleteTask(item: TaskListItem) {
        launchDatabaseAction(successMessage = appContext.getString(R.string.task_deleted)) {
            if (item.isCategory) {
                val ids = TaskHierarchyManager.collectSubtree(currentTasks, item.id).map(TaskEntity::id)
                currentTasks
                    .filter { it.id in ids && !it.isCategory }
                    .forEach { reminderScheduler.cancel(it.id) }
                repository.deleteByIds(ids)
            } else {
                reminderScheduler.cancel(item.id)
                repository.delete(item.toEntity())
            }
        }
    }

    /** Expande o colapsa una categoria visible. */
    fun toggleExpanded(item: TaskListItem) {
        if (!item.isCategory) return
        launchDatabaseAction {
            repository.update(item.toEntity().copy(expanded = !item.expanded))
        }
    }

    /** Reordena o agrupa elementos de la lista jerarquica. */
    fun moveItem(draggedId: Int, targetId: Int, mode: DropMode) {
        launchDatabaseAction {
            val snapshot = currentTasks.toMutableList()
            val dragged = snapshot.firstOrNull { it.id == draggedId } ?: return@launchDatabaseAction
            val target = snapshot.firstOrNull { it.id == targetId } ?: return@launchDatabaseAction
            if (dragged.id == target.id) return@launchDatabaseAction

            when (mode) {
                DropMode.REORDER_BEFORE -> {
                    applyReorder(snapshot, dragged, target, placeAfter = false)
                }
                DropMode.REORDER_AFTER -> {
                    applyReorder(snapshot, dragged, target, placeAfter = true)
                }
                DropMode.MOVE_INTO_CATEGORY -> {
                    if (!target.isCategory || TaskHierarchyManager.wouldCreateCycle(snapshot, dragged.id, target.id)) {
                        statusEvent.postValue(Event(appContext.getString(R.string.category_move_invalid)))
                        return@launchDatabaseAction
                    }
                    applyMoveIntoCategory(snapshot, dragged, target)
                }
                DropMode.MERGE_INTO_TASK -> {
                    if (target.isCategory) {
                        applyMoveIntoCategory(snapshot, dragged, target)
                    } else {
                        applyMergeIntoCategory(snapshot, dragged, target)
                    }
                }
            }
        }
    }

    /**
     * Construye la lista visible final a partir de la copia en memoria.
     *
     * Pipeline:
     * 1. filtro por modo
     * 2. filtro por busqueda
     * 3. mapeo a modelo de UI
     * 4. orden dinamico por prioridad
     */
    private fun buildVisibleTasks(): List<TaskListItem> {
        val currentQuery = query.value.orEmpty()
        val currentMode = filterMode.value ?: TaskFilterMode.ALL
        return TaskListDisplay.buildVisibleTasks(
            appContext,
            currentTasks,
            currentMode,
            currentQuery
        )
    }

    /** Convierte un item visible nuevamente en entidad persistible. */
    private fun TaskListItem.toEntity(): TaskEntity {
        return TaskEntity(
            id = id,
            text = text,
            isCategory = isCategory,
            completed = completed,
            importance = importance,
            parentId = parentId,
            sortOrder = sortOrder,
            expanded = expanded,
            dueDate = dueDate,
            link = link,
            pinned = pinned,
            reminderAt = reminderAt,
            repeatAmount = repeatAmount,
            repeatUnit = repeatUnit,
            createdAt = createdAt
        )
    }

    private suspend fun applyReorder(
        snapshot: MutableList<TaskEntity>,
        dragged: TaskEntity,
        target: TaskEntity,
        placeAfter: Boolean
    ) {
        if (dragged.isCategory && target.parentId != null &&
            TaskHierarchyManager.wouldCreateCycle(snapshot, dragged.id, target.parentId!!)
        ) {
            statusEvent.postValue(Event(appContext.getString(R.string.category_move_invalid)))
            return
        }

        val originalParentId = dragged.parentId
        replaceInList(snapshot, dragged.copy(parentId = target.parentId))

        val reorderedIds = snapshot
            .filter { it.parentId == target.parentId && it.id != dragged.id }
            .sortedWith(compareBy<TaskEntity> { it.sortOrder }.thenBy { it.createdAt })
            .map(TaskEntity::id)
            .toMutableList()

        val targetIndex = reorderedIds.indexOf(target.id)
        if (targetIndex < 0) return
        val insertIndex = if (placeAfter) targetIndex + 1 else targetIndex
        reorderedIds.add(insertIndex.coerceIn(0, reorderedIds.size), dragged.id)

        val updates = mutableListOf<TaskEntity>()
        reorderedIds.forEachIndexed { index, id ->
            val node = snapshot.first { it.id == id }
            val desiredParent = target.parentId
            if (node.sortOrder != index.toLong() || node.parentId != desiredParent) {
                val updated = node.copy(parentId = desiredParent, sortOrder = index.toLong())
                replaceInList(snapshot, updated)
                updates += updated
            }
        }

        updates += TaskHierarchyManager.renumberSiblings(snapshot, originalParentId)
        updates += TaskHierarchyManager.renumberSiblings(snapshot, target.parentId)
        updates += normalizeAffectedCategories(snapshot, originalParentId, target.parentId)
        repository.updateAll(updates.distinctBy(TaskEntity::id))
    }

    private suspend fun applyMoveIntoCategory(
        snapshot: MutableList<TaskEntity>,
        dragged: TaskEntity,
        category: TaskEntity
    ) {
        val originalParentId = dragged.parentId
        val nextOrder = snapshot
            .filter { it.parentId == category.id }
            .maxOfOrNull(TaskEntity::sortOrder)
            ?.plus(1)
            ?: 0L

        val moved = dragged.copy(parentId = category.id, sortOrder = nextOrder)
        replaceInList(snapshot, moved)

        val updates = mutableListOf<TaskEntity>(moved)
        updates += TaskHierarchyManager.renumberSiblings(snapshot, originalParentId)
        updates += TaskHierarchyManager.normalizeCategoryImportance(snapshot, category.id)
        originalParentId?.let { updates += TaskHierarchyManager.normalizeCategoryImportance(snapshot, it) }
        repository.updateAll(updates.distinctBy(TaskEntity::id))
    }

    private suspend fun applyMergeIntoCategory(
        snapshot: MutableList<TaskEntity>,
        dragged: TaskEntity,
        target: TaskEntity
    ) {

        if (dragged.parentId != target.parentId) {
            applyReorder(snapshot, dragged, target, placeAfter = true)
            val newDragged = snapshot.firstOrNull { it.id == dragged.id } ?: return
            val newTarget = snapshot.firstOrNull { it.id == target.id } ?: return
            applyMergeIntoCategory(snapshot, newDragged, newTarget)
            return
        }

        val siblingParentId = target.parentId
        val siblingOrder = snapshot
            .filter { it.parentId == siblingParentId }
            .sortedWith(compareBy<TaskEntity> { it.sortOrder }.thenBy { it.createdAt })

        val firstIndex = siblingOrder.indexOfFirst { it.id == target.id }
        val secondIndex = siblingOrder.indexOfFirst { it.id == dragged.id }
        if (firstIndex < 0 || secondIndex < 0) return

        val categoryImportance = maxOf(target.importance, dragged.importance)
        val category = TaskEntity(
            text = appContext.getString(R.string.default_category_name),
            isCategory = true,
            importance = categoryImportance,
            parentId = siblingParentId,
            sortOrder = minOf(firstIndex, secondIndex).toLong(),
            expanded = true,
            dueDate = null,
            link = null,
            pinned = false,
            reminderAt = null,
            repeatAmount = null,
            repeatUnit = null,
            createdAt = System.currentTimeMillis()
        )
        val categoryId = repository.insert(category).toInt()

        val newChildrenOrder = listOf(target, dragged)
            .sortedBy { siblingOrder.indexOfFirst { node -> node.id == it.id } }

        val updates = mutableListOf<TaskEntity>()
        newChildrenOrder.forEachIndexed { index, task ->
            val updated = task.copy(
                parentId = categoryId,
                sortOrder = index.toLong(),
                importance = categoryImportance
            )
            replaceInList(snapshot, updated)
            updates += updated
        }

        val storedCategory = category.copy(id = categoryId)
        snapshot += storedCategory
        updates += TaskHierarchyManager.renumberSiblings(snapshot, siblingParentId)
        repository.updateAll(updates.distinctBy(TaskEntity::id))
    }

    private fun normalizeAffectedCategories(
        snapshot: List<TaskEntity>,
        oldParentId: Int?,
        newParentId: Int?
    ): List<TaskEntity> {
        val updates = mutableListOf<TaskEntity>()
        oldParentId?.let { parentId ->
            snapshot.firstOrNull { it.id == parentId && it.isCategory }?.let {
                updates += TaskHierarchyManager.normalizeCategoryImportance(snapshot, parentId)
            }
        }
        newParentId?.let { parentId ->
            snapshot.firstOrNull { it.id == parentId && it.isCategory }?.let {
                updates += TaskHierarchyManager.normalizeCategoryImportance(snapshot, parentId)
            }
        }
        return updates
    }

    private fun nextSortOrder(parentId: Int?): Long {
        return currentTasks
            .filter { it.parentId == parentId }
            .maxOfOrNull(TaskEntity::sortOrder)
            ?.plus(1)
            ?: 0L
    }

    private fun replaceInList(tasks: MutableList<TaskEntity>, updated: TaskEntity) {
        val index = tasks.indexOfFirst { it.id == updated.id }
        if (index >= 0) {
            tasks[index] = updated
        }
    }

    /**
     * Helper comun para toda escritura a base.
     *
     * Garantiza:
     * - ejecucion sobre dispatcher IO
     * - logging consistente
     * - feedback uniforme a la UI
     */
    private fun launchDatabaseAction(
        successMessage: String? = null,
        action: suspend () -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                action()
                successMessage?.let { statusEvent.postValue(Event(it)) }
                WidgetRefresh.notifyTaskWidgets(appContext)
            } catch (exception: Exception) {
                Log.e("MainViewModel", "Database action failed", exception)
                statusEvent.postValue(Event(appContext.getString(R.string.task_failed)))
            }
        }
    }

    /** Factory manual para construir el ViewModel sin framework de DI. */
    class Factory(
        private val repository: TaskRepository,
        private val appContext: Context
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(MainViewModel::class.java))
            return MainViewModel(repository, appContext) as T
        }
    }
}
