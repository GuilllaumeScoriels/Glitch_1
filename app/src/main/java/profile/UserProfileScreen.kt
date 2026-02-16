package profile
/**
 * UserProfileScreen
 *
 * Ce fichier définit l’écran de profil utilisateur à l’aide de Jetpack Compose.
 * En Jetpack Compose, une UI n'est pas une classe ou un layout XML mais une fonction composable,
 * On ne construit pas l'interface, mais on la décrit. x
 *
 * Contrairement aux approches classiques basées sur des layouts XML et des
 * Activities/Fragments, l’interface utilisateur est ici décrite par des
 * fonctions composables. Une fonction composable représente l’état visuel
 * de l’écran pour un état donné de l’application.
 *
 * UserProfileScreen est volontairement implémentée comme une fonction :
 * - Elle ne contient aucune logique métier
 * - Elle ne gère pas la source des données
 * - Elle reçoit toutes les informations nécessaires via ses paramètres
 *
 * Cela permet :
 * - Une séparation claire des responsabilités (POO)
 * - Une meilleure testabilité
 * - Une recomposition automatique lorsque l’état change
 *
 * Le rôle de cet écran est uniquement de :
 * - Afficher les informations du profil utilisateur
 * - Réagir aux actions utilisateur via des callbacks
 *
 * Toute la logique applicative (chargement du profil, mises à jour, navigation)
 * est déléguée au ViewModel ou aux couches supérieures de l’architecture.
 *
 * En résumé, cet écran est une représentation déclarative de l’état utilisateur,
 * conforme aux principes de Jetpack Compose et de l’architecture moderne Android.
 */

import android.annotation.SuppressLint
import androidx.compose.runtime.*
import androidx.compose.material3.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import kotlinx.coroutines.launch
import planner.AccueilAction
import planner.goHome
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import your.pkg.ui.navigation.Routes
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.activity.compose.rememberLauncherForActivityResult
import android.net.Uri
import androidx.compose.material.icons.filled.InsertChart
import androidx.compose.ui.platform.LocalContext
import Calendars.CalendarStorage
import android.content.Intent
import android.util.Log
import java.time.LocalDate
import kotlinx.coroutines.flow.collectLatest
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import data.UserReader
import social.Auth
import social.RemoteCalendarService
import coil.compose.AsyncImage
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Job
import kotlinx.coroutines.tasks.await

