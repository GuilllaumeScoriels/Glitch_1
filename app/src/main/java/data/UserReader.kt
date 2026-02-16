package data

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

object UserReader {
    private val db by lazy { FirebaseFirestore.getInstance() }

    fun listenUser(uid: String): Flow<Result<AppUser?>> = callbackFlow {
        val ref = db.collection("users").document(uid)
        val reg = ref.addSnapshotListener { snap, err ->
            if (err != null) {
                trySend(Result.failure(err))
            } else {
                trySend(Result.success(snap.toAppUser()))
            }
        }
        awaitClose { reg.remove() }
    }

    suspend fun getOnce(uid: String): AppUser? {
        val snap = db.collection("users").document(uid).get().await()
        return snap.toAppUser()
    }

    private fun DocumentSnapshot?.toAppUser(): AppUser? {
        if (this == null || !exists()) return null
        return AppUser(
            uid = id,
            pseudo = getString("pseudo"),
            photoUrl = getString("photoUrl"),
            bio = getString("bio")
        )
    }
}
