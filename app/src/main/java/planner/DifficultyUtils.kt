package planner

object DifficultyUtils {

    /** Facteur appliqué à la durée (plus c’est difficile, plus c’est long). */
    fun speedFactor(difficulty: Int): Double = when (difficulty) {
        1 -> 0.85   // très facile → un peu plus rapide
        2 -> 0.93
        3 -> 1.00   // neutre
        4 -> 1.15
        5 -> 1.35   // très difficile → nettement plus long
        else -> 1.00
    }

    /** Poids de fréquence (plus c’est difficile, moins ça revient souvent). */
    fun frequencyWeight(difficulty: Int): Double = when (difficulty) {
        1 -> 1.30   // facile → revient plus souvent
        2 -> 1.15
        3 -> 1.00   // neutre
        4 -> 0.85
        5 -> 0.70   // difficile → revient moins souvent
        else -> 1.00
    }

    /** Arrondit la durée effective (>= 1 minute). */
    fun effectiveMinutes(baseMinutes: Int, difficulty: Int): Int {
        val mins = kotlin.math.ceil(baseMinutes * speedFactor(difficulty)).toInt()
        return if (mins < 1) 1 else mins
    }
}
