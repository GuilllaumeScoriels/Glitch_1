package social

import androidx.compose.runtime.*
import androidx.compose.material3.*
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import androidx.navigation.NavController
import planner.AccueilAction
import planner.goHome
import your.pkg.ui.navigation.Routes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.RssFeed
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.material3.Divider
import androidx.compose.ui.Alignment
import androidx.compose.material.icons.filled.AccountCircle
import android.net.Uri
import profile.UserProfileService

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SocialScreen(navController: NavController) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var authReady by remember { mutableStateOf(false) }

    var followingUids by remember { mutableStateOf<List<String>>(emptyList()) }
    var uidToPseudo by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var searchFollow by remember { mutableStateOf("") }

    // Init Firebase + connexion anonyme si besoin
    LaunchedEffect(Unit) {
        Auth.init(ctx)
        Auth.ensureSignedIn()
        authReady = true
    }

    // État pour "Mon pseudo"
    var myPseudo by remember { mutableStateOf<String?>(null) }
    var editPseudo by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    val snack = remember { SnackbarHostState() }

    // État pour abonnements
    var followInput by remember { mutableStateOf("") } // pseudo à suivre

    // Charge pseudo courant + abonnements : d'abord le cache local, puis le réseau
    LaunchedEffect(ctx, authReady) {
        // 1) Cache local (affichage immédiat, même hors-ligne)
        val localUids = FollowStore.getFollowing(ctx)
        val localPseudos = FollowStore.getPseudos(ctx)
        if (localUids.isNotEmpty()) followingUids = localUids
        if (localPseudos.isNotEmpty()) uidToPseudo = localPseudos

        if (authReady) {
            myPseudo = UserProfileService.getMyPseudo()

            val uids = RemoteCalendarService.listFollowing()
            if (uids.isNotEmpty()) {
                // Mettre à jour l’UI et le cache UNIQUEMENT si le serveur renvoie quelque chose
                followingUids = uids
                FollowStore.setFollowing(ctx, uids)
            }

            // Récupérer les pseudos pour ce qu’on a à l’écran (serveur ou cache)
            val base = if (uids.isNotEmpty()) uids else followingUids
            val pseudoMap = UserProfileService.getPseudosForUids(base)
            uidToPseudo = uidToPseudo + pseudoMap
            pseudoMap.forEach { (u, p) -> FollowStore.putPseudo(ctx, u, p) }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snack) },
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Abonnements") },
                actions = {
                    IconButton(onClick = { navController.navigate(Routes.FEED) }) {
                        Icon(Icons.Filled.RssFeed, contentDescription = "Flux")
                    }
                    IconButton(onClick = {
                        val uid = Auth.uid
                        if (uid != null) {
                            navController.navigate(Routes.PROFILE + "/${Uri.encode(uid)}")
                        }
                    }) {
                        Icon(Icons.Filled.AccountCircle, contentDescription = "Profil")
                    }
                    AccueilAction { navController.goHome() }
                }
            )
        }
    ) { pad ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(pad)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            // --- Mon pseudo ---------------------------------------------------
            ElevatedCard {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Mon pseudo", style = MaterialTheme.typography.titleMedium)
                    Text(text = myPseudo?.let { "Actuel : $it" } ?: "Aucun défini")
                    OutlinedTextField(
                        value = editPseudo,
                        onValueChange = { editPseudo = it },
                        label = { Text("Nouveau pseudo (3–20, lettres/chiffres _.-)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                scope.launch {
                                    saving = true
                                    val res = UserProfileService.setPseudo(editPseudo)
                                    saving = false
                                    res.fold(
                                        onSuccess = { p ->
                                            myPseudo = p
                                            editPseudo = ""
                                            snack.showSnackbar("Pseudo mis à jour : $p")
                                        },
                                        onFailure = { t ->
                                            snack.showSnackbar(t.message ?: "Échec de la mise à jour du pseudo")
                                        }
                                    )
                                }
                            },
                            enabled = editPseudo.isNotBlank() && !saving
                        ) { Text(if (saving) "En cours…" else "Enregistrer") }
                    }
                }
            }

            // --- S'abonner à quelqu'un par pseudo ----------------------------
            ElevatedCard {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("S'abonner", style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(
                        value = followInput,
                        onValueChange = { followInput = it },
                        label = { Text("Pseudo à suivre") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(
                        onClick = {
                            scope.launch {
                                // --- Handler "S'abonner" (bloc 4.2) ---

                                val input = followInput.trim()
                                if (input.isEmpty()) {
                                    snack.showSnackbar("Veuillez saisir un pseudo")
                                    return@launch
                                }

                                // 1) Résoudre le pseudo saisi → uid
                                val uid = UserProfileService.findUidByPseudo(input) ?: run {
                                    snack.showSnackbar("Pseudo introuvable")
                                    return@launch
                                }
                                if (followingUids.contains(uid)) {
                                    snack.showSnackbar("Déjà abonné")
                                    return@launch
                                }

                                // 2) Sauvegarde des états pour rollback
                                val prevFollowing = followingUids
                                val prevMap = uidToPseudo

                                // 3) ✅ Mise à jour optimiste de l’UI + persistance locale immédiate
                                followingUids = followingUids + uid
                                uidToPseudo = uidToPseudo + (uid to input)
                                FollowStore.addFollowing(ctx, uid)
                                FollowStore.putPseudo(ctx, uid, input)
                                followInput = ""

                                // 4) Réseau + consolidation ou rollback
                                val result = runCatching { RemoteCalendarService.follow(uid) }
                                result.fold(
                                    onSuccess = {
                                        // a) Re-synchroniser depuis le serveur
                                        val server = RemoteCalendarService.listFollowing()
                                        if (server.isNotEmpty()) {
                                            followingUids = server
                                            FollowStore.setFollowing(ctx, server)
                                        }
                                        // sinon : on conserve l’état optimiste + cache local (pas d’écrasement)


                                        // b) Mettre à jour le pseudo canonique si disponible
                                        val canonicalMap = runCatching {
                                            UserProfileService.getPseudosForUids(listOf(uid))
                                        }.getOrNull() ?: emptyMap()
                                        val canonical = canonicalMap[uid] ?: input

                                        uidToPseudo = uidToPseudo + (uid to canonical)
                                        FollowStore.putPseudo(ctx, uid, canonical)

                                        snack.showSnackbar("Abonnement ajouté")
                                    },
                                    onFailure = { e ->
                                        // ❌ Rollback complet si le réseau échoue
                                        followingUids = prevFollowing
                                        uidToPseudo = prevMap
                                        FollowStore.setFollowing(ctx, prevFollowing)
                                        FollowStore.removeFollowing(ctx, uid)
                                        prevMap[uid]?.let { FollowStore.putPseudo(ctx, uid, it) }

                                        snack.showSnackbar(e.message ?: "Échec de l’abonnement")
                                    }
                                )
                            }
                        },
                        enabled = followInput.isNotBlank()
                    ) { Text("S'abonner") }
                }
            }

            // --- Liste des abonnements ---------------------------------------
            ElevatedCard {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Vous suivez :", style = MaterialTheme.typography.titleMedium)
                    if (followingUids.isEmpty()) {
                        Text("Aucun abonnement pour l'instant.")
                    }
                    else {
                        val labels = followingUids.associateWith { uid -> uidToPseudo[uid] ?: uid }
                        OutlinedTextField(
                            value = searchFollow,
                            onValueChange = { searchFollow = it },
                            label = { Text("Rechercher un abonnement") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        val filtered = followingUids.filter { uid ->
                            labels[uid]?.contains(searchFollow, ignoreCase = true) == true
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            filtered.forEach { uid ->
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    val label = labels[uid] ?: uid
                                    Text(label)
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        TextButton(
                                            onClick = { navController.navigate(your.pkg.ui.navigation.Routes.PUBLIC_PROFILE + "/${Uri.encode(uid)}") }
                                        ) { Text("Voir profil") }
                                        TextButton(
                                            onClick = {
                                                scope.launch {
                                                    val prevFollowing = followingUids
                                                    val prevMap = uidToPseudo

                                                    // Optimiste + cache local
                                                    followingUids = followingUids - uid
                                                    uidToPseudo = uidToPseudo - uid
                                                    FollowStore.removeFollowing(ctx, uid)

                                                    val result = runCatching { RemoteCalendarService.unfollow(uid) }
                                                    result.fold(
                                                        onSuccess = {
                                                            val server = RemoteCalendarService.listFollowing()
                                                            if (server.isNotEmpty()) {
                                                                followingUids = server
                                                                FollowStore.setFollowing(ctx, server)
                                                            }
                                                        },
                                                        onFailure = { e ->
                                                            // rollback UI + cache
                                                            followingUids = prevFollowing
                                                            uidToPseudo = prevMap
                                                            FollowStore.setFollowing(ctx, prevFollowing)
                                                            prevMap[uid]?.let { FollowStore.putPseudo(ctx, uid, it) }
                                                            snack.showSnackbar(e.message ?: "Échec du désabonnement")
                                                        }
                                                    )
                                                }
                                            }
                                        ) { Text("Se désabonner") }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FollowingSection(
    navToProfile: (String) -> Unit,
    followingUids: List<String>,
    uidToPseudo: Map<String, String>,
    searchFollow: String,
    onSearchChange: (String) -> Unit
) {
    if (followingUids.isEmpty()) return

    Spacer(Modifier.height(8.dp))
    Divider()
    Spacer(Modifier.height(8.dp))
    Text(
        text = "Abonnements",
        style = MaterialTheme.typography.titleMedium
    )
    Spacer(Modifier.height(8.dp))

    OutlinedTextField(
        value = searchFollow,
        onValueChange = onSearchChange,
        label = { Text("Rechercher un abonnement") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )

    val filtered = remember(followingUids, uidToPseudo, searchFollow) {
        followingUids.filter { uid ->
            val label = uidToPseudo[uid] ?: uid
            label.contains(searchFollow, ignoreCase = true)
        }
    }

    Spacer(Modifier.height(8.dp))

    if (filtered.isEmpty()) {
        Text("Aucun résultat", style = MaterialTheme.typography.bodyMedium)
        return
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        filtered.forEach { uid ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = uidToPseudo[uid] ?: uid,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = { navToProfile(uid) }) {
                    Text("Voir profil")
                }
            }
        }
    }
}