@SuppressLint("RememberReturnType")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserProfileScreen(navController: NavController, userId: String) {
    var selfUid by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        selfUid = Auth.ensureSignedIn()   // coroutine
    }

    val myUserFlow = remember(selfUid) { selfUid?.let { UserReader.listenUser(it) } }
    val myUserState = myUserFlow?.collectAsStateWithLifecycle(initialValue = null)
    val myUser = myUserState?.value?.getOrNull()

    val isSelf = selfUid == userId

    val scope = rememberCoroutineScope()

    var showCalendars by rememberSaveable { mutableStateOf(false) }
    var showOccupancy by rememberSaveable { mutableStateOf(false) }

    var email by rememberSaveable { mutableStateOf("") }
    var followers by remember { mutableStateOf<List<Pair<String, String?>>>(emptyList()) }

    val ctx = LocalContext.current
    var photoUrl by remember { mutableStateOf<String?>(null) }

    var uploadJob: Job? = null
    // Sélecteur d'image moderne (Photo Picker). Pas de permission lecture requise.
    val pickPhoto = rememberLauncherForActivityResult( // Launcher ouvre le sélecteur de photos de l'appareil
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri -> //URI= Uniform Resource Identifier = adresse abstraite
        Log.d("PROFILE", "URI selected = $uri")
        /* Ecrit un message dans le logcat
        (=outil de debug android), au niveau debug (d), avec le TAG "PROFILE",
        avec comme message l'URI de l'image sélectionnée pour vérifier que le photo picker fonctionne,
        que le callback (= fonction donnée à android, appelée plus tard quand un événement se réalise
         est appelé et que l'utilisateur a bien sélectionné une image*/
        if (uri != null) { // Signifie qu'une image a été sélectionnée par l'utilisateur
            uploadJob = scope.launch { // Lance une tâche synchrone avec l'endroit qui attend uploadJob, à enlever après le test sur l'importation de la photo
                Log.d("PROFILE", "Starting upload")
                val r = UserProfileService.setProfilePhoto(uri)
                r.onSuccess { url ->
                    Log.d("PROFILE", "uploaded URL = $url")
                    photoUrl = url
                } /* Déclenche la recomposition UI
                car Compose détecte un changement et recompose */
                r.onFailure { e ->
                    Log.e("PROFILE", "Upload failed: ${e.message}", e)
                    /* Affiche l'erreur dans le logcat au niveau erreur si l'upload échoue
                    Va renvoyer un mot clé sur l'erreur et une stacktrace (at ...) qui indique
                    où apparait exactement l'erreur.
                    Quelle exception ? Quel message ? Quel fichier ? Quelle ressource ?
                     */
                }
            }
        }
    }

    fun computeNextDaysOccupancy(
        context: android.content.Context,
        days: Int = 7
    ): List<Pair<LocalDate, Double>> {
        val items = CalendarStorage.load(context)
        val today = LocalDate.now()
        return (0 until days).map { d ->
            val day = today.plusDays(d.toLong())
            val minutes = items.filter { it.start.toLocalDate() == day }
                .sumOf { it.durationMinutes }
            val ratio = minutes.toDouble() / (24 * 60).toDouble() // simple : base 24h
            day to ratio.coerceIn(0.0, 1.0)
        }
    }

    var pseudo by remember { mutableStateOf<String?>(null) }
    var calendars by remember { mutableStateOf(listOf<PublicCalendar>()) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(userId) {
        // Chargements ponctuels non bloquants
        try {
            email = UserProfileService.getMyEmail() ?: ""
        } catch (_: Throwable) { /* no-op */ }
    }

    LaunchedEffect(userId) {
        try {
            pseudo = runCatching { UserProfileService.getPseudoForUid(userId) }.getOrNull()
            calendars = runCatching { RemoteCalendarService.calendarsOfUser(userId) }.getOrElse { emptyList() }
        } catch (_: Throwable) {
            // Valeurs de repli en cas d’erreur inattendue
            pseudo = null
            calendars = emptyList()
        } finally {
            loading = false
        }
    }

    // Charger la photo initiale depuis Firestore
    LaunchedEffect(userId) {
        uploadJob?.join() // attend la fin de l'upload, à enlever après fonctionnement test sur l'importation de la photo
        runCatching { UserProfileService.getPhotoUrl(userId) }
            .onSuccess { url -> photoUrl = url }
    }

    LaunchedEffect(userId) {
        // Flux temps réel des abonnés ; n'impacte pas 'loading'
        try {
            RemoteCalendarService.observeFollowers(userId).collectLatest { ids ->
                val pseudoMap = runCatching { UserProfileService.getPseudosForUids(ids) }.getOrNull()
                followers = ids.map { uid -> uid to (pseudoMap?.get(uid) ?: uid) }
            }
        } catch (_: Throwable) {
            // Si erreur réseau, on n'empêche pas l'écran de s'afficher
            followers = emptyList()
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(pseudo?.let { "@$it" } ?: "Profil") },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Retour"
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            // Navigation vers la liste des calendriers publiés par l'utilisateur
                            navController.navigate(Routes.USER_PUBLISHED_CALENDARS)
                            // ou navController.navigate(Screen.UserPublishedCalendars.route)
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.CalendarToday, // gardez votre icône actuelle
                            contentDescription = "Mes calendriers publiés"
                        )
                    }
                    IconButton(onClick = { showOccupancy = !showOccupancy }) {
                        Icon(Icons.Filled.InsertChart, contentDescription = "Occupation")
                    }
                    AccueilAction { navController.goHome() }
                }
            )
        }
    ) { pad ->
        if (loading) {
            Box(
                modifier = Modifier.fillMaxSize().padding(pad),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(pad)
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // --- Photo de profil ------------------------------------------
                item {
                    ElevatedCard {
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            if (photoUrl.isNullOrBlank()) {
                                Icon(
                                    imageVector = Icons.Outlined.AccountCircle,
                                    contentDescription = "Photo de profil",
                                    modifier = Modifier.size(96.dp)
                                )
                            } else {
                                AsyncImage(
                                    model = photoUrl,
                                    contentDescription = "Photo de profil",
                                    contentScale = ContentScale.Crop, // Limite la taille du disque à la taille de la photo
                                    modifier = Modifier
                                        .size(96.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                )
                            }
                            if (isSelf) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(
                                        onClick = {
                                            pickPhoto.launch(
                                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                            )
                                        }
                                    ) {
                                        Text("Changer la photo")
                                    }
                                    if (!photoUrl.isNullOrBlank()) {
                                        OutlinedButton(
                                            onClick = {
                                                scope.launch {
                                                    UserProfileService.removeProfilePhoto()
                                                        .onSuccess { photoUrl = null }
                                                }
                                            }
                                        ) {
                                            Text("Supprimer")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // --- Pseudo ---------------------------------------------------
                item {
                    ElevatedCard {
                        Column(
                            Modifier.fillMaxWidth().padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("Pseudo", style = MaterialTheme.typography.titleMedium)
                            var newPseudo by rememberSaveable { mutableStateOf("") }
                            if (isSelf) {
                                OutlinedTextField(
                                    value = newPseudo,
                                    onValueChange = { newPseudo = it },
                                    label = { Text("Nouveau pseudo (3–20, lettres/chiffres _.-)") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            } else {
                                Text("pseudo")
                            }
                            if (isSelf) {
                                Button(
                                    onClick = {
                                        scope.launch {
                                            val r = UserProfileService.setPseudo(newPseudo.trim())
                                            // Optionnel : snackbar/toast de succès/erreur
                                        }
                                    },
                                    enabled = newPseudo.isNotBlank()
                                ) { Text("Enregistrer") }
                            }
                        }
                    }
                }

                // --- E-mail ---------------------------------------------------
                item {
                    ElevatedCard {
                        Column(
                            Modifier.fillMaxWidth().padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("E-mail", style = MaterialTheme.typography.titleMedium)
                            OutlinedTextField(
                                value = email,
                                onValueChange = { email = it },
                                label = { Text("Adresse e-mail") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            if (isSelf) {
                                Button(onClick = {
                                    scope.launch { UserProfileService.setEmail(email.trim()) }
                                }) { Text("Mettre à jour") }
                                Text(
                                    "Remarque : la mise à jour d’e-mail peut nécessiter une connexion récente (les comptes anonymes doivent être liés).",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }

                // --- Abonnés (followers) -------------------------------------
                item {
                    ElevatedCard {
                        Column(
                            Modifier.fillMaxWidth().padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text("Abonnés", style = MaterialTheme.typography.titleMedium)
                            if (followers.isEmpty()) {
                                Text("Aucun abonné.")
                            } else {
                                Text("${followers.size} abonné(s)", style = MaterialTheme.typography.bodyMedium)
                                followers.forEach { (uid, pseudo) ->
                                    ListItem(
                                        headlineContent = { Text(pseudo ?: uid) },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { navController.navigate("public_profile/${Uri.encode(uid)}") }
                                    )
                                    Divider()
                                }
                            }
                        }
                    }
                }

                // --- Occupation (7 jours) : masqué par défaut, icône pour afficher
                if (showOccupancy) {
                    item {
                        val occ = remember {
                            computeNextDaysOccupancy(ctx, days = 7)
                        }
                        ElevatedCard {
                            Column(
                                Modifier.fillMaxWidth().padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text("Taux d’occupation (7 prochains jours)", style = MaterialTheme.typography.titleMedium)
                                occ.forEach { (day, ratio) ->
                                    Text("${day} : ${(ratio * 100).toInt()}%")
                                }
                            }
                        }
                    }
                }

                if (showCalendars) {
                    items(calendars) { c ->
                        ElevatedCard(
                            onClick = { navController.navigate(Routes.PUBLIC_CALENDAR + "/${c.id}") }
                        ) {
                            Column(Modifier.padding(12.dp)) {
                                Text(c.title, style = MaterialTheme.typography.titleMedium)
                                Text("Complétude : ${(c.completion * 100).toInt()}% • ${c.itemCount} créneaux")
                            }
                        }
                    }
                }
            }
        }
    }
}
