package planner
/**
 * NavHelpers.kt
 * ---------------------------------------------------------
 * Rôle :
 * Ce fichier regroupe les composants UI réutilisables liés à la navigation
 * et aux actions globales de l'application (TopAppBar).
 *
 * Objectif :
 * Centraliser les boutons d’actions standards afin d’éviter la duplication
 * de code dans les différents écrans et garantir une cohérence visuelle
 * et fonctionnelle dans toute l’application.
 *
 * Contenu :
 * - AccueilAction : permet de revenir à l’écran principal (Home)
 * - ExportCalendarAction : exporte le planning vers un calendrier externe
 *
 * Avantages :
 * - Respect du principe DRY (Don't Repeat Yourself)
 * - Amélioration de la maintenabilité
 * - Facilite les modifications globales d’UI/navigation
 * - Meilleure lisibilité de chaque écran
 *
 * Architecture :
 * Les écrans n’implémentent pas directement leurs icônes de navigation.
 * Ils consomment ces helpers afin de séparer :
 *    UI d’écran  ≠  Actions globales d’application
 *
 * Ainsi, toute modification d’icône, tooltip, comportement ou accessibilité
 * se fait uniquement ici.
 */

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.navigation.NavController
import androidx.compose.material.icons.filled.Share

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

@Composable
fun ExportCalendarAction(onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(Icons.Filled.Share, contentDescription = "Exporter vers calendrier")
    }
}
