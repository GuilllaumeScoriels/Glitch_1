package Downloads

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import planner.AccueilAction
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import Calendars.CalendarSummary
import java.time.format.DateTimeFormatter
import Downloads.DownloadsViewModel
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import Settings.AppSettings
import androidx.compose.foundation.layout.size
import Settings.DataStoreSettingsRepository
import com.example.lecturemotparmotapp.formatMillis
import org.bouncycastle.asn1.x500.style.RFC4519Style.title

private val FILE_ICON_SIZE = 64.dp
private val ACTION_ICON_SIZE = 18.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(
    viewModel: DownloadsViewModel,
    onOpenText: (String) -> Unit = {},
    onRequestDeleteConsent: (androidx.activity.result.IntentSenderRequest) -> Unit = {},
    onDeleteByName: (String) -> Unit = {}, // <-- déjà présent
    onHome: () -> Unit = {}
) {
    val ctx = LocalContext.current
    val uiState by viewModel.state.collectAsState()

// Paramètres (vitesse par défaut ms/mot) — s’actualise automatiquement
    val settingsRepo = remember { DataStoreSettingsRepository(ctx) }
    val settings by settingsRepo.settings.collectAsState(initial = AppSettings())

// Carte id -> wordCount calculée par le VM
    val wordCounts by viewModel.wordCounts.collectAsState()

    // Difficultés choisies en local pour chaque item (1..5)
    val itemDifficulties by viewModel.itemDifficulties.collectAsState()

    //Mémoire locale du nom à renommer, synchro sur la cible.
    var renameText by remember(uiState.renameTarget) {
        mutableStateOf(uiState.renameTarget?.displayName ?: "")
    }


    LaunchedEffect(Unit) {
        viewModel.load(ctx)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Fichiers téléchargés") },
                actions = {
                    AccueilAction { onHome() }
                }
            )
        }
    ) { pad ->
        if (uiState.isLoading) {
            Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(pad),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(12.dp)
            ) {
                items(uiState.items, key = { it.id }) { item ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                // Action au clic : lire le contenu et le propager au parent
                                viewModel.readItemText(ctx, item)?.let { txt ->
                                    onOpenText(txt)
                                }
                            }
                            .size(FILE_ICON_SIZE)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .padding(18.dp)
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    item.displayName,
                                    style = MaterialTheme.typography.titleMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Spacer(Modifier.height(4.dp))
                                val size = item.sizeBytes?.let { humanBytes(it) } ?: "—"
                                val date = item.lastModified?.let {
                                    java.text.DateFormat.getDateTimeInstance()
                                        .format(java.util.Date(it))
                                } ?: "—"
                                Text("$size • $date", style = MaterialTheme.typography.bodySmall, modifier = Modifier.size(FILE_ICON_SIZE))
                            }

                            // Déclenche le calcul lazily au montage de l’item (pas de blocage UI)
                            LaunchedEffect(item.id) {
                                viewModel.ensureWordCount(ctx, item)
                            }

                            // Initialiser la difficulté de l'item si absente
                            LaunchedEffect(item.id, settings.defaultDifficulty) {
                                if (itemDifficulties[item.id] == null) {
                                    viewModel.setItemDifficulty(item.id, settings.defaultDifficulty)
                                }
                            }

                            // Choix de la difficulté (si absente: valeur par défaut des paramètres)
                            val diff = (itemDifficulties[item.id] ?: settings.defaultDifficulty).coerceIn(1, 5)

                            // ms/mot selon la difficulté (réagit en temps réel aux changements)
                            val msPerWord = when (diff) {
                                1 -> settings.readMsD1
                                2 -> settings.readMsD2
                                3 -> settings.readMsD3
                                4 -> settings.readMsD4
                                5 -> settings.readMsD5
                                else -> settings.defaultWordDelayMs
                            }.coerceAtLeast(1)

                            val wc = wordCounts[item.id] ?: 0
                            val durationMs = wc.toLong() * msPerWord.toLong()
                            val durationText = if (wc > 0) formatMillis(durationMs) else "—"

                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = "$durationText",
                                style = MaterialTheme.typography.bodySmall
                            )

                            Spacer(Modifier.height(2.dp))
                            // Tap pour cycler la difficulté 1..5 ; la durée s'actualise instantanément
                            Text(
                                text = "Diff $diff",
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.clickable {
                                    val next = if (diff >= 5) 1 else diff + 1
                                    viewModel.setItemDifficulty(item.id, next)
                                }
                            )

                            IconButton(onClick = { viewModel.requestRename(item) }) {
                                Icon(Icons.Filled.Edit, contentDescription = "Renommer", modifier = Modifier.size(ACTION_ICON_SIZE))
                            }

                            IconButton(onClick = {
                                viewModel.deleteByName(
                                    context = ctx,
                                    displayName = item.displayName,
                                    onNeedsConsent = onRequestDeleteConsent,
                                )
                            }) {
                                Icon(Icons.Default.Delete, contentDescription = "Supprimer", modifier = Modifier.size(ACTION_ICON_SIZE))
                            }
                        }
                    }
                }
            }
        }
    }
    if (uiState.renameTarget != null) {
        AlertDialog(
            onDismissRequest = { viewModel.cancelRename() },
            title = { Text("Renommer le fichier") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        uiState.renameTarget!!.displayName,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    OutlinedTextField(
                        value = renameText,
                        onValueChange = { renameText = it },
                        singleLine = true,
                        label = { Text("Nouveau nom") }
                    )
                }
            },
            confirmButton = {
                Button(onClick = { viewModel.confirmRename(ctx, renameText) }) {
                    Text("Renommer")
                }
            },
            dismissButton = {
                Button(onClick = { viewModel.cancelRename() }) { Text("Annuler") }
            }
        )
    }

}

private fun humanBytes(b: Long): String {
    if (b < 1024) return "$b B"
    val z = (63 - java.lang.Long.numberOfLeadingZeros(b)) / 10
    return String.format(java.util.Locale.getDefault(), "%.1f %sB", b / (1L shl (z * 10)).toDouble(), " KMGTPE"[z])
}



@Composable
private fun FilesRow(
    summary: CalendarSummary,
    onOpen: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
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
                IconButton(onClick = onRename) { Icon(Icons.Filled.Edit, contentDescription = "Renommer", modifier = Modifier.size(ACTION_ICON_SIZE)) }
                IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, contentDescription = "Supprimer", modifier = Modifier.size(ACTION_ICON_SIZE)) }
            }
        }
    )
    Divider()
}
