package com.lambda.opusagenda.util

/**
 * Wrapper de evento de un solo consumo para `LiveData`.
 *
 * Se usa para mensajes temporales y evita repetir la misma acción cuando
 * la Activity se recrea o vuelve a observar el valor.
 */
class Event<out T>(private val content: T) {
    private var consumed = false

    /** Entrega el contenido solo la primera vez que alguien lo consume. */
    fun getContentIfNotHandled(): T? {
        if (consumed) return null
        consumed = true
        return content
    }
}
