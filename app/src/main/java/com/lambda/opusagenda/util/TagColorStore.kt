package com.lambda.opusagenda.util

import android.content.Context
import org.json.JSONObject
import java.util.Locale

/**
 * Persistencia liviana para colores personalizados de tags.
 *
 * Los colores se guardan por tag normalizado y se reutilizan en la lista,
 * notificaciones y backups.
 */
object TagColorStore {

    private const val PREFS_NAME = "opusagenda_tag_colors"
    private const val PREF_COLORS_KEY = "tag_colors"

    private val lock = Any()
    private var cachedColors: MutableMap<String, Int>? = null

    fun getColor(context: Context, tag: String): Int? = synchronized(lock) {
        val key = keyForTag(tag) ?: return null
        loadColorsLocked(context)[key]
    }

    fun setColor(context: Context, tag: String, color: Int) = synchronized(lock) {
        val key = keyForTag(tag) ?: return@synchronized
        val colors = loadColorsLocked(context)
        colors[key] = color
        persistLocked(context, colors)
    }

    fun clearColor(context: Context, tag: String) = synchronized(lock) {
        val key = keyForTag(tag) ?: return@synchronized
        val colors = loadColorsLocked(context)
        if (colors.remove(key) != null) {
            persistLocked(context, colors)
        }
    }

    fun snapshot(context: Context): Map<String, Int> = synchronized(lock) {
        loadColorsLocked(context).toMap()
    }

    fun replaceAll(context: Context, colors: Map<String, Int>) = synchronized(lock) {
        val normalized = mutableMapOf<String, Int>()
        colors.forEach { (tag, color) ->
            keyForTag(tag)?.let { normalized[it] = color }
        }
        persistLocked(context, normalized)
    }

    private fun loadColorsLocked(context: Context): MutableMap<String, Int> {
        cachedColors?.let { return it }

        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val rawJson = prefs.getString(PREF_COLORS_KEY, null).orEmpty()
        val loaded = mutableMapOf<String, Int>()

        if (rawJson.isNotBlank()) {
            runCatching { JSONObject(rawJson) }.getOrNull()?.let { json ->
                val keys = json.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    loaded[key] = json.optInt(key)
                }
            }
        }

        cachedColors = loaded
        return loaded
    }

    private fun persistLocked(context: Context, colors: MutableMap<String, Int>) {
        cachedColors = colors
        val json = JSONObject()
        colors.forEach { (key, color) -> json.put(key, color) }
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(PREF_COLORS_KEY, json.toString())
            .apply()
    }

    private fun keyForTag(tag: String): String? {
        return TaskTagUtils.normalizeTag(tag)?.lowercase(Locale.ROOT)
    }
}
