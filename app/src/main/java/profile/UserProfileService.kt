package profile

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.tasks.await
import social.Auth
import com.google.firebase.ktx.Firebase
import com.google.firebase.auth.ktx.auth
import android.net.Uri
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.storage.FirebaseStorage

/**
 * Gestion des pseudos utilisateurs avec unicité globale.
 *
 * Schéma Firestore :
 * - users/{uid} : { uid, pseudo, pseudoLower, createdAt, updatedAt }
 * - usernames/{pseudoLower} : { uid, createdAt }
 */
object UserProfileService {
    private val db: FirebaseFirestore get() = FirebaseFirestore.getInstance()

    private fun normalize(input: String): String =
        input.trim().lowercase()

    private fun isValidPseudo(p: String): Boolean =
        p.length in 3..20 && p.all { it.isLetterOrDigit() || it == '_' || it == '-' || it == '.' }

    suspend fun getMyPseudo(): String? {
        val uid = Auth.ensureSignedIn()
        return getPseudoForUid(uid)
    }

    suspend fun getPseudoForUid(uid: String): String? {
        return try {
            val snap = db.collection("users").document(uid).get().await()

            // Essaye plusieurs clés usuelles
            val fromUsers = listOf(
                snap.getString("pseudo"),
                snap.getString("username"),
                snap.getString("displayName"),
                snap.getString("handle")
            ).firstOrNull { !it.isNullOrBlank() }

            if (!fromUsers.isNullOrBlank()) return fromUsers

            // Fallback éventuel sur une collection "profiles"
            val prof = db.collection("profiles").document(uid).get().await()
            listOf(
                prof.getString("pseudo"),
                prof.getString("username"),
                prof.getString("displayName"),
                prof.getString("handle")
            ).firstOrNull { !it.isNullOrBlank() }
        } catch (_: Throwable) {
            null
        }
    }

    suspend fun resolvePseudoToUid(pseudo: String): String? = try {
        val key = normalize(pseudo)
        val snap = db.collection("usernames").document(key).get().await()
        snap.getString("uid")
    } catch (_: Throwable) { null }

