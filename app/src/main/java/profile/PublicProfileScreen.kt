package profile

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui. Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.ui.text.style.TextAlign
import java.util.concurrent.TimeUnit
import androidx.compose.runtime.produceState
import androidx.compose.foundation.clickable
import social.RemoteCalendarService
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.firebase.auth.FirebaseAuth
import data.UserReader
import coil.compose.AsyncImage
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.ui.draw.clip

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PublicProfileScreen(
    userId: String,
    onBack: (() -> Unit)? = null,
    onOpenCalendar: ((String, String) -> Unit)? = null
) {
    // --- ÉTAT LOCAL ---
    var pseudo by remember { mutableStateOf<String?>(null) }
    var followers by remember { mutableStateOf<List<Pair<String, String?>>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var myFollowing by remember { mutableStateOf<List<String>>(emptyList()) }
    var activity by remember { mutableStateOf<List<PublicCalendar>>(emptyList()) }
    var createdAtMillis by remember { mutableStateOf(0L) }

    val ageLabel = remember(createdAtMillis) {
        if (createdAtMillis <= 0L) null else {
            val days = TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis() - createdAtMillis).toInt()
            when {
                days >= 365 -> {
                    val years = days / 365
                    if (years > 1) "$years ans" else "1 an"
                }
                days >= 30 -> {
                    val months = days / 30
                    if (months > 1) "$months mois" else "1 mois"
                }
                else -> if (days > 1) "$days jours" else "1 jour"
            }
        }
    }

    val displayPseudo by produceState<String?>(initialValue = null, userId) {
        val db = FirebaseFirestore.getInstance()
        // 1) users/{userId}
        val u = runCatching { db.collection("users").document(userId).get().await() }.getOrNull()
        var p = listOf(
            u?.getString("pseudo"),
            u?.getString("username"),
            u?.getString("displayName"),
            u?.getString("handle")
        ).firstOrNull { !it.isNullOrBlank() }?.trim()

        // 2) profiles/{userId} si rien trouvé
        if (p.isNullOrBlank()) {
            val pr = runCatching { db.collection("profiles").document(userId).get().await() }.getOrNull()
            p = listOf(
                pr?.getString("pseudo"),
                pr?.getString("username"),
                pr?.getString("displayName"),
                pr?.getString("handle")
            ).firstOrNull { !it.isNullOrBlank() }?.trim()
        }
        // 3) usernames (source de vérité utilisée par SocialScreen)
        if (p.isNullOrBlank()) {
            val map = runCatching { UserProfileService.getPseudosForUids(listOf(userId)) }.getOrNull()
            p = map?.get(userId)
        }

        value = p
    }

    // URL de la photo à afficher
    val avatarUrl by produceState<String?>(initialValue = null, userId) {
        val db = FirebaseFirestore.getInstance()
        val u = runCatching { db.collection("users").document(userId).get().await() }.getOrNull()
        value = u?.getString("photoUrl")
    }

    // Chargement ponctuel des infos publiques
    LaunchedEffect(userId) {
        try {
            pseudo = runCatching { UserProfileService.getPseudoForUid(userId) }.getOrNull()
            if (pseudo.isNullOrBlank()) {
                val map = runCatching { UserProfileService.getPseudosForUids(listOf(userId)) }.getOrNull()
                pseudo = map?.get(userId)
            }
        } catch (_: Throwable) {
            // On garde la valeur actuelle
        } finally {
            loading = false
        }
    }

    // Mes abonnements (pour calculer "en commun")
    LaunchedEffect(Unit) {
        myFollowing = runCatching { RemoteCalendarService.listFollowing() }.getOrDefault(emptyList())
    }

    // Activité (calendriers publiés par cet utilisateur)
    LaunchedEffect(userId) {
        activity = runCatching { RemoteCalendarService.listCalendarsByOwners(listOf(userId)) }
            .getOrDefault(emptyList())
    }

    // Écoute en temps réel des abonnés (affichage en lecture seule)
    LaunchedEffect(userId) {
        try {
            RemoteCalendarService.observeFollowers(userId).collect { ids ->
                val map = runCatching { UserProfileService.getPseudosForUids(ids) }.getOrNull()
                followers = ids.map { uid -> uid to (map?.get(uid) ?: uid) }
            }
        } catch (_: Throwable) {
            followers = emptyList()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = pseudo ?: "Profil") },
                navigationIcon = {
                    if (onBack != null) {
                        TextButton(onClick = onBack) { Text("Retour") }
                    }
                }
            )
        }
    ) { padding ->
        if (loading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = androidx.compose.ui.Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            val otherFollowers = remember(followers, myFollowing) {
                followers.filterNot { myFollowing.contains(it.first) }
            }
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // --- En-tête profil public (avatar + pseudo) -------------------
                item {
                    ElevatedCard {
                        ListItem(
                            leadingContent = {
                                if (avatarUrl.isNullOrBlank()) {
                                    Icon(
                                        imageVector = Icons.Outlined.AccountCircle,
                                        contentDescription = "Photo de profil",
                                        modifier = Modifier.size(64.dp)
                                    )
                                } else {
                                    AsyncImage(
                                        model = avatarUrl,
                                        contentDescription = "Photo de profil",
                                        modifier = Modifier
                                            .size(64.dp)
                                            .clip(CircleShape)
                                    )
                                }
                            },
                            headlineContent = { Text(text = pseudo?.let { "@$it" } ?: userId) }
                        )
                    }
                }

                item {
                    val mutualIds = remember(followers, myFollowing) {
                        followers.map { it.first }.toSet().intersect(myFollowing.toSet()).toList()
                    }
                    Text("Abonnés en commun (${mutualIds.size})", style = MaterialTheme.typography.titleMedium)
                }
                items(
                    items = followers.filter { myFollowing.contains(it.first) },
                    key = { it.first + "_mutual" }
                ) { (uid, name) ->
                    ListItem(headlineContent = { Text(name ?: uid) })
                    Divider()
                }
                item {
                    Text("Abonnés", style = MaterialTheme.typography.titleMedium)
                }
                val otherFollowersList = otherFollowers
                if (otherFollowersList.isEmpty()) {
                    item {
                        Text(
                            text = "Aucun abonné.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                } else {
                    items(
                        items = otherFollowersList,
                        key = { it.first + "_all" }
                    ) { (uid, name) ->
                        ListItem(
                            headlineContent = { Text(name ?: uid) }
                        )
                        Divider()
                    }
                }
                // --- Activité (mise en avant) ---
                item {
                    Text(
                        "Activité",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                    if (ageLabel != null) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Âge du compte : $ageLabel",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    if (activity.isEmpty()) {
                        Text("Aucun calendrier public", style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(8.dp))
                    }
                }
                items(
                    items = activity,
                    key = { it.id }
                ) { c ->
                    ElevatedCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenCalendar?.invoke(c.id, c.title) }
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Text(c.title, style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(6.dp))
                            LinearProgressIndicator(
                                progress = { c.completion.toFloat().coerceIn(0f, 1f) },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Complétude : ${(c.completion * 100).toInt()}% • ${c.itemCount} créneaux • ${c.likesCount} j’aime",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
}

