package planner

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.navigation.NavController

// Bouton d'action "Accueil" (icône maison)
@Composable
fun AccueilAction(onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(Icons.Filled.Home, contentDescription = "Accueil")
    }
}

// Navigation "retour à l'accueil" sans empiler les écrans
fun NavController.goHome() {
    navigate(Route.Home.route) {
        popUpTo(graph.startDestinationId) { inclusive= true }
        launchSingleTop = true
        restoreState = true
    }
}
