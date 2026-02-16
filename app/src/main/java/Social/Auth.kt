package social

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.tasks.await

object Auth {
    fun init(context: Context) {
        try { FirebaseApp.initializeApp(context) } catch (_: Throwable) {}
    }

    suspend fun ensureSignedIn(): String {
        val auth = FirebaseAuth.getInstance()
        val cur = auth.currentUser
        if (cur != null) return cur.uid
        return auth.signInAnonymously().await().user?.uid
            ?: error("Impossible de se connecter anonymement")
    }

    val uid: String? get() = FirebaseAuth.getInstance().currentUser?.uid
}
