package planner

import Calendars.CalendarRepository
import Calendars.CalendarSummary
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import java.time.format.DateTimeFormatter
import your.pkg.ui.navigation.Routes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavedCalendarsScreen(
    navController: NavController,
    vm: PlannerViewModel
) {
    val context = LocalContext.current
    val snack = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val mainId by Notifications.MainCalendarStore.mainIdFlow(context).collectAsState(initial = null)

    var list by remember { mutableStateOf(emptyList<CalendarSummary>()) }
    var renamingId by remember { mutableStateOf<String?>(null) }
    var renameText by remember { mutableStateOf(TextFieldValue("")) }

    fun refresh() { list = CalendarRepository.list(context) }

    LaunchedEffect(Unit) { refresh() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Mes calendriers") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Retour")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snack) }
    ) { pad ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(pad),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(list, key = { it.id }) { item ->
                CalendarRow(
                    summary = item,
                    onOpen = {
                        val sc = CalendarRepository.load(context, item.id)
                        if (sc != null) {
                            vm.setItems(sc.items)
                            vm.setCurrentCalendarId(sc.id)
                            scope.launch {
                                // Si le calendrier chargé est aussi le principal, on planifie tout de suite
                                val curMain = Notifications.MainCalendarStore.mainIdFlow(context).first()
                                if (curMain == sc.id) {
                                    Notifications.EventAlarmScheduler.scheduleFor(context, sc.items)
                                }
                            }
                            scope.launch { snack.showSnackbar("Calendrier chargé: ${sc.title}") }
                            navController.navigate(Route.Calendar.route) // ouvre l'écran Result
                        }
                    },
                    onEdit = {
                        val sc = CalendarRepository.load(context, item.id)
                        if (sc != null) {
                            vm.setItems(sc.items)
                            vm.setCurrentCalendarId(sc.id)
                            scope.launch { snack.showSnackbar("Édition du calendrier : ${sc.title}") }
                            navController.navigate(Routes.PLANNER) // ouvre l'écran Planner
                        } else {
                            scope.launch { snack.showSnackbar("Impossible de charger le calendrier.") }
                        }
                    },
                    onRename = {
                        renamingId = item.id
                        renameText = TextFieldValue(item.title)
                    },
                    onDelete = {
                        CalendarRepository.delete(context, item.id)
                        refresh()
                        scope.launch { snack.showSnackbar("Calendrier supprimé") }
                    },
                    // Nouveau : statut « principal » + action pour le définir
                    isMain = (mainId == item.id),
                    onSetMain = {
                        scope.launch {
                            Notifications.MainCalendarStore.setMainId(context, item.id)
                            val sc = CalendarRepository.load(context, item.id)
                            val items = sc?.items ?: emptyList()
                            Notifications.EventAlarmScheduler.scheduleFor(context, items)
                            snack.showSnackbar("Calendrier principal défini")
                        }
                    },
                    // Actions additionnelles injectées (Publier)
                    actions = {
                        IconButton(onClick = {
                            val sc = CalendarRepository.load(context, item.id)
                            if (sc != null) {
                                scope.launch {
                                    try {
                                        social.Auth.init(context)
                                        social.RemoteCalendarService.publishCalendar(sc.id, item.title, sc.items)
                                        snack.showSnackbar("Calendrier publié")
                                    } catch (_: Throwable) {
                                        snack.showSnackbar("Publication impossible (offline ?)")
                                    }
                                }
                            }
                        }) {
                            Icon(Icons.Filled.CloudUpload, contentDescription = "Publier")
                        }
                    }
                )
            }
        }
    }

    if (renamingId != null) {
        AlertDialog(
            onDismissRequest = { renamingId = null },
            confirmButton = {
                TextButton(onClick = {
                    val id = renamingId!!
                    CalendarRepository.rename(context, id, renameText.text.trim())
                    renamingId = null
                    refresh()
                    scope.launch { snack.showSnackbar("Calendrier renommé") }
                }) { Text("Renommer") }
            },
            dismissButton = { TextButton(onClick = { renamingId = null }) { Text("Annuler") } },
            title = { Text("Renommer le calendrier") },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Titre") }
                )
            }
        )
    }
}

@Composable
private fun CalendarRow(
    summary: CalendarSummary,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    isMain: Boolean,
    onSetMain: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {}
) {
    val fmt = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")

    ListItem(
        headlineContent = { Text(summary.title) },
        supportingContent = { Text("${summary.count} créneaux • ${summary.updatedAt.format(fmt)}") },
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpen() },
        trailingContent = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Boutons injectés par le parent (ex : « Publier »)
                actions()

                // Ouvrir dans l'éditeur (Planner)
                IconButton(onClick = onEdit) {
                    Icon(Icons.Filled.Add, contentDescription = "Éditer dans le Planner")
                }
                // Renommer
                IconButton(onClick = onRename) {
                    Icon(Icons.Filled.Edit, contentDescription = "Renommer")
                }
                // Définir comme "principal"
                IconButton(onClick = onSetMain) {
                    Icon(
                        imageVector = if (isMain) Icons.Filled.Star else Icons.Outlined.StarBorder,
                        contentDescription = if (isMain) "Calendrier principal" else "Définir comme principal"
                    )
                }
                // Supprimer
                IconButton(onClick = onDelete) {
                    Icon(Icons.Filled.Delete, contentDescription = "Supprimer")
                }
            }
        }
    )
    Divider()
}
