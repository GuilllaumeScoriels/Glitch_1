package social

import androidx.compose.runtime.*
import androidx.compose.material3.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import androidx.navigation.NavController
import planner.AccueilAction
import planner.goHome
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import your.pkg.ui.navigation.Routes
import androidx.compose.foundation.clickable
import kotlinx.coroutines.flow.collectLatest
import profile.PublicCalendar
import profile.UserProfileService
import social.NotificationsRepository.observeNotifications

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedScreen(navController: NavController) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val seenFollowNotifIds = remember { mutableStateListOf<String>() }
    var uid by remember { mutableStateOf<String?>(null) }

    var loading by remember { mutableStateOf(true) }
    var feed by remember { mutableStateOf(listOf<PublicCalendar>()) }

    LaunchedEffect(Unit) {
        Auth.init(context)
        feed = RemoteCalendarService.feed()
        loading = false
        uid = Auth.ensureSignedIn()
    }

    LaunchedEffect(uid) {
        val myUid = uid ?: return@LaunchedEffect
        observeNotifications(myUid).collectLatest { notifs ->
            // Ne traiter que les notifications "follow" jamais vues dans cette session
            val newFollows = notifs.filter { it.type == "follow" && !seenFollowNotifIds.contains(it.id) }
            for (n in newFollows) {
                seenFollowNotifIds.add(n.id)
                val follower = n.fromUid
                // Récupérer un pseudo sympathique si possible
                val pseudo = try {
                    if (follower != null) UserProfileService.getPseudoForUid(follower) else null
                } catch (_: Throwable) { null }
                val who = pseudo ?: follower ?: "Un utilisateur"
                snackbarHostState.showSnackbar("$who s’est abonné à vous")
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Flux") },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            // Revenir à l'écran précédent (typiquement SocialScreen)
                            val popped = navController.popBackStack()

                            // Fallback (optionnel) : si l'historique est vide, on force la nav vers SocialScreen.
                            // Remplace Routes.SOCIAL par ta route réelle si elle diffère.
                            if (!popped) {
                                navController.navigate(Routes.SOCIAL) {
                                    launchSingleTop = true
                                    restoreState = true
                                    popUpTo(Routes.SOCIAL) { inclusive = false }
                                }
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Retour"
                        )
                    }
                },
                actions = {
                    AccueilAction { navController.goHome() }
                }
            )
    }) { pad ->
        if (loading) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(pad),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                Modifier
                    .fillMaxSize()
                    .padding(pad)
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(feed) { c ->
                    ElevatedCard(
                        modifier = Modifier.clickable { navController.navigate(your.pkg.ui.navigation.Routes.PUBLIC_CALENDAR + "/${c.id}") }
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Text(c.title, style = MaterialTheme.typography.titleMedium)
                            Text("Complétude : ${(c.completion * 100).toInt()}% • ${c.itemCount} créneaux")
                            Spacer(Modifier.height(8.dp))
                            var likeCount by remember(c.id) { mutableStateOf(c.likesCount) }
                            Button(onClick = {
                                scope.launch {
                                    val (_, newCount) = RemoteCalendarService.toggleLike(c.id)
                                    if (newCount >= 0) likeCount = newCount
                                }
                            }) {
                                Text("J’aime ($likeCount)")
                            }
                        }
                    }
                }
            }
        }
    }
}
