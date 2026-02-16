package com.example.lecturemotparmotapp

import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.filled.Folder
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.filled.Subscriptions
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import social.Auth
import profile.UserProfileService
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccueilScreen(
    onStartReading: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenPlanner: () -> Unit,
    onOpenDownloads: () -> Unit,
    onOpenSavedCalendars: () -> Unit,
    onOpenFeed: () -> Unit,
    onOpenSubscriptions: () -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenProfile: (String) -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            Scaffold(
                // Fond du Scaffold noir pour l’uniformité
                containerColor = Color.Black,

                // Top bar: fond noir, texte blanc
                topBar = {
                    CenterAlignedTopAppBar(
                        title = {
                            Text("Glitch")
                        },
                        navigationIcon = {
                        },
                        actions = {
                            val ctx = LocalContext.current
                            val scope = rememberCoroutineScope()
                            val unread by Notifications.UnreadBadgeStore.unreadFlow(ctx).collectAsState(initial = 0)

                            IconButton(onClick = {
                                scope.launch { Notifications.UnreadBadgeStore.reset(ctx) }
                                onOpenNotifications()
                            }) {
                                Box {
                                    Icon(Icons.Filled.Notifications, contentDescription = "Notifications")
                                    if (unread > 0) {
                                        // Pastille rouge en haut à droite
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.TopEnd)
                                                .offset(x = 6.dp, y = (-4).dp)
                                                .size(18.dp)
                                                .background(Color.Red, shape = CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = if (unread > 99) "99+" else unread.toString(),
                                                color = Color.White,
                                                fontSize = 10.sp
                                            )
                                        }
                                    }
                                }
                            }
                        },
                        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                            containerColor = Color.Black,
                            titleContentColor = Color.White,
                            navigationIconContentColor = Color.White,
                            actionIconContentColor = Color.White
                        )
                    )
                },
                bottomBar = {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.Gray)
                            .padding(vertical = 16.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // FAB Paramètres (flottant)
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            FloatingActionButton(
                                onClick = onOpenSettings,
                                containerColor = Color.Gray,
                                contentColor = Color.White,
                            ) {
                                Icon(
                                    Icons.Filled.Settings,
                                    contentDescription = "Paramètres",
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Text(
                                "Paramètres",
                                color = Color.White,
                                modifier = Modifier.padding(top = 6.dp),
                                fontSize = 12.sp
                            )
                        }


                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            FloatingActionButton(
                                onClick = onOpenDownloads,
                                containerColor = Color.Gray,
                                contentColor = Color.White
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Folder,
                                    contentDescription = "Fichiers téléchargés"
                                )
                            }
                            Text(
                                "Fichiers",
                                color = Color.White,
                                modifier = Modifier.padding(top = 6.dp),
                                fontSize = 12.sp
                            )
                        }


                        // FAB Lecture (principal)
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            FloatingActionButton(
                                onClick = onStartReading,
                                containerColor = Color.Gray,
                                contentColor = Color.White
                            ) {
                                Icon(
                                    Icons.Filled.PlayArrow,
                                    contentDescription = "Démarrer la lecture",
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Text(
                                "Lecture",
                                color = Color.White,
                                modifier = Modifier.padding(top = 6.dp),
                                fontSize = 12.sp
                            )
                        }

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            FloatingActionButton(
                                onClick = onOpenSavedCalendars,
                                containerColor = Color.Gray,
                                contentColor = Color.White
                            ) {
                                Icon(
                                    Icons.Filled.Event,
                                    contentDescription = "Calendriers enregistrés",
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Text(
                                "Calendars",
                                color = Color.White,
                                modifier = Modifier.padding(top = 6.dp),
                                fontSize = 12.sp
                            )
                        }

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            FloatingActionButton(
                                onClick = onOpenSubscriptions,
                                containerColor = Color.Gray,
                                contentColor = Color.White
                            ) {
                                Icon(Icons.Filled.Subscriptions, contentDescription = "Social")
                            }
                            Text(
                                "Social",
                                color = Color.White,
                                modifier = Modifier.padding(top = 6.dp),
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            ) { padding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .background(Color.Black),
                    contentAlignment = Alignment.Center
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(onClick = onOpenPlanner) { Text("Configurer le planning") }
                    }
                }
            }
        }
    }
}
