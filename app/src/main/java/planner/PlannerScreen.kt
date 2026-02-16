package planner

import Calendars.CalendarRepository
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import kotlinx.coroutines.launch
import java.time.*
import java.time.format.DateTimeFormatter
import androidx.compose.material.icons.filled.Close
import androidx.compose.ui.text.style.TextOverflow
import Settings.DataStoreSettingsRepository
import androidx.compose.foundation.background
import kotlinx.coroutines.flow.first
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.Divider
import androidx.compose.ui.text.style.TextAlign

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PlannerScreen(
    navController: NavController,
    vm: PlannerViewModel
) {
    val context = LocalContext.current
    val snack = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val state by vm.form.collectAsState()
    val items by vm.items.collectAsState()
    val editing by vm.editingItem.collectAsState()

    val grouped = remember(items) { items.groupBy { it.start.toLocalDate() }.toSortedMap() }

    // Etats du formulaire
    var startDate by remember { mutableStateOf(state.startDate) }
    var endDate by remember { mutableStateOf(state.endDate) }
    var dayStart by remember { mutableStateOf(state.dayStart) }
    var dayEnd by remember { mutableStateOf(state.dayEnd) }
    var slotMinutes by remember { mutableStateOf(state.slotMinutes.toString()) }
    var breakEverySlots by remember { mutableStateOf(state.breakEverySlots.toString()) }
    var breakMinutes by remember { mutableStateOf(state.breakMinutes.toString()) }

    val selectedFiles by vm.selectedFiles.collectAsState()
    var showFilePicker by remember { mutableStateOf(false) }
    var sheetOpen by remember { mutableStateOf(false) }
    var sheetTarget by remember { mutableStateOf<PlannedFile?>(null) }

    var mon by remember { mutableStateOf(state.workDays.contains(DayOfWeek.MONDAY)) }
    var tue by remember { mutableStateOf(state.workDays.contains(DayOfWeek.TUESDAY)) }
    var wed by remember { mutableStateOf(state.workDays.contains(DayOfWeek.WEDNESDAY)) }
    var thu by remember { mutableStateOf(state.workDays.contains(DayOfWeek.THURSDAY)) }
    var fri by remember { mutableStateOf(state.workDays.contains(DayOfWeek.FRIDAY)) }
    var sat by remember { mutableStateOf(state.workDays.contains(DayOfWeek.SATURDAY)) }
    var sun by remember { mutableStateOf(state.workDays.contains(DayOfWeek.SUNDAY)) }

    val workDays: Set<DayOfWeek> = buildSet {
        if (mon) add(DayOfWeek.MONDAY)
        if (tue) add(DayOfWeek.TUESDAY)
        if (wed) add(DayOfWeek.WEDNESDAY)
        if (thu) add(DayOfWeek.THURSDAY)
        if (fri) add(DayOfWeek.FRIDAY)
        if (sat) add(DayOfWeek.SATURDAY)
        if (sun) add(DayOfWeek.SUNDAY)
    }

    fun toIntSafe(s: String, fallback: Int) = s.toIntOrNull() ?: fallback

    // Boîte "Sauver sous…"
    var showSaveDialog by remember { mutableStateOf(false) }
    var saveTitle by remember { mutableStateOf(TextFieldValue("")) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Paramètres du planning") },
                actions = {
                    // Sauver sous...
                    IconButton(onClick = { showSaveDialog = true }) {
                        Icon(Icons.Filled.Save, contentDescription = "Sauver sous…")
                    }
                    // Aller à "Mes calendriers"
                    IconButton(onClick = { navController.navigate(Route.SavedCalendars.route) }) {
                        Icon(Icons.Filled.Folder, contentDescription = "Mes calendriers")
                    }
                    AccueilAction { navController.goHome() }
                }
            )
        },
        snackbarHost = { SnackbarHost(snack) }
    ) { pad ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(pad),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(vertical = 6.dp, horizontal = 8.dp)
        ) {
            // === Formulaire de configuration ===
            item {
                DateRow("Date de début", startDate) { startDate = it }
                DateRow("Date de fin", endDate) { endDate = it }
                TimeRow("Début de journée", dayStart) { dayStart = it }
                TimeRow("Fin de journée", dayEnd) { dayEnd = it }

                OutlinedTextField(
                    value = slotMinutes,
                    onValueChange = { slotMinutes = it.filter { c -> c.isDigit() } },
                    label = { Text("Durée d'un créneau (min)") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = breakEverySlots,
                    onValueChange = { breakEverySlots = it.filter { c -> c.isDigit() } },
                    label = { Text("Pause toutes les N séances (0 = jamais)") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = breakMinutes,
                    onValueChange = { breakMinutes = it.filter { c -> c.isDigit() } },
                    label = { Text("Durée des pauses (min)") },
                    singleLine = true
                )

                Text("Jours de travail")
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DayChip("Lun", mon) { mon = !mon }
                    DayChip("Mar", tue) { tue = !tue }
                    DayChip("Mer", wed) { wed = !wed }
                    DayChip("Jeu", thu) { thu = !thu }
                    DayChip("Ven", fri) { fri = !fri }
                    DayChip("Sam", sat) { sat = !sat }
                    DayChip("Dim", sun) { sun = !sun }
                }

                // --- Fichiers à planifier ---
                Text("Fichiers à planifier", style = MaterialTheme.typography.titleSmall)
                if (selectedFiles.isEmpty()) {
                    Text(
                        "Aucun fichier sélectionné.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                } else {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        selectedFiles.forEach { pf ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = pf.displayName,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodyMedium
                                )

                                Text(
                                    text = "${pf.durationMinutes} min · diff ${pf.difficulty} · imp ${pf.importance}",
                                    maxLines = 1,
                                    style = MaterialTheme.typography.bodySmall
                                )

                                // Bouton ⋮ pour ouvrir la page flottante des critères
                                IconButton(onClick = {
                                    sheetTarget = pf
                                    sheetOpen = true
                                }) {
                                    Icon(Icons.Filled.MoreVert, contentDescription = "Paramètres")
                                }

                                // Bouton de suppression (inchangé)
                                IconButton(onClick = { vm.removePlannedFile(pf.displayName) }) {
                                    Icon(Icons.Filled.Close, contentDescription = "Supprimer")
                                }
                            }
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { showFilePicker = true }) { Text("Ajouter des fichiers") }
                    if (selectedFiles.isNotEmpty()) {
                        TextButton(onClick = { vm.clearSelectedFiles() }) { Text("Vider") }
                    }
                }

                Button(
                    enabled = endDate >= startDate && dayEnd > dayStart && toIntSafe(slotMinutes, 60) > 0,
                    onClick = {
                        vm.update {
                            it.copy(
                                startDate = startDate,
                                endDate = endDate,
                                workDays = workDays,
                                dayStart = dayStart,
                                dayEnd = dayEnd,
                                slotMinutes = toIntSafe(slotMinutes, it.slotMinutes),
                                breakEverySlots = toIntSafe(breakEverySlots, it.breakEverySlots),
                                breakMinutes = toIntSafe(breakMinutes, it.breakMinutes)
                            )
                        }
                        if (selectedFiles.isNotEmpty()) {
                            vm.regenerateFromFiles()
                        } else {
                            vm.regenerate()
                        }
                        navController.navigate(Route.Calendar.route)
                    }
                ) { Text("Générer le calendrier") }

                Divider(Modifier.padding(top = 8.dp))
            }

            // === Liste groupée des créneaux ===
            grouped.forEach { (date, dayItems) ->
                item {
                    Text(
                        date.format(DateTimeFormatter.ofPattern("EEEE d MMMM yyyy")),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(16.dp)
                    )
                }
                items(dayItems, key = { it.id }) { entry ->
                    ScheduleRow(
                        entry = entry,
                        onClick = { vm.startEditing(it.id) },
                        onSetStatus = { item, status -> vm.setStatus(item.id, status) }
                    )
                }
            }
        }

        // === Dialogue "Sauver sous…" ===
        if (showSaveDialog) {
            AlertDialog(
                onDismissRequest = { showSaveDialog = false },
                confirmButton = {
                    TextButton(onClick = {
                        val title = saveTitle.text.trim().ifBlank { "Calendrier" }
                        val newId = CalendarRepository.saveNew(context, title, items)
                        vm.setCurrentCalendarId(newId)

                        // ⬇️ Remplace LaunchedEffect(...) par une coroutine
                        scope.launch {
                            try {
                                social.Auth.init(context)
                                social.RemoteCalendarService.publishCalendar(newId, title, items)
                            } catch (_: Throwable) {
                                // best effort, ne casse pas l'UX
                            }
                        }

                        showSaveDialog = false
                        saveTitle = TextFieldValue("")
                        scope.launch { snack.showSnackbar("Calendrier sauvegardé: $title") }
                    }) { Text("Sauvegarder") }
                },
                dismissButton = { TextButton(onClick = { showSaveDialog = false }) { Text("Annuler") } },
                title = { Text("Sauver le calendrier") },
                text = {
                    OutlinedTextField(
                        value = saveTitle,
                        onValueChange = { saveTitle = it },
                        singleLine = true,
                        placeholder = { Text("Titre du calendrier") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            )
        }

        if (sheetOpen && sheetTarget != null) {
            val bottomSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            ModalBottomSheet(
                onDismissRequest = {
                    sheetOpen = false
                    sheetTarget = null
                },
                sheetState = bottomSheetState
            ) {
                val pf = sheetTarget!!

                // États locaux pour l’édition dans la page flottante
                var localRepeat by remember(pf.displayName, pf.repeatCount) {
                    mutableStateOf(pf.repeatCount.toString())
                }
                var localSessions by remember(pf.displayName, pf.sessionsPerRead) {
                    mutableStateOf(pf.sessionsPerRead.toString())
                }
                var localDiff by remember(pf.displayName, pf.difficulty) {
                    mutableStateOf(pf.difficulty.toString())
                }
                val effMinutes = remember(pf.durationMinutes, localDiff) {
                    val d = localDiff.toIntOrNull() ?: pf.difficulty
                    DifficultyUtils.effectiveMinutes(pf.durationMinutes, d)
                }
                var localSessionMinutes by remember(pf.displayName, pf.sessionDurationMinutes, pf.sessionsPerRead, localDiff) {
                    val base = pf.sessionDurationMinutes
                        ?: kotlin.math.ceil(effMinutes / pf.sessionsPerRead.coerceAtLeast(1).toDouble()).toInt()
                    mutableStateOf(base.toString())
                }
                var localImportance by remember(pf.displayName, pf.importance) {
                    mutableStateOf(pf.importance.toString())
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        pf.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text("${pf.durationMinutes} min", style = MaterialTheme.typography.bodySmall)

                    // ---- Titre de la feuille des critères ----
                    val sheetTitle = "Critères de planification du fichier " + (pf.displayName.ifBlank { "Fichier" })
                    Text(
                        text = sheetTitle,
                        style = MaterialTheme.typography.titleLarge,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                    Divider(modifier = Modifier.padding(bottom = 8.dp))
                    // ------------------------------------------

                    FlowRow(
                        modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Lectures
                        Column(
                            modifier = Modifier.widthIn(min = 108.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("Répétitions :", style = MaterialTheme.typography.titleSmall)
                            CompactNumberField(
                                value = localRepeat,
                                onValueChange = { s ->
                                    localRepeat = s
                                    val n = s.toIntOrNull() ?: 1
                                    vm.updateRepeatCount(pf.displayName, if (n < 1) 1 else n)
                                },
                                label = "Lectures",
                                modifier = Modifier
                                    .width(88.dp)
                                    .padding(top = 4.dp),
                                maxChars = 2
                            )
                        }


                        // Séances
                        Column(
                            modifier = Modifier.widthIn(min = 108.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("Découpage en séances :", style = MaterialTheme.typography.titleSmall)
                            CompactNumberField(
                                value = localSessions,
                                onValueChange = { s ->
                                    localSessions = s
                                    val n = s.toIntOrNull()?.coerceIn(1, 12) ?: 1

                                    // L’utilisateur force le NOMBRE de séances -> on recalcule la durée/séance
                                    vm.updateSessionsPerRead(pf.displayName, n)

                                    val eff = DifficultyUtils.effectiveMinutes(pf.durationMinutes, localDiff.toIntOrNull() ?: pf.difficulty)
                                    val per = kotlin.math.ceil(eff / n.toDouble()).toInt().coerceAtLeast(1)
                                    localSessionMinutes = per.toString()
                                },
                                label = "Séances",
                                modifier = Modifier
                                    .width(88.dp)
                                    .padding(top = 4.dp),
                                maxChars = 2
                            )
                        }

                        // Durée par séance
                        Column(
                            modifier = Modifier.widthIn(min = 108.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("Durée/séance :", style = MaterialTheme.typography.titleSmall)
                            CompactNumberField(
                                value = localSessionMinutes,
                                onValueChange = { s ->
                                    localSessionMinutes = s
                                    val m = s.toIntOrNull()?.coerceAtLeast(1) ?: 1
                                    vm.updateSessionDuration(pf.displayName, m)

                                    val eff = DifficultyUtils.effectiveMinutes(pf.durationMinutes, localDiff.toIntOrNull() ?: pf.difficulty)
                                    val sess = kotlin.math.ceil(eff / m.toDouble()).toInt().coerceAtLeast(1)
                                    localSessions = sess.toString()
                                },
                                label = "min",
                                modifier = Modifier
                                    .width(88.dp)
                                    .padding(top = 4.dp),
                                maxChars = 3
                            )
                        }

                        // Difficulté
                        Column(
                            modifier = Modifier.widthIn(min = 108.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("Difficulté", style = MaterialTheme.typography.titleSmall)
                            CompactNumberField(
                                value = localDiff,
                                onValueChange = { s ->
                                    localDiff = s
                                    val n = s.toIntOrNull()?.coerceIn(1, 5) ?: 3
                                    vm.updateDifficulty(pf.displayName, n)

                                    // Conserver le nombre de séances actuel, ajuster la durée/séance
                                    val sessionsNow = localSessions.toIntOrNull()?.coerceIn(1, 12) ?: 1
                                    val eff = DifficultyUtils.effectiveMinutes(pf.durationMinutes, n)
                                    val per = kotlin.math.ceil(eff / sessionsNow.toDouble()).toInt().coerceAtLeast(1)
                                    localSessionMinutes = per.toString()
                                },
                                label = "Diff",
                                modifier = Modifier
                                    .width(88.dp)
                                    .padding(top = 4.dp),
                                maxChars = 1
                            )
                        }
                    }

                    // Importance
                    Column(
                        modifier = Modifier.widthIn(min = 108.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("Importance", style = MaterialTheme.typography.titleSmall)
                        CompactNumberField(
                            value = localImportance,
                            onValueChange = { s ->
                                localImportance = s
                                val lvl = s.toIntOrNull()?.coerceIn(1, 3) ?: 2
                                vm.updateImportance(pf.displayName, lvl)
                            },
                            label = "Imp",
                            modifier = Modifier
                                .width(88.dp)
                                .padding(top = 4.dp),
                            maxChars = 1
                        )
                    }

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = {
                            sheetOpen = false
                            sheetTarget = null
                        }) {
                            Text("Fermer")
                        }
                    }
                }
            }
        }

        // === Dialogue d’édition réactivé (APERÇU INSTANTANÉ) ===
        if (editing != null) {
            SlotEditorDialog(
                initial = editing!!,
                onPreview = { vm.previewItemAndShiftFollowing(it) }, // bouger le slider recalcule en direct
                onDismiss = { vm.cancelEditing() },                  // annule et restaure le backup
                onSave = { vm.commitEditing() }                      // valide l’aperçu actuel
            )
        }
    }
    if (showFilePicker) {
        FilePickerDialog(
            onPicked = { pf ->
                vm.addPlannedFile(pf)
                showFilePicker = false
            },
            onDismiss = { showFilePicker = false }
        )
    }

}

/* ================= Composants réutilisables ================= */

@Composable
private fun DayChip(label: String, selected: Boolean, onToggle: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onToggle,
        label = { Text(label) },
        leadingIcon = if (selected) { { Icon(Icons.Filled.Check, contentDescription = null) } } else null
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateRow(label: String, date: LocalDate, onChange: (LocalDate) -> Unit) {
    var showSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = date
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
    )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // La date affichée agit comme un bouton
        TextButton(onClick = { showSheet = true }) {
            Text("$label : $date")
        }
        OutlinedButton(onClick = { onChange(date.minusDays(1)) }) { Text("-1j") }
        OutlinedButton(onClick = { onChange(date.plusDays(1)) }) { Text("+1j") }
        // (Bouton "Choisir" supprimé)
    }

    if (showSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSheet = false },
            sheetState = sheetState
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(label, style = MaterialTheme.typography.titleMedium)
                DatePicker(
                    state = datePickerState,
                    showModeToggle = true
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = { showSheet = false }) { Text("Annuler") }
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = {
                        val millis = datePickerState.selectedDateMillis
                        if (millis != null) {
                            val newDate = Instant.ofEpochMilli(millis)
                                .atZone(ZoneId.systemDefault())
                                .toLocalDate()
                            onChange(newDate)
                        }
                        showSheet = false
                    }) { Text("Valider") }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeRow(label: String, time: LocalTime, onChange: (LocalTime) -> Unit) {
    var showSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val timeState = rememberTimePickerState(
        initialHour = time.hour,
        initialMinute = time.minute,
        is24Hour = true
    )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // L'heure affichée agit comme un bouton
        TextButton(onClick = { showSheet = true }) {
            val hhmm = "%02d:%02d".format(time.hour, time.minute)
            Text("$label : $hhmm")
        }
        OutlinedButton(onClick = { onChange(time.minusMinutes(30)) }) { Text("-30m") }
        OutlinedButton(onClick = { onChange(time.plusMinutes(30)) }) { Text("+30m") }
        // (Bouton "Choisir" supprimé)
    }

    if (showSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSheet = false },
            sheetState = sheetState
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(label, style = MaterialTheme.typography.titleMedium)
                TimePicker(state = timeState)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = { showSheet = false }) { Text("Annuler") }
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = {
                        onChange(LocalTime.of(timeState.hour, timeState.minute))
                        showSheet = false
                    }) { Text("Valider") }
                }
            }
        }
    }
}

