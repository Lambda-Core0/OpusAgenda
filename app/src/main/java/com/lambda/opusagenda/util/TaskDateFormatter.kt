package com.lambda.opusagenda.util

import android.content.Context
import com.lambda.opusagenda.R
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/**
 * Utilidades de fecha usadas por toda la app.
 *
 * Centraliza calculos relativos y formateo humano para que la logica
 * temporal no quede duplicada entre Activity, ViewModel y Adapter.
 */
object TaskDateFormatter {
    /** Zona horaria local del dispositivo. */
    private val zoneId: ZoneId = ZoneId.systemDefault()

    /** Combina estado relativo y fecha corta en una sola etiqueta legible. */
    fun formatForPicker(context: Context, millis: Long?): String {
        if (millis == null) return context.getString(R.string.no_due_date)
        val date = Instant.ofEpochMilli(millis).atZone(zoneId).toLocalDate()
        val displayFormatter = DateTimeFormatter.ofPattern("dd MMM", localeFor(context))
        return context.getString(
            R.string.task_date_with_relative,
            displayRelative(context, millis),
            date.format(displayFormatter)
        )
    }

    /** Devuelve un datetime localizado para resumir recordatorios custom. */
    fun formatDateTime(context: Context, millis: Long?): String {
        if (millis == null) return context.getString(R.string.no_reminder)
        val formatter = DateTimeFormatter
            .ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
            .withLocale(localeFor(context))
        return Instant.ofEpochMilli(millis).atZone(zoneId).format(formatter)
    }

    /** Devuelve textos del tipo "today", "tomorrow" o "overdue 2d". */
    fun displayRelative(context: Context, millis: Long?, nowMillis: Long = System.currentTimeMillis()): String {
        val days = daysUntil(millis, nowMillis) ?: return context.getString(R.string.no_due_date)
        return when {
            days < 0 -> context.getString(R.string.due_relative_overdue, kotlin.math.abs(days))
            days == 0 -> context.getString(R.string.due_relative_today)
            days == 1 -> context.getString(R.string.due_relative_tomorrow)
            else -> context.getString(R.string.due_relative_in, days)
        }
    }

    /**
     * Define si una tarea entra dentro de la vista "Today".
     *
     * Se consideran:
     * - vencidas
     * - con vencimiento hoy
     * - con vencimiento manana
     */
    fun isTodayBucket(millis: Long?, nowMillis: Long = System.currentTimeMillis()): Boolean {
        val days = daysUntil(millis, nowMillis) ?: return false
        return days <= 1
    }

    /** Calcula dias enteros entre la fecha actual y el vencimiento. */
    fun daysUntil(millis: Long?, nowMillis: Long = System.currentTimeMillis()): Int? {
        if (millis == null) return null
        val currentDate = Instant.ofEpochMilli(nowMillis).atZone(zoneId).toLocalDate()
        val dueDate = Instant.ofEpochMilli(millis).atZone(zoneId).toLocalDate()
        return dueDate.toEpochDay().minus(currentDate.toEpochDay()).toInt()
    }

    private fun localeFor(context: Context): Locale {
        return context.resources.configuration.locales[0] ?: Locale.getDefault()
    }
}
