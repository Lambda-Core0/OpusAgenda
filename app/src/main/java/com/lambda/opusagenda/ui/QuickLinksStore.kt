package com.lambda.opusagenda.ui

import android.content.Context

/**
 * Persistencia liviana para accesos rápidos.
 *
 * Se usa `SharedPreferences` porque la cantidad de enlaces es pequeña y no
 * justifica otra tabla Room separada del dominio principal.
 */
class QuickLinksStore(
    context: Context
) {
    /** Preferencias privadas donde se serializa la lista de links. */
    private val preferences = context.getSharedPreferences("quick_links", Context.MODE_PRIVATE)

    /**
     * Carga enlaces guardados o, si todavía no hay ninguno, devuelve defaults.
     */
    fun load(): List<QuickLink> {
        val stored = preferences.getStringSet(KEY_LINKS, null)
        if (stored.isNullOrEmpty()) return defaultLinks()

        return stored.mapNotNull { raw ->
            // Formato persistido: "label|url".
            val parts = raw.split(SEPARATOR, limit = 2)
            if (parts.size != 2) return@mapNotNull null
            val label = parts[0].trim()
            val url = parts[1].trim()
            if (label.isBlank() || url.isBlank()) return@mapNotNull null
            QuickLink(label = label, url = url)
        }.sortedBy { it.label.lowercase() }
    }

    /**
     * Reemplaza el contenido persistido completo por la lista actual.
     */
    fun save(links: List<QuickLink>) {
        val payload = links
            .filter { it.label.isNotBlank() && it.url.isNotBlank() }
            .map { "${it.label.trim()}$SEPARATOR${it.url.trim()}" }
            .toSet()

        preferences.edit().putStringSet(KEY_LINKS, payload).apply()
    }

    /** Conjunto base visible en una instalación limpia. */
    private fun defaultLinks(): List<QuickLink> {
        return listOf(
            QuickLink("MAIL", "https://mail.google.com"),
            QuickLink("DRIVE", "https://drive.google.com"),
            QuickLink("CALENDAR", "https://calendar.google.com")
        )
    }

    private companion object {
        /** Clave del registro dentro de SharedPreferences. */
        const val KEY_LINKS = "quick_links"
        /** Separador simple entre etiqueta y URL. */
        const val SEPARATOR = "|"
    }
}
