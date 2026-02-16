package Notifications

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// Nom spécifique pour éviter tout conflit d’extension
private val Context.mainCalendarDataStore by preferencesDataStore("main_calendar_prefs")

// Nom spécifique pour éviter la redéclaration
private object MainCalendarKeys { val MAIN_ID = stringPreferencesKey("main_calendar_id") }

object MainCalendarStore {
    fun mainIdFlow(context: Context): Flow<String?> =
        context.mainCalendarDataStore.data.map { it[MainCalendarKeys.MAIN_ID] }

    suspend fun setMainId(context: Context, id: String) {
        context.mainCalendarDataStore.edit { it[MainCalendarKeys.MAIN_ID] = id }
    }
}
