package planner

import Calendars.UserPublishedCalendarsScreen
import android.app.Application
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.example.a18.FullscreenManager
import com.example.a18.TextFileImporter
import com.example.lecturemotparmotapp.AccueilScreen
import com.example.lecturemotparmotapp.LectureMotParMotScreen
import Lecteurtxt.LectureViewModel
import Lecteurtxt.LectureViewModelFactory
import your.pkg.ui.navigation.Routes
import your.pkg.ui.settings.SettingsScreen
import Settings.SettingsViewModel
import Settings.SettingsViewModelFactory
import Downloads.DownloadsScreen
import Downloads.DownloadsViewModel
import Downloads.DownloadsVmFactory
import social.FeedScreen
import social.SocialScreen
import social.Auth
import profile.UserProfileScreen
import social.RemoteCalendarService
import profile.PublicProfileScreen
import android.net.Uri
import social.NotificationsScreen
import social.PublicCalendarScreen

// Routes déjà définies ailleurs
sealed class Route(val route: String) {
    data object Home : Route("home")
    data object Planner : Route("planner")
    data object Calendar : Route("calendar")
    data object Reader : Route("reader")
    data object SavedCalendars : Route("saved_calendars")
}

@Composable
fun AppNavHost(
    navController: NavHostController,
    fullscreenManager: FullscreenManager
) {
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        try { Auth.init(context); Auth.ensureSignedIn() } catch (_: Throwable) {}
    }

    // Dépendances partagées (inchangées)
    val fileRepo = remember { storage.FileRepositoryImpl(context) }
    val settingsRepo = remember { Settings.DataStoreSettingsRepository(context) }
    val importer = TextFileImporter(context)

    // LectureViewModel partagé (inchangé)
    val lectureVmFactory = LectureViewModelFactory(importer, settingsRepo)
    val lectureVm: LectureViewModel =
        viewModel(factory = lectureVmFactory)

    // VM du planner (inchangé)
    val vm: PlannerViewModel = viewModel()

    NavHost(navController = navController, startDestination = Routes.HOME) {
        composable(Routes.FEED) { FeedScreen(navController) }
        // Profil utilisateur (via UID)
        composable(Routes.NOTIFICATIONS) { NotificationsScreen(navController) }
        composable(your.pkg.ui.navigation.Routes.PROFILE + "/{uid}") { backStackEntry ->
            val uid = backStackEntry.arguments?.getString("uid")?.let(Uri::decode) ?: return@composable
            UserProfileScreen(navController, uid)
        }

// Affichage d'un calendrier public complet par ID
        composable(your.pkg.ui.navigation.Routes.PUBLIC_CALENDAR + "/{id}") { backStackEntry ->
            val id = backStackEntry.arguments?.getString("id") ?: return@composable
            LaunchedEffect(id) {
                val items = RemoteCalendarService.loadItems(id)
                vm.setItems(items)
                vm.setCurrentCalendarId(id)
            }
            CalendarResultScreen(navController, vm)
        }
        composable(Routes.SUBSCRIPTIONS) { SocialScreen(navController) }
        composable(Routes.HOME) {
            AccueilScreen(
                onStartReading = { navController.navigate(Routes.READER) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                onOpenPlanner = { navController.navigate(Routes.PLANNER) },
                onOpenDownloads = { navController.navigate(Routes.DOWNLOADS) },
                onOpenSavedCalendars = { navController.navigate(Route.SavedCalendars.route) },
                onOpenFeed = { navController.navigate(Routes.FEED) },
                onOpenSubscriptions = { navController.navigate(Routes.SUBSCRIPTIONS) },
                onOpenNotifications = { navController.navigate(Routes.NOTIFICATIONS) },
                onOpenProfile = { uid ->
                    navController.navigate(Routes.PROFILE + "/${Uri.encode(uid)}")
                }
            )
        }

        composable(Route.Planner.route) {
            PlannerScreen(navController, vm)
        }

        composable(Route.Calendar.route) {
            CalendarResultScreen(navController, vm)
        }

        composable(Route.Reader.route) {
            val injected = navController.previousBackStackEntry
                ?.savedStateHandle
                ?.get<String>("initialText")

            LaunchedEffect(injected) {
                if (!injected.isNullOrEmpty()) {
                    // ✅ Remplacement de Lecteurtxt.setExternalText(...) par le helper tolérant :
                    tryInjectTextIntoLectureVm(lectureVm, injected)
                    navController.previousBackStackEntry
                        ?.savedStateHandle
                        ?.remove<String>("initialText")
                }
            }

            LectureMotParMotScreen(
                vm = lectureVm,
                fullscreenManager = fullscreenManager,
                fileRepo = fileRepo,
                onHome = { navController.navigate(your.pkg.ui.navigation.Routes.HOME) }
            )
        }

        composable(Route.SavedCalendars.route) {
            SavedCalendarsScreen(navController = navController, vm = vm)
        }


        composable(Routes.SETTINGS) {
            val settingsVm: SettingsViewModel = viewModel(
                factory = SettingsViewModelFactory(context)
            )
            SettingsScreen(
                onBack = { navController.popBackStack() },
                vm = settingsVm
            )
        }

        composable(Routes.DOWNLOADS) {
            val downloadsVm: DownloadsViewModel =
                viewModel(factory = DownloadsVmFactory(context))

            val deleteConsentLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.StartIntentSenderForResult()
            ) { result ->
                val confirmed = (result.resultCode == android.app.Activity.RESULT_OK)
                downloadsVm.onDeleteConsentResult(context, confirmed)
                if (!confirmed) {
                    Toast.makeText(context, "Suppression annulée", Toast.LENGTH_SHORT).show()
                }
            }

            DownloadsScreen(
                viewModel = downloadsVm,
                onOpenText = { text ->
                    navController.currentBackStackEntry
                        ?.savedStateHandle
                        ?.set("initialText", text)
                    navController.navigate(Routes.READER)
                },
                onRequestDeleteConsent = { req ->
                    deleteConsentLauncher.launch(req)
                },
                onHome = { navController.goHome() }

            )
        }
        composable(Routes.PUBLIC_PROFILE + "/{userId}") { backStackEntry ->
            val uid = backStackEntry.arguments?.getString("userId")?.let(Uri::decode) ?: return@composable
            PublicProfileScreen(
                userId = uid,
                onBack = { navController.popBackStack() },
                onOpenCalendar = { id, title ->
                    navController.navigate(
                        Routes.PUBLIC_CALENDAR + "/" + android.net.Uri.encode(id) + "/" + android.net.Uri.encode(title)
                    )
                }
            )
        }
        composable(Routes.USER_PUBLISHED_CALENDARS) {
            UserPublishedCalendarsScreen(
                onBack = { navController.popBackStack() },
                onOpenCalendar = { id, title ->
                    navController.navigate(
                        Routes.PUBLIC_CALENDAR + "/" + android.net.Uri.encode(id) + "/" + android.net.Uri.encode(title)
                    )
                }
            )
        }
        composable(Routes.PUBLIC_CALENDAR + "/{calendarId}/{title}") { backStackEntry ->
            val calendarId = backStackEntry.arguments?.getString("calendarId")?.let(android.net.Uri::decode) ?: return@composable
            val title = backStackEntry.arguments?.getString("title")?.let(android.net.Uri::decode) ?: "Calendrier"
            PublicCalendarScreen(
                calendarId = calendarId,
                title = title,
                onBack = { navController.popBackStack() }
            )
        }
    }
}

