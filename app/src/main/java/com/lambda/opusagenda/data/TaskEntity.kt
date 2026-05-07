package com.lambda.opusagenda.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Modelo persistido de una tarea.
 *
 * Este objeto representa exactamente la fila guardada en SQLite y no contiene
 * logica de presentacion. Los datos derivados para UI se calculan aparte.
 */
@Entity(tableName = "tasks")
data class TaskEntity(
    /** Identificador autogenerado por Room. */
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    /** Texto principal visible para el usuario. */
    val text: String,
    /** Distingue entre tarea normal y categoria/contenedor. */
    val isCategory: Boolean = false,
    /** Estado de completado. */
    val completed: Boolean = false,
    /** Importancia definida por el usuario, limitada al rango 1..4. */
    val importance: Int,
    /** Categoria padre o `null` si esta en la raiz. */
    val parentId: Int? = null,
    /** Orden manual entre hermanos. */
    val sortOrder: Long = 0L,
    /** Estado visual de expansion para categorias. */
    val expanded: Boolean = true,
    /** Fecha limite en epoch millis o `null` si no existe. */
    val dueDate: Long?,
    /** Campo opcional heredado del modelo base. */
    val link: String?,
    /** Marca si la tarea debe subir visualmente en la lista. */
    val pinned: Boolean = false,
    /** Momento exacto del recordatorio local en epoch millis o `null` si no existe. */
    val reminderAt: Long? = null,
    /** Cantidad positiva del intervalo de repeticion o `null` si no aplica. */
    val repeatAmount: Int? = null,
    /** Unidad persistida del intervalo de repeticion o `null` si no aplica. */
    val repeatUnit: String? = null,
    /** Momento de creacion en epoch millis. */
    val createdAt: Long
)
