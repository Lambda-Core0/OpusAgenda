package com.lambda.opusagenda.repository

import androidx.lifecycle.LiveData
import com.lambda.opusagenda.data.TaskDao
import com.lambda.opusagenda.data.TaskEntity

/**
 * Repositorio de tareas.
 *
 * Hoy actua como adaptador fino sobre el DAO, pero mantiene la arquitectura
 * preparada para crecer con otras fuentes de datos si hace falta.
 */
class TaskRepository(
    private val taskDao: TaskDao
) {
    /** Flujo observable que Room actualiza automaticamente. */
    val tasks: LiveData<List<TaskEntity>> = taskDao.observeTasks()

    /** Inserta una nueva tarea en la base local. */
    suspend fun insert(task: TaskEntity): Long {
        return taskDao.insert(task)
    }

    /** Inserta varias filas de manera conjunta. */
    suspend fun insertAll(tasks: List<TaskEntity>): List<Long> {
        return taskDao.insertAll(tasks)
    }

    /** Persiste cambios sobre una tarea existente. */
    suspend fun update(task: TaskEntity) {
        taskDao.update(task)
    }

    /** Persiste cambios sobre varias tareas existentes. */
    suspend fun updateAll(tasks: List<TaskEntity>) {
        if (tasks.isNotEmpty()) {
            taskDao.updateAll(tasks)
        }
    }

    /** Elimina una tarea de la base local. */
    suspend fun delete(task: TaskEntity) {
        taskDao.delete(task)
    }

    /** Elimina una lista concreta de ids. */
    suspend fun deleteByIds(ids: List<Int>) {
        if (ids.isNotEmpty()) {
            taskDao.deleteByIds(ids)
        }
    }

    /** Devuelve tareas con recordatorios vigentes para reprogramacion. */
    suspend fun getTasksWithFutureReminders(now: Long): List<TaskEntity> {
        return taskDao.getTasksWithFutureReminders(now)
    }
}
