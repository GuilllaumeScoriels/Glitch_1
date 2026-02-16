package social

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.FieldValue
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

data class Comment(
    val id: String,
    val calendarId: String,
    val itemId: String,
    val fromUid: String,
    val text: String,
    val timestamp: Long
)

object CommentsService {
    private val db = FirebaseFirestore.getInstance()

    fun observeComments(calendarId: String, itemId: String) = callbackFlow<List<Comment>> {
        val ref = db.collection("calendars").document(calendarId)
            .collection("items").document(itemId)
            .collection("comments")
            .orderBy("timestamp", Query.Direction.DESCENDING)

        val reg = ref.addSnapshotListener { snap, err ->
            if (err != null) { trySend(emptyList()); return@addSnapshotListener }
            val list = snap?.documents?.map { d ->
                Comment(
                    id = d.id,
                    calendarId = calendarId,
                    itemId = itemId,
                    fromUid = d.getString("fromUid") ?: "",
                    text = d.getString("text") ?: "",
                    timestamp = d.getLong("timestamp") ?: 0L
                )
            } ?: emptyList()
            trySend(list).isSuccess
        }
        awaitClose { reg.remove() }
    }

    suspend fun postComment(calendarId: String, itemId: String, text: String) {
        val uid = Auth.ensureSignedIn()
        val data = mapOf(
            "fromUid" to uid,
            "text" to text,
            "timestamp" to FieldValue.serverTimestamp()
        )
        db.collection("calendars").document(calendarId)
            .collection("items").document(itemId)
            .collection("comments")
            .add(data).await()

        // Notifie le propriétaire du calendrier
        val ownerId = try {
            db.collection("calendars").document(calendarId).get().await().getString("ownerId")
        } catch (_: Throwable) { null }

        if (ownerId != null && ownerId != uid) {
            try {
                db.collection("users").document(ownerId)
                    .collection("notifications")
                    .add(
                        mapOf(
                            "type" to "comment",
                            "fromUid" to uid,
                            "calendarId" to calendarId,
                            "itemId" to itemId,
                            "timestamp" to FieldValue.serverTimestamp()
                        )
                    ).await()
            } catch (_: Throwable) { }
        }
    }
}