    /**
     * Définit le pseudo de l'utilisateur courant en transaction :
     * - Vérifie l'unicité (usernames/{pseudoLower})
     * - Met à jour users/{uid}
     * - Met à jour usernames/{pseudoLower} et libère l'ancien pseudo si besoin
     */
    suspend fun setPseudo(raw: String): Result<String> {
        val auth = FirebaseAuth.getInstance()
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Non connecté"))
        val desired = raw.trim()
        val key = normalize(desired)
        if (!isValidPseudo(desired)) {
            return Result.failure(IllegalArgumentException("Le pseudo doit faire 3 à 20 caractères (lettres, chiffres, _.-)"))
        }

        return try {
            db.runTransaction { tx ->
                val unameRef = db.collection("usernames").document(key)
                val existing = tx.get(unameRef)
                if (existing.exists() && existing.getString("uid") != uid) {
                    throw IllegalStateException("Ce pseudo est déjà pris.")
                }

                val userRef = db.collection("users").document(uid)
                val current = tx.get(userRef)
                val oldKey = (current.getString("pseudoLower") ?: "")
                if (oldKey.isNotBlank() && oldKey != key) {
                    val oldRef = db.collection("usernames").document(oldKey)
                    val oldSnap = tx.get(oldRef)
                    if (oldSnap.exists() && oldSnap.getString("uid") == uid) {
                        tx.delete(oldRef)
                    }
                }

                tx.set(
                    userRef,
                    mapOf(
                        "uid" to uid,
                        "pseudo" to desired,
                        "pseudoLower" to key,
                        "updatedAt" to FieldValue.serverTimestamp(),
                        "createdAt" to (current.getTimestamp("createdAt") ?: FieldValue.serverTimestamp())
                    ),
                    SetOptions.merge()
                )

                null
            }.await()
            Result.success(desired)
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    suspend fun getPseudosForUids(uids: List<String>): Map<String, String> {
        if (uids.isEmpty()) return emptyMap()
        return try {
            // Hypothèse: collection "users" avec champs: "uid" et "pseudo"
            val snapshots = db.collection("usernames")
                .whereIn("uid", uids.take(10))
                .get()
                .await()
                .documents

            val firstBatch = snapshots.mapNotNull { d ->
                val uid = d.getString("uid")
                val name = d.id
                if (uid == null) null else uid to name
            }.toMap()

            // S’il y a >10 UIDs, on boucle par tranches
            if (uids.size <= 10) return firstBatch

            val rest = mutableMapOf<String, String>()
            uids.drop(10).chunked(10).forEach { chunk ->
                val docs = db.collection("usernames")
                    .whereIn("uid", chunk)
                    .get()
                    .await()
                    .documents
                docs.forEach { d ->
                    val uid = d.getString("uid")
                    val name = d.id
                    if (uid != null) rest[uid] = name
                }
            }

            firstBatch + rest
        } catch (_: Throwable) {
            // En cas d’erreur réseau, on renvoie une map vide: l’UI reste vivante.
            emptyMap()
        }
    }

    suspend fun findUidByPseudo(pseudo: String): String? {
        val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
        val doc = db.collection("usernames")
            .document(pseudo.trim().lowercase())
            .get().await()
        return doc.getString("uid")
    }

    /** L'utilisateur courant suit-il ownerId ? */
    private suspend fun isFollowerOfOwner(ownerId: String): Boolean {
        val viewer = Firebase.auth.currentUser?.uid ?: return false
        return try {
            val db = FirebaseFirestore.getInstance()
            db.collection("follows")
                .document(ownerId)
                .collection("followers")
                .document(viewer)
                .get()
                .await()
                .exists()
        } catch (_: Throwable) {
            false
        }
    }

    /** E-mail actuel (si défini) */
    suspend fun getMyEmail(): String? = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.email

    /** Mise à jour de l'e-mail (demande une auth récente si le compte n'est pas anonyme) */
    suspend fun setEmail(email: String): Result<Unit> {
        val auth = com.google.firebase.auth.FirebaseAuth.getInstance()
        val user = auth.currentUser ?: return Result.failure(IllegalStateException("Non connecté"))
        return runCatching { user.updateEmail(email).await() }
    }

    /** URL de photo pour un uid donné (ou null si absente) */
    suspend fun getPhotoUrl(uid: String): String? {
        val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
        val snap = db.collection("users").document(uid).get().await()
        return snap.getString("photoUrl")
    }

    /** Définit / remplace la photo de profil de l'utilisateur courant. Retourne l'URL publique. */
    suspend fun setProfilePhoto(contentUri: Uri): Result<String> = runCatching {
        val auth = com.google.firebase.auth.FirebaseAuth.getInstance()
        val uid = auth.currentUser?.uid ?: error("Non connecté")

        val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
        val storage = FirebaseStorage.getInstance()

        // Ancienne photo pour nettoyage ultérieur
        val userRef = db.collection("users").document(uid)
        val current = userRef.get().await()
        val oldPath = current.getString("photoPath")

        // Nouveau chemin dans le bucket
        val path = "users/$uid/profile/${System.currentTimeMillis()}.jpg"
        val ref = storage.reference.child(path)

        val user = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
        Log.d("PROFILE", "Auth user = ${user?.uid} email=${user?.email}")
        Log.d("PROFILE", "Uploading to: bucket=${ref.bucket} path=${ref.path}")
        Log.d("PROFILE", "Bucket from app = ${storage.reference.bucket}")
        Log.d("PROFILE", "App project = ${FirebaseApp.getInstance().options.projectId}")
        Log.d("PROFILE", "Storage url = ${FirebaseApp.getInstance().options.storageBucket}")


        // Upload
        ref.putFile(contentUri).await()
        val downloadUrl = ref.downloadUrl.await().toString()

        // Mise à jour du document 'users/{uid}'
        userRef.set(
            mapOf(
                "uid" to uid,
                "photoUrl" to downloadUrl,
                "photoPath" to path,
                "updatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
            ),
            com.google.firebase.firestore.SetOptions.merge()
        ).await()

        // Nettoyage ancienne photo si différente
        if (!oldPath.isNullOrBlank() && oldPath != path) {
            runCatching { storage.reference.child(oldPath).delete().await() }
        }

        downloadUrl
    }

    /** Supprime la photo de profil de l'utilisateur courant (et le champ en base). */
    suspend fun removeProfilePhoto(): Result<Unit> = runCatching {
        val auth = com.google.firebase.auth.FirebaseAuth.getInstance()
        val uid = auth.currentUser?.uid ?: error("Non connecté")

        val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
        val storage = FirebaseStorage.getInstance()
        val userRef = db.collection("users").document(uid)

        // Récupérer l'ancien chemin éventuel
        val snap = userRef.get().await()
        val oldPath = snap.getString("photoPath")
        if (!oldPath.isNullOrBlank()) {
            runCatching { storage.reference.child(oldPath).delete().await() }
        }

        // Effacer les champs côté Firestore
        userRef.set(
            mapOf(
                "photoUrl" to null,
                "photoPath" to null,
                "updatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
            ),
            com.google.firebase.firestore.SetOptions.merge()
        ).await()
    }
}
