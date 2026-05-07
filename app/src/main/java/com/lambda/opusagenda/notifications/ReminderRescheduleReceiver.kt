package com.lambda.opusagenda.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.lambda.opusagenda.data.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Reprograma recordatorios pendientes despues de reinicio o actualizacion.
 */
class ReminderRescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_MY_PACKAGE_REPLACED) return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val scheduler = TaskReminderScheduler(context)
                val dao = AppDatabase.getInstance(context).taskDao()
                dao.getAllTasksOnce().forEach(scheduler::sync)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
