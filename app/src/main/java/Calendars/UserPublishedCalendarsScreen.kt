package Calendars

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.foundation.clickable

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserPublishedCalendarsScreen(
    viewModel: UserPublishedCalendarsViewModel = hiltViewModel(),
    onBack: () -> Unit,
    onOpenCalendar: (String, String) -> Unit
) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Mes calendriers publiés") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                    }
                }
            )
        }
    ) { padding ->
        when {
            state.isLoading -> {
                Box(Modifier.fillMaxSize().padding(padding)) {
                    CircularProgressIndicator(Modifier.align(Alignment.Center))
                }
            }
            state.items.isEmpty() -> {
                Box(Modifier.fillMaxSize().padding(padding)) {
                    Text(
                        "Aucun calendrier publié pour le moment.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            }
            else -> {
                LazyColumn(contentPadding = padding) {
                    items(state.items) { cal ->
                        ListItem(
                            headlineContent = { Text(cal.title) },
                            supportingContent = { Text("Dernière mise à jour : ${cal.updatedAt}") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onOpenCalendar(cal.id, cal.title) }
                        )
                        Divider()
                    }
                }
            }
        }
    }
}