/**
 * Pont qui connecte l'écran Téléchargements au graphe de navigation
 * sans modifier l’UI : on écoute deux clés SavedStateHandle sur la
 * backStackEntry actuelle :
 *  - "openText" (String)         -> ouvre l’écran Lecture et pré-remplit le champ
 *  - "deleteConsent" (IntentSenderRequest) -> lance le dialogue système de suppression
 *
 * Côté DownloadsScreen, il suffit de poser :
 *   navController.currentBackStackEntry?.savedStateHandle?.set("openText", texte)
 *   navController.currentBackStackEntry?.savedStateHandle?.set("deleteConsent", req)
 */
@Composable
private fun DownloadsRouteBridge(
    navController: NavHostController,
    downloadsVm: DownloadsViewModel
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val ctx = LocalContext.current

    // Lanceur pour la demande de suppression (API 30+)
    val deleteConsentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            runCatching { downloadsVm.load(ctx) }
        }
    }

    // Observateurs sur la SavedStateHandle de la destination actuelle (Downloads)
    val s = navController.currentBackStackEntry?.savedStateHandle

    DisposableEffect(s, lifecycleOwner) {
        if (s == null) return@DisposableEffect onDispose { }

        // 1) Ouverture de texte : "openText"
        val openObserver = Observer<String> { text ->
            if (!text.isNullOrEmpty()) {
                navController.currentBackStackEntry
                    ?.savedStateHandle
                    ?.set("initialText", text)
                navController.navigate(Routes.READER)
                s.remove<String>("openText")
            }
        }
        s.getLiveData<String>("openText").observe(lifecycleOwner, openObserver)

        // 2) Consentement suppression : "deleteConsent"
        val consentObserver = Observer<IntentSenderRequest> { req ->
            deleteConsentLauncher.launch(req)
        }
        s.getLiveData<IntentSenderRequest>("deleteConsent").observe(lifecycleOwner, consentObserver)

        onDispose {
            s.getLiveData<String>("openText").removeObserver(openObserver)
            s.getLiveData<IntentSenderRequest>("deleteConsent").removeObserver(consentObserver)
        }
    }
}

/**
 * Injection tolérante de texte dans le ViewModel de lecture.
 * N’exige aucun changement d’API : on essaie plusieurs méthodes usuelles
 * par réflexion. Si aucune n’existe, on n’altère pas le comportement.
 *
 * Méthodes tentées, dans l’ordre :
 *  - setExternalText(String)
 *  - setInputText(String)
 *  - setText(String)
 *  - replaceText(String)
 */
private fun tryInjectTextIntoLectureVm(lectureVm: Any, text: String) {
    val candidates = listOf("setExternalText", "setInputText", "setText", "replaceText")
    for (name in candidates) {
        runCatching {
            val m = lectureVm.javaClass.methods.firstOrNull {
                it.name == name && it.parameterTypes.contentEquals(arrayOf(String::class.java))
            }
            if (m != null) {
                m.isAccessible = true
                m.invoke(lectureVm, text)
                return
            }
        }
    }
}
