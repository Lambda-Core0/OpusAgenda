package com.lambda.opusagenda.ui

/**
 * Modelo mínimo para un acceso rápido independiente de las tareas.
 */
data class QuickLink(
    /** Texto visible en el botón del link rápido. */
    val label: String,
    /** URL final que se abrirá al pulsarlo. */
    val url: String
)
