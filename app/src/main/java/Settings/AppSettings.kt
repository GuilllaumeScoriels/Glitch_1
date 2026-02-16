package Settings

data class AppSettings(
    val defaultFullScreen: Boolean = false,
    val defaultWordDelayMs: Int = 500, // <— nouveau : ms/mot par défaut
    val wordsPerMinute: Int = 300,     // conservé pour compat (non utilisé ici)
    val ttsEnabled: Boolean = true,
    val ttsRate: Float = 1.0f,         // 0.1f..2.0f
    val keepScreenOn: Boolean = true,

    // === Nouveaux réglages ===
    // Difficulté par défaut pour un fichier ajouté au calendrier (1..5)
    val defaultDifficulty: Int = 3,

    // Vitesses par défaut (ms/mot) par niveau de difficulté
    // 1 = très facile → plus rapide ; 5 = très difficile → plus lent
    val readMsD1: Int = 400,
    val readMsD2: Int = 450,
    val readMsD3: Int = 500,
    val readMsD4: Int = 575,
    val readMsD5: Int = 675
)