@Composable
private fun ScheduleRow(
    entry: ScheduleItem,
    onClick: (ScheduleItem) -> Unit,
    onSetStatus: (ScheduleItem, ScheduleStatus) -> Unit
) {
    val timeFmt = DateTimeFormatter.ofPattern("HH:mm")

    val indicator = when (entry.status) {
        ScheduleStatus.FAIT     -> Color(0xFF4CAF50)
        ScheduleStatus.EN_COURS -> Color(0xFFFFC107)
        ScheduleStatus.PAS_FAIT -> Color(0xFFF44336)
        else                    -> Color.Transparent
    }
    val bg = when (entry.status) {
        ScheduleStatus.FAIT     -> Color(0x334CAF50)
        ScheduleStatus.EN_COURS -> Color(0x33FFC107)
        ScheduleStatus.PAS_FAIT -> Color(0x33F44336)
        else                    -> Color.Transparent
    }

    val now = java.time.LocalDateTime.now()
    val passed = entry.end.isBefore(now)

    Surface(color = bg) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onClick(entry) }
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(6.dp)
                    .height(36.dp)
                    .background(indicator)
            )
            Spacer(Modifier.width(8.dp))

            Text(
                "${entry.start.format(timeFmt)}—${entry.end.format(timeFmt)}",
                modifier = Modifier.width(110.dp)
            )

            Column(Modifier.weight(1f)) {
                Text(entry.title, style = MaterialTheme.typography.titleSmall)
                if (entry.notes.isNotBlank()) {
                    Text(entry.notes, style = MaterialTheme.typography.bodySmall, maxLines = 2)
                } else {
                    Text(
                        "Notes (touchez pour éditer)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }

            AssistChip(
                onClick = { onClick(entry) },
                label = { Text("${entry.durationMinutes} min") }
            )
        }
    }
    Divider()
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SlotEditorDialog(
    initial: ScheduleItem,
    onPreview: (ScheduleItem) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit
) {
    var title by remember { mutableStateOf(TextFieldValue(initial.title)) }
    var notes by remember { mutableStateOf(TextFieldValue(initial.notes)) }
    var duration by remember { mutableStateOf(initial.durationMinutes.toFloat()) }
    val isPast = initial.end.isBefore(java.time.LocalDateTime.now())

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onSave) { Text("Enregistrer") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
        title = { Text("Éditer le créneau") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = {
                        title = it
                        onPreview(
                            initial.copy(
                                title = it.text,
                                notes = notes.text,
                                durationMinutes = duration.toInt()
                            )
                        )
                    },
                    label = { Text("Titre") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Durée: ${duration.toInt()} minutes")
                Slider(
                    value = duration,
                    onValueChange = {
                        if (!isPast) {
                            duration = it
                            onPreview(
                                initial.copy(
                                    title = title.text,
                                    notes = notes.text,
                                    durationMinutes = it.toInt().coerceAtLeast(5)
                                )
                            )
                        }
                    },
                    valueRange = 5f..240f,
                    steps = 235,
                    enabled = !isPast
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = {
                        notes = it
                        onPreview(
                            initial.copy(
                                title = title.text,
                                notes = it.text,
                                durationMinutes = duration.toInt()
                            )
                        )
                    },
                    label = { Text("Notes") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    )
}

@Composable
private fun FilePickerDialog(
    onPicked: (PlannedFile) -> Unit,
    onDismiss: () -> Unit
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val dlVm: Downloads.DownloadsViewModel =
        androidx.lifecycle.viewmodel.compose.viewModel(factory = Downloads.DownloadsVmFactory(ctx))
    val state by dlVm.state.collectAsState()

    LaunchedEffect(Unit) { dlVm.load(ctx) }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Fermer") } },
        title = { Text("Choisir un fichier à planifier") },
        text = {
            if (state.isLoading) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    items(state.items, key = { it.id }) { item ->
                        ListItem(
                            headlineContent = { Text(item.displayName) },
                            supportingContent = {
                                val kb = (item.sizeBytes ?: 0L) / 1024
                                Text("${kb} Ko")
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    scope.launch {
                                        val repo = DataStoreSettingsRepository(ctx)
                                        val s = repo.settings.first()
                                        val diff = s.defaultDifficulty.coerceIn(1, 5)

                                        val mins = ReadingDurationEstimator.estimateMinutesFor(ctx, item, diff)
                                        onPicked(
                                            PlannedFile(
                                                displayName = item.displayName,
                                                durationMinutes = mins,
                                                difficulty = diff,
                                                uri = item.uri
                                            )
                                        )
                                    }
                                }
                        )
                        Divider()
                    }
                }
            }
        }
    )
}
