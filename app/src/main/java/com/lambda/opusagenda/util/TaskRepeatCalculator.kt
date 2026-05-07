package com.lambda.opusagenda.util

import android.content.Context
import com.lambda.opusagenda.R
import com.lambda.opusagenda.data.TaskEntity
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Helpers compartidos para intervalos de repeticion y reactivacion.
 */
object TaskRepeatCalculator {

    enum class RepeatUnit(
        val storageValue: String,
        val labelRes: Int,
        val quantityRes: Int
    ) {
        MINUTES("minutes", R.string.repeat_unit_minutes_label, R.plurals.repeat_unit_minutes),
        HOURS("hours", R.string.repeat_unit_hours_label, R.plurals.repeat_unit_hours),
        DAYS("days", R.string.repeat_unit_days_label, R.plurals.repeat_unit_days),
        WEEKS("weeks", R.string.repeat_unit_weeks_label, R.plurals.repeat_unit_weeks),
        MONTHS("months", R.string.repeat_unit_months_label, R.plurals.repeat_unit_months),
        YEARS("years", R.string.repeat_unit_years_label, R.plurals.repeat_unit_years);

        companion object {
            fun fromStorage(value: String?): RepeatUnit? {
                return values().firstOrNull { it.storageValue == value }
            }
        }
    }

    data class RepeatInterval(
        val amount: Int,
        val unit: RepeatUnit
    )

    fun intervalOf(task: TaskEntity): RepeatInterval? {
        return intervalOf(task.repeatAmount, task.repeatUnit)
    }

    fun intervalOf(amount: Int?, unitValue: String?): RepeatInterval? {
        val safeAmount = amount?.takeIf { it > 0 } ?: return null
        val unit = RepeatUnit.fromStorage(unitValue) ?: return null
        return RepeatInterval(safeAmount, unit)
    }

    fun hasRepeat(task: TaskEntity): Boolean = intervalOf(task) != null

    fun formatSummary(context: Context, amount: Int?, unitValue: String?): String {
        val repeat = intervalOf(amount, unitValue) ?: return context.getString(R.string.repeat_none)
        return context.resources.getQuantityString(repeat.unit.quantityRes, repeat.amount, repeat.amount)
    }

    fun applyCompletion(
        task: TaskEntity,
        completed: Boolean,
        nowMillis: Long = System.currentTimeMillis()
    ): TaskEntity {
        if (task.isCategory) return task
        if (!completed) return task.copy(completed = false)

        val repeat = intervalOf(task) ?: return task.copy(completed = true)
        val currentReminderAt = task.reminderAt ?: return task.copy(completed = true)
        val nextReminderAt = computeNextOccurrence(currentReminderAt, repeat, nowMillis)
        val shiftedDueDate = task.dueDate?.let { dueDate ->
            dueDate + (nextReminderAt - currentReminderAt)
        }

        return task.copy(
            completed = true,
            dueDate = shiftedDueDate,
            reminderAt = nextReminderAt
        )
    }

    fun computeNextOccurrence(
        anchorMillis: Long,
        repeat: RepeatInterval,
        nowMillis: Long = System.currentTimeMillis()
    ): Long {
        var next = Instant.ofEpochMilli(anchorMillis).atZone(ZoneId.systemDefault())
        while (next.toInstant().toEpochMilli() <= nowMillis) {
            next = next.plus(repeat)
        }
        return next.toInstant().toEpochMilli()
    }

    private fun ZonedDateTime.plus(repeat: RepeatInterval): ZonedDateTime {
        return when (repeat.unit) {
            RepeatUnit.MINUTES -> plusMinutes(repeat.amount.toLong())
            RepeatUnit.HOURS -> plusHours(repeat.amount.toLong())
            RepeatUnit.DAYS -> plusDays(repeat.amount.toLong())
            RepeatUnit.WEEKS -> plusWeeks(repeat.amount.toLong())
            RepeatUnit.MONTHS -> plusMonths(repeat.amount.toLong())
            RepeatUnit.YEARS -> plusYears(repeat.amount.toLong())
        }
    }
}
