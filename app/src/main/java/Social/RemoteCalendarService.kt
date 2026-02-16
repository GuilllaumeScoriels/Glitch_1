package social

import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import planner.ScheduleItem
import planner.ScheduleStatus
import java.time.Instant
import java.time.LocalDateTime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.channels.awaitClose
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.Query
import profile.PublicCalendar

object RemoteCalendarService {
    private val firestore: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
// ou, si tu préfères une propriété calculée :
// private val firestore: FirebaseFirestore get() = FirebaseFirestore.getInstance()

    private fun completionOf(items: List<ScheduleItem>): Double {
        if (items.isEmpty()) return 0.0
        val done = items.count { it.status == ScheduleStatus.FAIT }
        return done.toDouble() / items.size
    }

    suspend fun publishCalendar(calendarId: String, title: String, items: List<ScheduleItem>) {
        val uid = Auth.ensureSignedIn()
        val now = Instant.now().toEpochMilli()
        val data = mapOf(
            "id" to calendarId,
            "ownerId" to uid,
            "title" to title,
            "completion" to completionOf(items),
            "itemCount" to items.size,
            "updatedAt" to now
        )
        try {
            firestore.collection("calendars").document(calendarId).set(data).await()
            // Publie/écrase également les items en sous-collection
            val itemsRef = firestore.collection("calendars").document(calendarId).collection("items")
            items.forEach { itItem ->
                val map = mapOf(
                    "id" to itItem.id,
                    "start" to itItem.start.toString(),
                    "durationMinutes" to itItem.durationMinutes,
                    "title" to itItem.title,
                    "notes" to itItem.notes,
                    "status" to itItem.status.name
                )
                try { itemsRef.document(itItem.id).set(map).await() } catch (_: Throwable) {}
                // Notification "publish" pour l'auteur (non bloquante)
                try {
                    firestore.collection("users").document(uid)
                        .collection("notifications")
                        .add(
                            mapOf(
                                "type" to "publish",
                                "calendarId" to calendarId,
                                "timestamp" to com.google.firebase.firestore.FieldValue.serverTimestamp()
                            )
                        ).await()
                } catch (_: Throwable) { /* on ignore l'erreur pour ne rien bloquer */ }
            }
        } catch (_: Throwable) { /* no-op : ne casse rien en offline */ }
    }

    suspend fun updateCompletionIfPublished(calendarId: String, items: List<ScheduleItem>) {
        val now = Instant.now().toEpochMilli()
        val newCompletion = completionOf(items)
        try {
            val ref = firestore.collection("calendars").document(calendarId)
            val snap = ref.get().await()
            if (snap.exists()) {
                ref.update(mapOf("completion" to newCompletion, "updatedAt" to now)).await()
            }
        } catch (_: Throwable) { /* no-op */ }
    }

    /** Abonnements */
    suspend fun follow(targetUserId: String) {
        val uid = Auth.ensureSignedIn()
        val follows = firestore.collection("follows")
        val followingRef = follows.document(uid).collection("following").document(targetUserId)
        val followersRef = follows.document(targetUserId).collection("followers").document(uid)

        val batch = firestore.batch()
        batch.set(followingRef, mapOf("since" to FieldValue.serverTimestamp()), SetOptions.merge())
        batch.set(followersRef, mapOf("since" to FieldValue.serverTimestamp()), SetOptions.merge())

        try {
            batch.commit().await()
            // Notification "follow" pour l'utilisateur suivi
            try {
                firestore.collection("users").document(targetUserId)
                    .collection("notifications")
                    .add(
                        mapOf(
                            "type" to "follow",
                            "fromUid" to uid,
                            "timestamp" to FieldValue.serverTimestamp()
                        )
                    ).await()
            } catch (_: Throwable) { }
        } catch (_: Throwable) {
            // On ne casse rien dans l’app ; un log peut aider au debug si besoin
        }
    }

