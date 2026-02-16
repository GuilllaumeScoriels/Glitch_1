package social

import androidx.compose.runtime.*
import androidx.compose.material3.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.tasks.await
import androidx.compose.ui.platform.LocalContext

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
        Notifications.UnreadBadgeStore.reset(ctx)
    }

    val notifications by produceState(initialValue = emptyList<AppNotification>(), uid) {
        if (uid == null) return@produceState
        NotificationsRepository.observeNotifications(uid!!).collectLatest { value = it }
    }

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
                    timestamp = d.getLong("timestamp") ?: 0L
                )
            } ?: emptyList()
            trySend(list).isSuccess
        }
        awaitClose { reg.remove() }
    }
}
