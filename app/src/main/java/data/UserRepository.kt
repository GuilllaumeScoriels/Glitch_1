package data

import android.net.Uri
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.tasks.await

data class AppUser(
    val uid: String = "",
    val pseudo: String? = null,
    val photoUrl: String? = null,
    val photoPath: String? = null,
    val bio: String? = null
)