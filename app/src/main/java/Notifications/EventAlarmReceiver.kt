package Notifications
/**
 * EventAlarmReceiver
 * ---------------------------------------------------------
 * Point d’entrée du système lorsque l’heure d’un créneau est atteinte.
 *
 * Ce composant est déclenché par Android (AlarmManager) même si :
 * - l’application est fermée
 * - le téléphone est verrouillé
 * - le processus a été tué
 *
 * Rôle principal :
 * 1) Recevoir l’événement temporel envoyé par le système
 * 2) Reconstruire les informations du créneau (id, titre, heure)
 * 3) Afficher la notification utilisateur correspondante
 * 4) Mettre à jour le compteur de notifications non lues
 * 5) Programmer le prochain créneau (rolling scheduling)
 *
 * IMPORTANT :
 * Le Receiver ne doit contenir aucune logique lourde.
 * Android lui accorde seulement quelques millisecondes d’exécution.
 * Toute opération lente est donc déléguée à une coroutine via goAsync().
 *
 * Architecture :
 * AlarmManager → EventAlarmReceiver → NotificationHelper → UI
 *                                      ↓
 *                                EventAlarmScheduler (prochain créneau)
 *
 * Ce fichier est le lien entre le temps système Android et la logique de l’application.
 */

import Calendars.CalendarRepository
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

class EventAlarmReceiver : BroadcastReceiver() { /* Hérite d'une classe du système android, donc ce n'est pas mon programme
    mais le système d'exploitation qui décide quand elle s'exécute */
    override fun onReceive(context: Context, intent: Intent) { /* On décide ici ce qui est fait quand android réveille la classe,
        càd au moment programmé par AlarmManager*/
        val itemId   = intent.getStringExtra(EXTRA_ID) ?: return // Opérateur Elvis pour donner une valeur si l'expression à gauche est null
        val title    = intent.getStringExtra(EXTRA_TITLE) ?: "Créneau"
        val startMs  = intent.getLongExtra(EXTRA_START_MS, System.currentTimeMillis())
        val startLdt = LocalDateTime.ofInstant(Instant.ofEpochMilli(startMs), ZoneId.systemDefault()) // Conversion heure humaine

        NotificationHelper.notifySlotStart(context, itemId, title, calendarTitle, startLdt) // Envoi de la notification

        val pending = goAsync() // Demande à android du temps supp après la fin officielle du broadcast
        CoroutineScope(Dispatchers.IO).launch { // Dispatchers.IO ~ threads dédiés aux opérations lentes (hors du CPU)
            try {
                UnreadBadgeStore.increment(context) // Mémoire persistante du nombre de notifications qui n'ont pas été vues.

                // Rolling scheduling: On programme un seul événement à la fois dès que le précédent a été notifié.
                val mainId = MainCalendarStore.mainIdFlow(context).first()
                if (!mainId.isNullOrBlank()) {
                    val sc = CalendarRepository.load(context, mainId)
                    val items = sc?.items ?: emptyList ()
                    EventAlarmScheduler.scheduleFor(context, items)
                }
                // Bloc finally s'exécute toujours (même si exception/return/crash
            } finally { pending.finish() } // Signal pour libérer le Receiver
        }
    }

    companion object {
        const val EXTRA_ID = "item_id"
        const val EXTRA_TITLE = "item_title"
        const val EXTRA_START_MS = "item_start_ms"
        const val EXTRA_CALENDAR_TITLE =
        const val ACTION = "notifications.EVENT_ALARM"
    }
}
