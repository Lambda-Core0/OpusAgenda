package com.lambda.opusagenda.util

import android.content.Context
import androidx.core.content.ContextCompat
import com.lambda.opusagenda.R
import org.json.JSONArray

/**
 * Normaliza, serializa y colorea tags de tareas.
 */
object TaskTagUtils {

    private val tagSplitRegex = Regex("[,;\\n]+")
    private val whitespaceRegex = Regex("\\s+")

    fun normalizeTag(rawTag: String): String? {
        val trimmed = rawTag.trim()
        if (trimmed.isBlank()) return null

        val normalized = trimmed
            .replace(whitespaceRegex, "-")
            .replace(Regex("-+"), "-")
            .trim('-')

        return normalized.ifBlank { null }
    }

    fun parseTagInput(rawInput: String): List<String> {
        if (rawInput.isBlank()) return emptyList()

        return rawInput
            .split(tagSplitRegex)
            .mapNotNull(::normalizeTag)
            .distinctBy { it.lowercase() }
    }

    fun parseTags(rawTags: String?): List<String> {
        if (rawTags.isNullOrBlank()) return emptyList()

        val parsedFromJson = runCatching {
            val array = JSONArray(rawTags)
            buildList(array.length()) {
                for (index in 0 until array.length()) {
                    array.optString(index).letNotBlank(::normalizeTag)?.let { add(it) }
                }
            }
        }.getOrNull()

        val parsed = parsedFromJson
            ?: rawTags.split(tagSplitRegex).mapNotNull(::normalizeTag)

        return parsed.distinctBy { it.lowercase() }
    }

    fun serializeTags(tags: List<String>): String? {
        val normalized = tags.mapNotNull(::normalizeTag).distinctBy { it.lowercase() }
        return if (normalized.isEmpty()) null else JSONArray(normalized).toString()
    }

    fun matchesQuery(rawTags: String?, normalizedQuery: String): Boolean {
        if (normalizedQuery.isBlank()) return true

        val queryAsTag = normalizedQuery.replace(whitespaceRegex, "-")
        return parseTags(rawTags).any { tag ->
            val normalizedTag = tag.lowercase()
            normalizedTag.contains(normalizedQuery) ||
                normalizedTag.contains(queryAsTag) ||
                normalizedTag.replace('-', ' ').contains(normalizedQuery)
        }
    }

    fun colorForTag(context: Context, tag: String): Int {
        TagColorStore.getColor(context, tag)?.let { return it }
        val palette = intArrayOf(
            R.color.terminal_green,
            R.color.terminal_yellow,
            R.color.terminal_red,
            R.color.terminal_selected,
            R.color.terminal_green_dim
        )
        val index = (tag.lowercase().hashCode() and Int.MAX_VALUE) % palette.size
        return ContextCompat.getColor(context, palette[index])
    }

    private inline fun String.letNotBlank(transform: (String) -> String?): String? {
        return if (isNotBlank()) transform(this) else null
    }
}
