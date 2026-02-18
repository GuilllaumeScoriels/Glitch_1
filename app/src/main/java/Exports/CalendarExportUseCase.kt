package Exports

/**
 * CalendarExportUseCase
 * ---------------------------------------------------------
 * Responsabilité :
 * - générer un fichier .ics depuis une liste de ScheduleItem
 * - créer l'URI via FileProvider
 * - ouvrir une app calendrier via Intent
 */

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import planner.ScheduleItem

/* Context = lien entre code kotlin et téléphone (android): représente identité + permissions + environnement
Passé en argument pour toute fonction qui fait une action concrète sur le téléphone.
Context fournit le dossier privé autorisé (contourne le sandboxing android).
Android impose que le context viennt toujours du composant actif.
 */
object CalendarExportUseCase {
    fun exportAndOpen(
        context: Context, // Objet android qui donne accès répertoire et permet de lancer Google Calendar
        items: List<ScheduleItem>,
        calendarName: String = "Calendrier organisé",
        useChooser: Boolean = true // Android affichera une fenêtre "Choisir une application"
    ) {
        // Appel au composant qui écrit le calendrier
        val file = IcsCalendarExporter.exportToCacheFile(
            context = context,
            items = items,
            calendarName = calendarName
        )
        // Création de l'URI partageable
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        // Intent = message de l'app vers android pour demander une action
        val intent = Intent(Intent.ACTION_VIEW).apply { // Action standard : afficher une ressource
            setDataAndType(uri, "text/calendar") // uri le fichier à ouvrir + texte indiquant que c'est un calendrier
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) // Donne temporairement la permission à l'app cible de lire le fichier via uri
        }

        if (useChooser) { // if(true): android affiche boite de dialogue "importe le plannign" + liste apps compatibles
            context.startActivity(Intent.createChooser(intent, "importer le planning dans..."))
        } else { // Android lance directement l'app par défaut
            context.startActivity(intent)
        }
    }
}