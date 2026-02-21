package social

import Notifications.AppNotificationEntity
import Notifications.AppNotificationsRepository
import Notifications.UnreadBadgeStore
import androidx.compose.foundation.Image
import androidx.compose.runtime.*
import androidx.compose.material3.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.google.android.play.integrity.internal.s
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.collectLatest
import org.bouncycastle.asn1.x500.style.RFC4519Style.title
import androidx.compose.ui.res.painterResource
import com.example.a18.R

data class AppNotification(
    val id: String,
    val type: String,
    val fromUid: String?,
    val calendarId: String?,
    val itemId: String?,
    val timestamp: Long
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(navController: NavController) {
    var uid by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { uid = Auth.ensureSignedIn() }

    val ctx = LocalContext.current
    LaunchedEffect(Unit) {
        UnreadBadgeStore.reset(ctx)
    }

    val notifications by produceState(initialValue = emptyList<AppNotification>(), uid) {
        if (uid == null) return@produceState
        NotificationsRepository.observeNotifications(uid!!).collectLatest { value = it }
    }

    /* Récupérer le context courant android dans le package où LocalContext est défini;
    Nécessaire pour obtenir l'instance de la Database. */
    val context = LocalContext.current

    /* Créer une liste de notifications issue de la DB.
    "by" = délégation Kotlin: permet d'écrire stored au lieu de stored.value.
     runtime = module de Jetpack Compose qui gère le fonctionnement iterne vivant de l'UI;
     produceState en est une fonction qui transforme source asynchrone (Flow) en variable d'UI observable.
     produceState écoute le Flow Room et met à jour value pour déclencher la recomposition de l'UI.     */
    val stored by produceState(
        /* initialValue est initialement une liste vide, lorsque Room n'a pas encore émis de valeur,
        permet d'éviter null. */
        initialValue = emptyList<AppNotificationEntity>()
    ) {
        /* Récupère le singleton du repository (et donc de la DB) en utilisant le context
        .observeAll renvoie un Flow qui émet une nouvelle liste à chaque changement DB.
        .collect {...} s'abonne au Flow.
         value est la valeur interne du State(=une valeur observable par l'UI) créé par produceState.
         it est la nouvelle liste émise par le Flow.*/
        AppNotificationsRepository.get(context)
            .observeAll()
            .collect {value = it}
    }

    // Notifs triées par date décroissante.
    val slotStarts = stored.filter {it.type ==  "SLOT_START"}.sortedByDescending {it.timestamp}
    val latestSlot = slotStarts.firstOrNull() // Renvoie le premier élément si liste non vide.
    // Enlève le premier élément si plus d'une notif, pour afficher toutes les anciennes dans un bloc déroulable.
    val olderSlots = if (slotStarts.size <= 1) emptyList() else slotStarts.drop(1)
    // On garde les notif qui ne sont pas début de créneau.
    val others = stored.filter {it.type != "SLOT_START"}

    Scaffold(
        topBar = { CenterAlignedTopAppBar(title = { Text("Notifications") }) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Plus récente notif de créneau, affichée entièrement:
            item{
                latestSlot?.let { slot -> // let vérifie que latestSlot n'est pas null
                    AppNotifCard(
                        title = slot.title,
                        text = slot.texte
                    )
                }
            }

            // Anciennes notifs de créneau regroupées dans un bloc déroulable:
            if (olderSlots.isNotEmpty()) {
                item(key = "slotStartGroup"){
                    SlotStartGroup((olderSlots))
                }
            }

            // Autres notids de l'app:
            /* key pour donner une identité stable à chaque ligne UI.
            key définie dans AppNotificationEntity. */
            items(others, key = {it.key}) { n->
                AppNotifCard(
                    title = n.title,
                    text = n.texte
                )
            }

            // Notifications social, provenant de firestore
            items(notifications, key = { it.id }) { n ->
                val title = when (n.type) {
                    "follow" -> "Quelqu’un s’est abonné à vous"
                    "comment" -> "Nouveau commentaire sur votre calendrier"
                    "publish" -> "Votre calendrier a été mis en ligne"
                    else -> "Notification"
                }
                ElevatedCard {
                    Column(Modifier.padding(12.dp)) {
                        Text(title, style = MaterialTheme.typography.titleMedium)
                        if (n.type == "comment" && n.calendarId != null) {
                            Text("Calendrier : ${n.calendarId}")
                        }
                    }
                }
                if (n.type == "publish" && n.calendarId != null) {
                    Text("Calendrier : ${n.calendarId}")
                }
            }
        }
    }
}

object NotificationsRepository {
    private val db = FirebaseFirestore.getInstance()

    fun observeNotifications(uid: String) = callbackFlow<List<AppNotification>> {
        val ref = db.collection("users").document(uid)
            .collection("notifications")
            .orderBy("timestamp", Query.Direction.DESCENDING)

        val reg = ref.addSnapshotListener { snap, err ->
            if (err != null) { trySend(emptyList()); return@addSnapshotListener }
            val list = snap?.documents?.map { d ->
                AppNotification(
                    id = d.id,
                    type = d.getString("type") ?: "",
                    fromUid = d.getString("fromUid"),
                    calendarId = d.getString("calendarId"),
                    itemId = d.getString("itemId"),
                    /* getTimestamp('timestamp") récupère un com.google.firebase.Timestamp
                    toDate() convertit en java.util.Date
                    time convertit en Long (millisecondes)
                    ?: fallback(càd. valeur par défaut, plan B) si absent. */
                    timestamp = d.getTimestamp("timestamp")?.toDate()?.time ?: 0L
                )
            } ?: emptyList()
            trySend(list).isSuccess
        }
        awaitClose { reg.remove() }
    }
}

// Icône identique "social":
@Composable // Composable Compose, peut "dessiner" l'UI.
private fun AppNotifCard(title: String, text: String) {
    ElevatedCard { /* Carte material3 avec ombre légère= conteneur propre pour chaque notif
    Card en Compose est un composant visuel pour regrouper informations dans un bloc séparé.*/
        Row( // Met les éléments suivants sur une ligne: icône, texte
            modifier = Modifier.padding(12.dp), // Marge sur tout les bords.
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                painter = painterResource(id = R.drawable.ic_glitch_foreground),
                contentDescription = "App icon",
                modifier = Modifier.size(40.dp)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium
                )
                if (text.isNotBlank()) {
                    Text(
                        text,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}

// Bloc groupé déroulable + bouton replier:
@Composable
private fun SlotStartGroup(older: List<Notifications.AppNotificationEntity>) {
    /* expanded = état UI (déroulé ou non).
    * remember pour que Compose conserve la valeur tant que le composable reste à l'écran,
      évite recompositions intempestives.
    * mutableStateOf() est une variable observée par Compose, qui va donc recomposer le
      composant dès qu'un changement est opéré.
    * by permet la délégation, évite de devoir écrire tout ceci :
        val expandedState = remember { mutableStateOf(false) }
        val expanded = expandedState.value
        expandedState.value = true
        */
    var expanded by androidx.compose.runtime.remember {androidx.compose.runtime.mutableStateOf(false)}

    androidx.compose.material3.ElevatedCard(
        onClick = {expanded = !expanded} // Carte cliquable, retournes l'"état de repliage" inverse.
    ) {
        androidx.compose.foundation.layout.Column(
            modifier = androidx.compose.ui.Modifier.padding(12.dp)
        ) {
            androidx.compose.material3.Text( "Calendrier (${older.size})")
            if (expanded) { // Si déroulé, montre la liste + bouton replier.
                /* Créer un espace vide vertical de 8 dp ("density-independent pixels") entre les éléments de la colonne.
                Spacer occupe de la place dans la mise en page sans rien dessiner (ici hauteur fixe de 8dp). */
                androidx.compose.foundation.layout.Spacer(modifier = androidx.compose.ui.Modifier.height(8.dp))

                older.forEach { n -> // Réutilise la même UI pour toutes les anciennes notifs.
                    AppNotifCard(title = n.title, text = n.texte)
                    androidx.compose.foundation.layout.Spacer(modifier = androidx.compose.ui.Modifier.height(8.dp))
                }

                androidx.compose.material3.Button(onClick = {expanded = false}) {
                    androidx.compose.material3.Text("Replier")
                }
            } else {
                androidx.compose.material3.Text("Appuie pour dérouler")
            }
        }
    }
}
