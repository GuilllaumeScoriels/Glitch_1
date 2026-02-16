package Settings

import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import Settings.AppSettings
import androidx.datastore.preferences.preferencesDataStore
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import Settings.SettingsRepository

// ⚠️ déclaration top-level (en dehors de la classe)
private val Context.dataStore by preferencesDataStore(name = "app_settings")

class DataStoreSettingsRepository(private val context: Context) : SettingsRepository {

    private object Keys {
        val FULLSCREEN = booleanPreferencesKey("default_fullscreen")
        val WPM = intPreferencesKey("words_per_minute")
        val TTS_ENABLED = booleanPreferencesKey("tts_enabled")
        val TTS_RATE = floatPreferencesKey("tts_rate")
        val KEEP_ON = booleanPreferencesKey("keep_screen_on")
        val DEFAULT_WORD_DELAY = intPreferencesKey("default_word_delay_ms") // <— nouveau

        val DEFAULT_DIFF = intPreferencesKey("default_difficulty")
        val READ_MS_D1 = intPreferencesKey("read_ms_d1")
        val READ_MS_D2 = intPreferencesKey("read_ms_d2")
        val READ_MS_D3 = intPreferencesKey("read_ms_d3")
        val READ_MS_D4 = intPreferencesKey("read_ms_d4")
        val READ_MS_D5 = intPreferencesKey("read_ms_d5")

    }

    override val settings: Flow<AppSettings> =
        context.dataStore.data.map { prefs ->
            AppSettings(
                defaultFullScreen   = prefs[Keys.FULLSCREEN] ?: false,
                defaultWordDelayMs  = prefs[Keys.DEFAULT_WORD_DELAY] ?: 500,
                wordsPerMinute      = prefs[Keys.WPM] ?: 300,
                ttsEnabled          = prefs[Keys.TTS_ENABLED] ?: true,
                ttsRate             = prefs[Keys.TTS_RATE] ?: 1.0f,
                keepScreenOn        = prefs[Keys.KEEP_ON] ?: true,

                // Nouveaux champs (valeurs initiales si absent)
                defaultDifficulty   = (prefs[Keys.DEFAULT_DIFF] ?: 3).coerceIn(1, 5),
                readMsD1            = prefs[Keys.READ_MS_D1] ?: 400,
                readMsD2            = prefs[Keys.READ_MS_D2] ?: 450,
                readMsD3            = prefs[Keys.READ_MS_D3] ?: 500,
                readMsD4            = prefs[Keys.READ_MS_D4] ?: 575,
                readMsD5            = prefs[Keys.READ_MS_D5] ?: 675
            )
        }

    override suspend fun setDefaultFullScreen(enabled: Boolean) {
        context.dataStore.edit { it[Keys.FULLSCREEN] = enabled }
    }

    override suspend fun setWordsPerMinute(wpm: Int) {
        context.dataStore.edit { it[Keys.WPM] = wpm.coerceIn(50, 2000) }
    }

    override suspend fun setTtsEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.TTS_ENABLED] = enabled }
    }

    override suspend fun setTtsRate(rate: Float) {
        context.dataStore.edit { it[Keys.TTS_RATE] = rate.coerceIn(0.1f, 2.0f) }
    }

    override suspend fun setKeepScreenOn(enabled: Boolean) {
        context.dataStore.edit { it[Keys.KEEP_ON] = enabled }
    }

    override suspend fun setDefaultWordDelayMs(ms: Int) {
        context.dataStore.edit { it[Keys.DEFAULT_WORD_DELAY] = ms.coerceIn(2, 2500) }
    }

    override suspend fun setDefaultDifficulty(level: Int) {
        context.dataStore.edit { it[Keys.DEFAULT_DIFF] = level.coerceIn(1, 5) }
    }

    override suspend fun setReadMsD1(ms: Int) {
        context.dataStore.edit { it[Keys.READ_MS_D1] = ms.coerceIn(2, 2500) }
    }
    override suspend fun setReadMsD2(ms: Int) {
        context.dataStore.edit { it[Keys.READ_MS_D2] = ms.coerceIn(2, 2500) }
    }
    override suspend fun setReadMsD3(ms: Int) {
        context.dataStore.edit { it[Keys.READ_MS_D3] = ms.coerceIn(2, 2500) }
    }
    override suspend fun setReadMsD4(ms: Int) {
        context.dataStore.edit { it[Keys.READ_MS_D4] = ms.coerceIn(2, 2500) }
    }
    override suspend fun setReadMsD5(ms: Int) {
        context.dataStore.edit { it[Keys.READ_MS_D5] = ms.coerceIn(2, 2500) }
    }
}
