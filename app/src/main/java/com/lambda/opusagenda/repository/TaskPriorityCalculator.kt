package com.lambda.opusagenda.repository

import com.lambda.opusagenda.data.TaskEntity
import com.lambda.opusagenda.util.TaskDateFormatter

/**
 * Heurística central de prioridad dinámica.
 *
 * Combina importancia manual, urgencia temporal y bonus por pin para producir
 * un puntaje entero que se usa al ordenar la lista.
 */
object TaskPriorityCalculator {
    /** Peso principal de la importancia elegida por el usuario. */
    private const val IMPORTANCE_WEIGHT = 18
    /** Bonus fijo para tareas fijadas. */
    private const val PINNED_BONUS = 40
    /** Base extra aplicada a tareas vencidas. */
    private const val OVERDUE_BONUS = 36

    /**
     * Calcula el puntaje final de una tarea.
     *
     * Las tareas completas se mandan al fondo usando `Int.MIN_VALUE`.
     */
    fun calculate(task: TaskEntity, nowMillis: Long = System.currentTimeMillis()): Int {
        if (task.isCategory) return task.importance.coerceIn(1, 4) * IMPORTANCE_WEIGHT
        if (task.completed) return Int.MIN_VALUE

        // La importancia define la base del puntaje.
        val importanceScore = task.importance.coerceIn(1, 4) * IMPORTANCE_WEIGHT
        // La urgencia crece cuanto menos días faltan o si la tarea ya venció.
        val urgencyScore = when (val daysLeft = TaskDateFormatter.daysUntil(task.dueDate, nowMillis)) {
            null -> 0
            in Int.MIN_VALUE..-1 -> OVERDUE_BONUS + (kotlin.math.abs(daysLeft) * 4)
            0 -> 30
            1 -> 22
            in 2..3 -> 14
            in 4..7 -> 6
            else -> 2
        }
        // El pin agrega prioridad visible sin ocultar el resto de señales.
        val pinnedScore = if (task.pinned) PINNED_BONUS else 0
        return importanceScore + urgencyScore + pinnedScore
    }
}
