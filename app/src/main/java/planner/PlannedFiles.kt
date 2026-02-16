package planner

import android.net.Uri

/** Fichier sélectionné pour le planning + sa durée estimée de lecture (en minutes). */
data class PlannedFile(
    val displayName: String,
    val durationMinutes: Int,
    val repeatCount: Int = 1,
    val sessionsPerRead: Int = 1,
    val difficulty: Int = 3,
    val sessionDurationMinutes: Int? = null,
    val importance: Int = 2,
    val uri: Uri? = null
    )

