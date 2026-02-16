package Notifications

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// Nom spécifique pour éviter tout conflit d’extension
private val Context.notificationStateDataStore by preferencesDataStore("notification_state")

// Nom spécifique pour éviter la redéclaration
private object UnreadBadgeKeys { val UNREAD = intPreferencesKey("unread_count") }

object UnreadBadgeStore {
    fun unreadFlow(context: Context): Flow<Int> =
        context.notificationStateDataStore.data.map { it[UnreadBadgeKeys.UNREAD] ?: 0 }

    suspend fun increment(context: Context, by: Int = 1) {
        context.notificationStateDataStore.edit { prefs ->
            val cur = prefs[UnreadBadgeKeys.UNREAD] ?: 0
            prefs[UnreadBadgeKeys.UNREAD] = cur + by
        }
    }

    suspend fun reset(context: Context) {
        context.notificationStateDataStore.edit { it[UnreadBadgeKeys.UNREAD] = 0 }
    }
}
