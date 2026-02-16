package social

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

// DataStore pour persister les abonnements (survit aux redémarrages)
private val Context.followDataStore by preferencesDataStore(name = "follow_store")

object FollowStore {
    private val KEY_FOLLOWING = stringSetPreferencesKey("following_uids")
    private val KEY_PAIRS = stringSetPreferencesKey("uid_pseudos") // éléments "uid:pseudo"

    suspend fun getFollowing(context: Context): List<String> {
        val prefs = context.followDataStore.data.first()
        return prefs[KEY_FOLLOWING]?.toList() ?: emptyList()
    }

    suspend fun setFollowing(context: Context, uids: List<String>) {
        context.followDataStore.edit { it[KEY_FOLLOWING] = uids.toSet() }
    }

    suspend fun addFollowing(context: Context, uid: String) {
        context.followDataStore.edit { prefs ->
            val cur = prefs[KEY_FOLLOWING]?.toMutableSet() ?: mutableSetOf()
            cur.add(uid)
            prefs[KEY_FOLLOWING] = cur
        }
    }

    suspend fun removeFollowing(context: Context, uid: String) {
        context.followDataStore.edit { prefs ->
            val cur = prefs[KEY_FOLLOWING]?.toMutableSet() ?: mutableSetOf()
            cur.remove(uid)
            prefs[KEY_FOLLOWING] = cur
        }
    }

    suspend fun getPseudos(context: Context): Map<String, String> {
        val prefs = context.followDataStore.data.first()
        val pairs = prefs[KEY_PAIRS] ?: emptySet()
        return pairs.mapNotNull { s ->
            val i = s.indexOf(':'); if (i <= 0) null else s.substring(0, i) to s.substring(i + 1)
        }.toMap()
    }

    suspend fun putPseudo(context: Context, uid: String, pseudo: String) {
        context.followDataStore.edit { prefs ->
            val pairs = prefs[KEY_PAIRS]?.toMutableSet() ?: mutableSetOf()
            pairs.removeAll { it.startsWith("$uid:") }
            pairs.add("$uid:$pseudo")
            prefs[KEY_PAIRS] = pairs
        }
    }
}
