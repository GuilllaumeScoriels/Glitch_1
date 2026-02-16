package social

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.time.format.DateTimeFormatter
import planner.ScheduleItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PublicCalendarScreen(
    calendarId: String,
    title: String,
    onBack: () -> Unit
) {
    var items by remember { mutableStateOf<List<ScheduleItem>>(emptyList()) }

    LaunchedEffect(calendarId) {
        items = RemoteCalendarService.loadItems(calendarId)
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(title) }, // ← Titre affiché, NON éditable
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                    }
                }
            )
        }
    ) { padding ->
        if (items.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding)) {
                Text(
                    "Aucun créneau dans ce calendrier.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        } else {
            val timeFmt = DateTimeFormatter.ofPattern("HH:mm")
            LazyColumn(
                contentPadding = padding,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp)
            ) {
                items(items, key = { it.id }) { s ->
                    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp)) {
                            Text("${s.start.toLocalDate()}  ${s.start.toLocalTime().format(timeFmt)} — ${(s.start.plusMinutes(s.durationMinutes.toLong())).toLocalTime().format(timeFmt)}",
                                style = MaterialTheme.typography.labelMedium)
                            Spacer(Modifier.height(4.dp))
                            Text(s.title.ifBlank { "Créneau" }, style = MaterialTheme.typography.titleMedium)
                            if (s.notes.isNotBlank()) {
                                Spacer(Modifier.height(4.dp))
                                Text(s.notes, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
    }
}
