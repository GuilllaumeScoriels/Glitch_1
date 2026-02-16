package Settings
/* L'interface décrit ce que fait le dépôt de paramètres. */

import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    val settings: Flow<AppSettings>
    suspend fun setDefaultFullScreen(enabled: Boolean)
    suspend fun setWordsPerMinute(wpm: Int)
    suspend fun setTtsEnabled(enabled: Boolean)
    suspend fun setTtsRate(rate: Float)
    suspend fun setKeepScreenOn(enabled: Boolean)
    suspend fun setDefaultWordDelayMs(ms: Int)
    // Nouveaux setters
    suspend fun setDefaultDifficulty(level: Int)

    suspend fun setReadMsD1(ms: Int)
    suspend fun setReadMsD2(ms: Int)
    suspend fun setReadMsD3(ms: Int)
    suspend fun setReadMsD4(ms: Int)
    suspend fun setReadMsD5(ms: Int)

}