    suspend fun unfollow(targetUserId: String) {
        val uid = Auth.ensureSignedIn()
        val follows = firestore.collection("follows")
        val followingRef = follows.document(uid).collection("following").document(targetUserId)
        val followersRef = follows.document(targetUserId).collection("followers").document(uid)

        val batch = firestore.batch()
        batch.delete(followingRef)
        batch.delete(followersRef)

        try {
            batch.commit().await()
        } catch (_: Throwable) {
            // No-op pour ne pas bloquer l’UX
        }
    }

    suspend fun listFollowing(): List<String> {
        val uid = Auth.ensureSignedIn()
        return try {
            firestore.collection("follows")
                .document(uid).collection("following").get().await()
                .documents.map { it.id }
        } catch (_: Throwable) { emptyList() }
    }

    /** Abonnés (followers) d'un utilisateur — lecture ponctuelle */
    suspend fun listFollowers(userId: String? = null): List<String> {
        val targetUserId = userId ?: Auth.ensureSignedIn()
        return try {
            firestore.collection("follows").document(targetUserId)
                .collection("followers").get().await()
                .documents.map { it.id } // UID du follower
        } catch (_: Throwable) { emptyList() }
    }

    /** Flux temps réel des abonnés (followers) d'un utilisateur */
    fun observeFollowers(userId: String): Flow<List<String>> = callbackFlow {
        val ref = firestore.collection("follows").document(userId)
            .collection("followers")
            .orderBy("since", Query.Direction.DESCENDING)
        val registration = ref.addSnapshotListener { snap, err ->
            if (err != null) {
                trySend(emptyList())
                return@addSnapshotListener
            }
            val ids = snap?.documents?.map { it.id } ?: emptyList()
            trySend(ids).isSuccess
        }
        awaitClose { registration.remove() }
    }

    /** Likes */
    suspend fun toggleLike(calendarId: String): Pair<Boolean, Int> {
        val uid = Auth.ensureSignedIn()
        val likeRef = firestore.collection("calendars").document(calendarId)
            .collection("likes").document(uid)

        return try {
            val liked = likeRef.get().await().exists()
            if (liked) likeRef.delete().await() else likeRef.set(mapOf("at" to Instant.now().toEpochMilli())).await()

            // recalcul simple (OK pour démo)
            val count = firestore.collection("calendars").document(calendarId)
                .collection("likes").get().await().size()
            firestore.collection("calendars").document(calendarId).update(mapOf("likesCount" to count)).await()
            (!liked) to count
        } catch (_: Throwable) {
            false to -1
        }
    }

    /** Flux : récupère les derniers calendriers des personnes suivies */
    suspend fun feed(): List<PublicCalendar> {
        val following = listFollowing()
        if (following.isEmpty()) return emptyList()
        // Firestore n'autorise que IN <= 10 ; on tronque pour simplicité
        val owners = following.take(10)
        return try {
            firestore.collection("calendars")
                .whereIn("ownerId", owners)
                .get().await()
                .documents.mapNotNull { d ->
                    PublicCalendar(
                        id = d.getString("id") ?: return@mapNotNull null,
                        ownerId = d.getString("ownerId") ?: return@mapNotNull null,
                        title = d.getString("title") ?: "Calendrier",
                        completion = d.getDouble("completion") ?: 0.0,
                        itemCount = (d.getLong("itemCount") ?: 0L).toInt(),
                        updatedAt = d.getLong("updatedAt") ?: 0L,
                        likesCount = (d.getLong("likesCount") ?: 0L).toInt()
                    )
                }.sortedByDescending { it.updatedAt }
        } catch (_: Throwable) { emptyList() }
    }

    suspend fun loadItems(calendarId: String): List<ScheduleItem> {
        return try {
            val ref = firestore.collection("calendars").document(calendarId).collection("items")
            ref.get().await().documents.mapNotNull { d ->
                val id = d.getString("id") ?: d.id
                val startStr = d.getString("start") ?: return@mapNotNull null
                val duration = (d.getLong("durationMinutes") ?: 0L).toInt()
                val title = d.getString("title") ?: "Créneau"
                val notes = d.getString("notes") ?: ""
                val statusStr = d.getString("status") ?: "NONE"
                val status = runCatching { ScheduleStatus.valueOf(statusStr) }.getOrElse { ScheduleStatus.NONE }
                ScheduleItem(
                    id = id,
                    start = LocalDateTime.parse(startStr),
                    durationMinutes = duration,
                    title = title,
                    notes = notes,
                    status = status
                )
            }.sortedBy { it.start }
        } catch (_: Throwable) { emptyList() }
    }

