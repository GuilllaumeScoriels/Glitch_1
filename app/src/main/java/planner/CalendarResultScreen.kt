package planner

import Calendars.CalendarRepository
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import java.time.format.DateTimeFormatter
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.rotate
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.alpha
import androidx.compose.animation.core.animateFloatAsState
import kotlinx.coroutines.delay
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import com.tom_roush.harmony.awt.AWTColor.white
import social.CommentsService

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarResultScreen(navController: NavController, vm: PlannerViewModel) {
    val items by vm.items.collectAsState()
    val currentId by vm.currentCalendarId.collectAsState()
    val context = LocalContext.current

    val grouped = items.groupBy { it.start.toLocalDate() }
    val fmt = DateTimeFormatter.ofPattern("EEEE d MMMM yyyy")
    var selectedItem by remember { mutableStateOf<ScheduleItem?>(null) }
    var dialogNotes by rememberSaveable(selectedItem?.id) { mutableStateOf("") }

    // À chaque ouverture sur un créneau différent, on (ré)initialise le champ d’édition.
    LaunchedEffect(selectedItem?.id) {
        dialogNotes = selectedItem?.notes.orEmpty()
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Calendrier organisé") },
                actions = {
                    AccueilAction { navController.goHome() }
                }
            )
        }
    ) { pad ->
        val listState = rememberLazyListState()
        val coroutineScope = rememberCoroutineScope()

// Jours triés (sections) du calendrier
        val dayKeys = remember(items) { grouped.toSortedMap().keys.toList() }

// Indices de début de chaque section "jour" dans la LazyColumn
        val dayStartIndices = remember(items) {
            var acc = 1 // 1 = l’item d’en-tête "Retour"
            buildList {
                dayKeys.forEach { date ->
                    add(acc) // index du header pour ce jour
                    acc += 1 + (grouped[date]?.size ?: 0) // +1 header + N créneaux
                }
            }
        }

// Position du curseur (0f..1f) sur TOUT le contenu (pas seulement par jour)
        var sliderPos by remember { mutableStateOf(0f) }

// Total d'items ~ 1 ("Retour") + headers de jours + créneaux
        val totalItemsCount = remember(items) { 1 + dayKeys.size + items.size }

// Infos de layout visibles (taille moyenne d'item & nb visibles)
        val visibleInfo = listState.layoutInfo.visibleItemsInfo
        val avgItemSizePx = (visibleInfo.map { it.size }.average().toFloat()).coerceAtLeast(1f)
        val approxVisibleItems = visibleInfo.size.coerceAtLeast(1)

// Nombre d'items "scrollables" = total - visibles (évite dépassement en bas)
        val scrollableItems = (totalItemsCount - approxVisibleItems).coerceAtLeast(1)

// Affiche le curseur seulement si liste scrollable et pendant le scroll (+ délai)
        val sliderEnabled = totalItemsCount > approxVisibleItems
        var showSlider by remember { mutableStateOf(false) }
        var isDraggingSlider by remember { mutableStateOf(false) }

        LaunchedEffect(listState.isScrollInProgress, sliderEnabled, isDraggingSlider) {
            if (sliderEnabled && (listState.isScrollInProgress || isDraggingSlider)) {
                showSlider = true
            } else {
                delay(800) // rémanence après l’arrêt
                showSlider = false
            }
        }

        val sliderAlpha by animateFloatAsState(
            targetValue = if (showSlider || isDraggingSlider) 1f else 0f,
            label = "sliderAlpha"
        )

// Met à jour la fraction pendant le scroll naturel (hors drag sur le curseur)
        LaunchedEffect(
            listState.firstVisibleItemIndex,
            listState.firstVisibleItemScrollOffset,
            scrollableItems,
            isDraggingSlider
        ) {
            if (!isDraggingSlider) {
                val visibleInfoNow = listState.layoutInfo.visibleItemsInfo
                val avgItemSizePxNow =
                    (visibleInfoNow.map { it.size }.average().toFloat()).coerceAtLeast(1f)
                val scrolledItems = listState.firstVisibleItemIndex +
                        (listState.firstVisibleItemScrollOffset / avgItemSizePxNow)
                sliderPos = (scrolledItems / scrollableItems.toFloat()).coerceIn(0f, 1f)
            }
        }

        Box(Modifier.fillMaxSize()) {
            LazyColumn(
                state = listState,
                contentPadding = pad,
                modifier = Modifier.fillMaxSize()
            ) {
                item {
                    Spacer(Modifier.height(24.dp))
                    OutlinedButton(
                        onClick = { navController.navigateUp() },
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text("Retour")
                    }
                }
                grouped.toSortedMap().forEach { (date, dayItems) ->
                    item(key = "header-$date") {
                        Text(
                            text = date.format(fmt),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                    items(dayItems, key = { it.start.toString() }) { s ->
                        val background = when (s.type) {
                            ScheduleItemType.PAUSE -> Color(0xFFFFC0CB) // rose clair
                            ScheduleItemType.WORK -> MaterialTheme.colorScheme.surface
                        }
                        val indicator = when (s.status) {
                            ScheduleStatus.FAIT -> Color(0xFF4CAF50)
                            ScheduleStatus.EN_COURS -> Color(0xFFFFC107)
                            ScheduleStatus.PAS_FAIT -> Color(0xFFF44336)
                            else -> Color.Transparent
                        }
                        val bg = when (s.status) {
                            ScheduleStatus.FAIT -> Color(0x334CAF50)
                            ScheduleStatus.EN_COURS -> Color(0x33FFC107)
                            ScheduleStatus.PAS_FAIT -> Color(0x33F44336)
                            else -> Color.Transparent
                        }

                        val containerColor = when {
                            s.status == ScheduleStatus.FAIT -> Color(0x334CAF50)
                            s.status == ScheduleStatus.EN_COURS -> Color(0x33FFC107)
                            s.status == ScheduleStatus.PAS_FAIT -> Color(0x99F44336)
                            else -> MaterialTheme.colorScheme.surface
                        }

                            ListItem(
                                colors = ListItemDefaults.colors(containerColor = containerColor),
                                headlineContent = { Text("${s.start.toLocalTime()} - ${s.end.toLocalTime()}") },
                                supportingContent = {
                                    Column {
                                        Text(s.title, style = MaterialTheme.typography.bodyMedium)
                                        var notesText by rememberSaveable(s.id) { mutableStateOf(s.notes) }
                                        if (s.notes.isNotBlank()) {
                                            Text(
                                                text = s.notes.take(30) + if (s.notes.length > 30) "..." else "",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurface.copy(
                                                    alpha = 0.7f
                                                )
                                            )
                                        }

                                        // --- Commentaires publics ---
                                        val calendarId = currentId
                                        if (calendarId != null) {
                                            val comments by remember(calendarId, s.id) {
                                                CommentsService.observeComments(calendarId, s.id)
                                            }.collectAsState(initial = emptyList())

                                            Spacer(Modifier.height(8.dp))
                                            Text("Commentaires (${comments.size})", style = MaterialTheme.typography.labelLarge)

                                            comments.forEach { c ->
                                                Text("• ${c.fromUid}: ${c.text}")
                                            }

                                            var newComment by rememberSaveable(s.id) { mutableStateOf("") }
                                            OutlinedTextField(
                                                value = newComment,
                                                onValueChange = { newComment = it },
                                                modifier = Modifier.fillMaxWidth(),
                                                placeholder = { Text("Ajouter un commentaire…") }
                                            )
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.End
                                            ) {
                                                val coroutineScope = rememberCoroutineScope()
                                                TextButton(
                                                    enabled = newComment.isNotBlank(),
                                                    onClick = {
                                                        coroutineScope.launch {
                                                            runCatching { CommentsService.postComment(calendarId, s.id, newComment.trim()) }
                                                            newComment = ""
                                                        }
                                                    }
                                                ) { Text("Envoyer") }
                                            }
                                        }

                                        val now = java.time.LocalDateTime.now()
                                        val passed = s.end.isBefore(now)
                                        if (passed && s.type == ScheduleItemType.WORK) {
                                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                AssistChip(
                                                    onClick = {
                                                        vm.setStatus(s.id, ScheduleStatus.FAIT)
                                                        currentId?.let {
                                                            CalendarRepository.saveOverwrite(
                                                                context,
                                                                it,
                                                                null,
                                                                vm.items.value
                                                            )
                                                        }
                                                    },
                                                    label = { Text("Fait") }
                                                )
                                                AssistChip(
                                                    onClick = {
                                                        vm.setStatus(s.id, ScheduleStatus.EN_COURS)
                                                        currentId?.let {
                                                            CalendarRepository.saveOverwrite(
                                                                context,
                                                                it,
                                                                null,
                                                                vm.items.value
                                                            )
                                                        }
                                                    },
                                                    label = { Text("En cours") }
                                                )
                                                AssistChip(
                                                    onClick = {
                                                        vm.setStatus(s.id, ScheduleStatus.PAS_FAIT)
                                                        currentId?.let {
                                                            CalendarRepository.saveOverwrite(
                                                                context,
                                                                it,
                                                                null,
                                                                vm.items.value
                                                            )
                                                        }
                                                    },

                                                    label = { Text("Pas fait") }
                                                )
                                            }
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(background)
                                    .clickable {
                                        selectedItem = s
                                    }  // ouvre la fenêtre flottante pour éditer les notes
                                    .padding(vertical = 4.dp),        // garde la compacité des lignes
                                trailingContent = {
                                    val now = java.time.LocalDateTime.now()
                                    val passed = s.end.isBefore(now)
                                    AssistChip(
                                        onClick = { /* rien ici pour ne pas modifier depuis Result */ },
                                        label = { Text("${s.durationMinutes} min") },
                                        enabled = false || !passed // false = lecture seule pour tous ; sinon !passed pour griser quand passé
                                    )
                                },
                                leadingContent = {
                                    if (indicator != Color.Transparent) {
                                        Box(Modifier.size(12.dp).background(indicator))
                                    }
                                }
                            )
                        Divider()
                    }
                }
            }

            // --- Curseur vertical plein écran (suit le doigt, pouce proportionnel) ---
            run {
                val density = LocalDensity.current
                val total = 1 + dayKeys.size + items.size
                val approxVisible = listState.layoutInfo.visibleItemsInfo.size.coerceAtLeast(1)
                val enabled = total > approxVisible

                if (enabled) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .fillMaxHeight()
                            .width(36.dp)           // gouttière
                            .padding(end = 4.dp)
                            .alpha(sliderAlpha),    // invisible hors scroll/drag
                        contentAlignment = Alignment.Center
                    ) {
                        var boxHeightPx by remember { mutableStateOf(1f) }

                        // Taille du pouce (proportionnelle au viewport) - calculée HORS Canvas
                        val viewportFraction =
                            (approxVisible.toFloat() / total.toFloat()).coerceIn(0f, 1f)
                        val minThumbPx = with(density) { 28.dp.toPx() }
                        val thumbHPx = maxOf(minThumbPx, boxHeightPx * viewportFraction)
                        val travelPx = (boxHeightPx - thumbHPx).coerceAtLeast(0f)

                        // Geste : actif si visible OU doigt posé
                        val dragModifier =
                            if (showSlider || isDraggingSlider) {
                                Modifier.pointerInput(total, travelPx, thumbHPx) {
                                    detectVerticalDragGestures(
                                        onDragStart = { offset ->
                                            isDraggingSlider = true
                                            // aligne le CENTRE du pouce sous le doigt
                                            val center = offset.y
                                                .coerceIn(thumbHPx / 2f, boxHeightPx - thumbHPx / 2f)
                                            val f =
                                                if (travelPx > 0f) ((center - thumbHPx / 2f) / travelPx) else 0f
                                            sliderPos = f.coerceIn(0f, 1f)

                                            val scrollable = (total - approxVisible).coerceAtLeast(1)
                                            val target =
                                                (sliderPos * scrollable).toInt().coerceIn(0, scrollable)
                                            coroutineScope.launch { listState.scrollToItem(target) }
                                        },
                                        onVerticalDrag = { change, dragAmount ->
                                            change.consume()
                                            isDraggingSlider = true
                                            // on déplace le CENTRE du pouce d’un delta = dragAmount
                                            val currentCenter = (sliderPos * travelPx) + (thumbHPx / 2f)
                                            val newCenter = (currentCenter + dragAmount)
                                                .coerceIn(thumbHPx / 2f, boxHeightPx - thumbHPx / 2f)
                                            val f =
                                                if (travelPx > 0f) ((newCenter - thumbHPx / 2f) / travelPx) else 0f
                                            sliderPos = f.coerceIn(0f, 1f)

                                            val scrollable = (total - approxVisible).coerceAtLeast(1)
                                            val target =
                                                (sliderPos * scrollable).toInt().coerceIn(0, scrollable)
                                            coroutineScope.launch { listState.scrollToItem(target) }
                                        },
                                        onDragEnd = { isDraggingSlider = false },
                                        onDragCancel = { isDraggingSlider = false }
                                    )
                                }
                            } else {
                                Modifier
                            }

                        Canvas(
                            modifier = Modifier
                                .fillMaxSize()
                                .onSizeChanged { boxHeightPx = it.height.toFloat() }
                                .then(dragModifier)
                        ) {
                            val trackX = size.width / 2f
                            val trackH = size.height

                            // rail
                            drawLine(
                                color = Color.LightGray,
                                start = Offset(trackX, 0f),
                                end = Offset(trackX, trackH),
                                strokeWidth = 4f
                            )

                            // Position du pouce (haut) : fraction * course disponible
                            val thumbTop = (sliderPos * (trackH - thumbHPx))
                                .coerceIn(0f, trackH - thumbHPx)

                            // pouce rect arrondi (gris)
                            drawRoundRect(
                                color = Color.Gray,
                                topLeft = Offset(trackX - 10f, thumbTop),
                                size = androidx.compose.ui.geometry.Size(20f, thumbHPx),
                                cornerRadius = androidx.compose.ui.geometry.CornerRadius(10f, 10f)
                            )
                        }
                    }
                }
            }
            // --- fin curseur vertical ---
        }

        if (selectedItem != null) {
            val item = selectedItem!!
            val now = java.time.LocalDateTime.now()
            val passed = item.end.isBefore(now)
            val changed = dialogNotes != item.notes

            AlertDialog(
                onDismissRequest = { selectedItem = null },
                confirmButton = {
                    TextButton(
                        onClick = {
                            if (changed) {
                                vm.updateNotes(item.id, dialogNotes)
                                currentId?.let {
                                    CalendarRepository.saveOverwrite(
                                        context,
                                        it,
                                        null,
                                        vm.items.value
                                    )
                                }
                            }
                            selectedItem = null
                        },
                        enabled = true // on permet de fermer même sans changement
                    ) { Text(if (changed) "Enregistrer" else "Fermer") }
                },
                dismissButton = {
                    if (changed) {
                        TextButton(onClick = { selectedItem = null }) { Text("Annuler") }
                    }
                },
                title = { Text(item.title) },
                text = {
                    Column {
                        // Heures, en petit
                        Text(
                            "${item.start.toLocalTime()} - ${item.end.toLocalTime()}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                        )
                        Spacer(Modifier.height(8.dp))

                        // Champ d’édition des notes (toujours éditable, même si passé)
                        OutlinedTextField(
                            value = dialogNotes,
                            onValueChange = { dialogNotes = it },
                            label = { Text("Notes") },
                            singleLine = false,
                            maxLines = 8,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 120.dp)
                        )

                        // Aide visuelle si le créneau est passé (optionnel)
                        if (passed) {
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "Créneau passé : seules les notes sont modifiables.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                    }
                }
            )
        }
    }
}
