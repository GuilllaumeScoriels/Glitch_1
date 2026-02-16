package profile

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import data.AppUser
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

// Petite extension pour convertir un doc Firestore en StateFlow<AppUser?>
private fun com.google.firebase.firestore.DocumentReference.snapshotsAsStateFlow()
        : StateFlow<AppUser?> {
    return callbackFlow {
        val listener = addSnapshotListener { snap, _ ->
            val data = if (snap != null && snap.exists()) {
                AppUser(
                    uid = snap.id,
                    pseudo = snap.getString("pseudo"),
                    photoUrl = snap.getString("photoUrl"),
                    photoPath = snap.getString("photoPath"),
                    bio = snap.getString("bio")
                )
            } else null
            trySend(data)
        }
        awaitClose { listener.remove() }
    }.stateIn(
        scope = kotlinx.coroutines.GlobalScope, // remplace par un scope di/vm si tu préfères
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = null
    )
}