    /** Tous les calendriers publics d'un utilisateur donné (profil) */
    suspend fun calendarsOfUser(userId: String): List<PublicCalendar> {
        return try {
            firestore.collection("calendars")
                .whereEqualTo("ownerId", userId)
                .get().await()
                .documents.mapNotNull { d ->
                    PublicCalendar(
                        id = d.getString("id") ?: return@mapNotNull null,
                        ownerId = d.getString("ownerId") ?: return@mapNotNull null,
                        title = d.getString("title") ?: "Calendrier",
                        completion = d.getDouble("completion") ?: 0.0,
                        itemCount = (d.getLong("itemCount") ?: 0L).toInt(),
                        updatedAt = d.getLong("updatedAt") ?: 0L,
                        likesCount = (d.getLong("likesCount") ?: 0L).toInt()
                    )
                }.sortedByDescending { it.updatedAt }
        } catch (_: Throwable) { emptyList() }
    }

    /**
     * Récupère les calendriers publiés appartenant à une liste d'auteurs.
     * - Gère la limite Firestore "whereIn" (10 éléments) en chunkant la liste.
     * - Tolère différents schémas de champs pour la complétude (completion | doneCount/totalCount | itemCount).
     * - Récupère aussi likesCount et updatedAt (plusieurs alias couverts pour robustesse).
     */
    suspend fun listCalendarsByOwners(owners: List<String>): List<PublicCalendar> {
        if (owners.isEmpty()) return emptyList()

        val chunks = owners.filter { it.isNotBlank() }.distinct().chunked(10)
        val results = mutableListOf<PublicCalendar>()

        for (chunk in chunks) {
            val snap = firestore.collection("calendars")
                .whereIn("ownerId", chunk)
                .whereEqualTo("published", true) // ou "isPublic" selon ton schéma
                .get()
                .await()

            for (d in snap.documents) {
                val id = d.id
                val title = d.getString("title") ?: "(sans titre)"
                val ownerId = d.getString("ownerId") ?: ""

                // Complétude
                val completionDirect = d.getDouble("completion")
                val doneCount = d.getLong("doneCount")?.toInt()
                val totalCount = d.getLong("totalCount")?.toInt()
                val itemCountField = d.getLong("itemCount")?.toInt()

                val completion = when {
                    completionDirect != null -> completionDirect.coerceIn(0.0, 1.0)
                    doneCount != null && totalCount != null && totalCount > 0 ->
                        (doneCount.toDouble() / totalCount.toDouble()).coerceIn(0.0, 1.0)
                    else -> 0.0
                }

                val itemCount = when {
                    itemCountField != null -> itemCountField
                    totalCount != null -> totalCount
                    else -> 0
                }

                // 👍 Likes
                val likesCount = d.getLong("likesCount")?.toInt() ?: 0

                // 🕒 Mise à jour (couvre plusieurs noms & formats)
                val updatedAt: Long =
                    d.getTimestamp("updatedAt")?.toDate()?.time ?:
                    d.getTimestamp("updateAt")?.toDate()?.time ?:
                    d.getLong("updatedAt") ?:
                    d.getLong("updateAt") ?:
                    d.getLong("lastModified") ?:
                    d.getLong("timestamp") ?:
                    0L

                results += PublicCalendar(
                    id = id,
                    title = title,
                    ownerId = ownerId,
                    completion = completion,
                    itemCount = itemCount,
                    likesCount = likesCount,
                    updatedAt = updatedAt
                )
            }
        }

        // Tri optionnel : du plus récent au plus ancien
        return results.sortedByDescending { it.updatedAt }
    }
}
