package com.lambda.opusagenda.data

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update

/**
 * DAO de Room para la tabla de tareas.
 *
 * Mantiene una API simple: observar, insertar, actualizar y eliminar.
 */
@Dao
interface TaskDao {

    /**
     * Observa todas las tareas persistidas.
     *
     * El orden base se conserva por `parentId` + `sortOrder` para reconstruir
     * la jerarquia visible en memoria.
     */
    @Query("SELECT * FROM tasks ORDER BY parentId ASC, sortOrder ASC, createdAt ASC")
    fun observeTasks(): LiveData<List<TaskEntity>>

    /** Lectura puntual para widgets y procesos sin LiveData. */
    @Query("SELECT * FROM tasks ORDER BY parentId ASC, sortOrder ASC, createdAt ASC")
    suspend fun getAllTasksOnce(): List<TaskEntity>

    @Query("SELECT * FROM tasks WHERE id = :id LIMIT 1")
    suspend fun getTaskByIdOnce(id: Int): TaskEntity?

    /** Inserta una tarea nueva. */
    @Insert
    suspend fun insert(task: TaskEntity): Long

    /** Inserta varias filas atomicas. */
    @Insert
    suspend fun insertAll(tasks: List<TaskEntity>): List<Long>

    /** Actualiza una tarea existente usando su `id`. */
    @Update
    suspend fun update(task: TaskEntity)

    /** Actualiza multiples tareas de una vez. */
    @Update
    suspend fun updateAll(tasks: List<TaskEntity>)

    /** Elimina una tarea concreta. */
    @Delete
    suspend fun delete(task: TaskEntity)

    /** Elimina una lista concreta de ids. */
    @Query("DELETE FROM tasks WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Int>)

    /** Borra todas las tareas, usado por importaciones completas. */
    @Query("DELETE FROM tasks")
    suspend fun clearAll()

    /** Recupera tareas con recordatorios futuros para reprogramarlas al reiniciar. */
    @Query("SELECT * FROM tasks WHERE isCategory = 0 AND reminderAt IS NOT NULL AND completed = 0 AND reminderAt > :now")
    suspend fun getTasksWithFutureReminders(now: Long): List<TaskEntity>
}
