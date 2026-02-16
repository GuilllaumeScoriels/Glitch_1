package Notifications
/**
 * EventAlarmScheduler
 * ---------------------------------------------------------
 * Cœur temporel du système de notifications.
 *
 * Responsabilité :
 * Traduire des ScheduleItem (logiques) en alarmes Android (physiques).
 *
 * Ce module transforme :
 *    "un créneau commence/termine à 14:30"
 * en :
 *    "le système doit réveiller l’app à 14:30 même téléphone verrouillé"
 *
 * Fonctionnement :
 * - Parcourt les créneaux du calendrier
 * - Programme deux alarmes par créneau :
 *      START → début du travail
 *      END   → fin du créneau
 * - Utilise setExactAndAllowWhileIdle pour ignorer Doze mode
 *
 * Contraintes gérées :
 * - Evite de programmer dans le passé
 * - Limite aux 7 prochains jours (performance + OS restrictions)
 * - Gère Android 12+ exact alarm permission
 *
 * Architecture :
 * Planner → ScheduleItem → EventAlarmScheduler → AlarmManager → EventAlarmReceiver → NotificationHelper
 *
 * Ce fichier ne crée aucune notification :
 * il déclenche uniquement un réveil système.
 */

import android.app.AlarmManager
import android.app.AlarmManager.AlarmClockInfo
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.google.common.io.Files.map
import org.bouncycastle.asn1.cmc.CMCStatus.pending
import planner.ScheduleItem
import java.time.ZoneId
import java.time.ZonedDateTime // Conversion heure humaine/ heure machine

object EventAlarmScheduler { // Un seul singleton scheduler de notifications

    fun scheduleFor(context: Context, items: List<ScheduleItem>) {
        val am = context.getSystemService(AlarmManager::class.java)
        val now = System.currentTimeMillis() // heure machine actuelle en millisecondes depuis 1970

        // Filtrer + trier les créneaux futurs
        val next = items
            .asSequence() /* Transforme la liste en flux paresseux:
            Chaque élément est traité un par un au lieu de créer des listes intermédiaires,
            c'est donc moins coûteux car il n'y a aucune liste temporaire. */
            .map {it to ZonedDateTime.of(it.start, ZoneId.systemDefault()).toInstant().toEpochMilli() }
            // ScheduleItem transformés en paires (ScheduleItem, heure machine)
            .filter { (_,at) -> at > now } // Eliminer tout ce qui est déjà passé
            .minByOrNull { (_,at) -> at} /* On cherche le timestamp le plus petit
            parmi ceux dans le futur. (Timestamp = heure en millis depuis 1970)
            Liste pas triée, juste scannée (plus rapide) */
            ?: run { // ( A?:B signifie utiliser A si non null, sinon exécuter B)
                // Run pour exécuter plusieurs lignes dans un contexte d'expression
                // Rien à programmer -> On annule l'éventuelle alarme "NEXT"
                val pi = pending(context, id = "", title = "", at = 0L)
                am.cancel(pi) // Supprimer réveil futur
                return // Sortir de ScheduleNext
            }

        val (item, at) = next

        // On fabrique un pendingIntent qu'Android ouvrira plus tard.
        val pi = pending(context, item.id, item.title, at)
        am.cancel(pi) // Suppression ancienne alarme

        try { /* Try / catch pour éviter qu'une seule erreur -> boucle infinie
            Le système peut refuser l'exécution pour des raisons externes au code
            Si android reduse l'action demandée, l'exception ne remonte pas (ce qui
            causerait le crash), évitant qu'une notification ne mène au crash total.
            Transforme un erreur système fatale en simple échec fonctionnel local.
            */
            /* Alarme exacte si autorisé par l'appareil, sinon on demande à android de
            traiter l'alarme comme un réveil pour lui attribuer un traitement privilégié.
            C'est cette partie qui génère la notification android.
            */
            if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) {
                am.setAlarmClock(AlarmClockInfo(at, pi), pi)
            } else {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            }
        } catch (_: SecurityException) {
            // Fallback anti-crash si l'OS refuse les exact alarms sur cet appareil
            try {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            } catch (_: Throwable) {
                // On ne crash jamais l'app pour une notification
            }
        } catch (_: IllegalStateException) {
            // Typiquement "Too many alarms ... " selon OEM -> on ne crash pas
        }
    }

    private fun pending(context: Context, id: String, title: String, at: Long): PendingIntent {
        // Création du message à envoyer dans le futur.

        val intent = Intent(context, EventAlarmReceiver::class.java).apply { // Android lance EventAlarmReceiver quand l'heure arrive
            /*Intent = message déclaratif: ne contient pas de code, mais contient quoi faire
            et avec quelles données. Dit à Android que la classe qu'il faudra recréer est EventAlarmReceiver,
            même si l'app est morte.
             */
            action = EventAlarmReceiver.ACTION
            /* Identifie que c'est une alarme de planning,
            Un broadcast pouvant servir à plain de choses.
             */
            /* Les extras = les données du colis sous forme de paires
            clé/valeur sérialisées, stockées dans la mémoire système pour les restaurer plus tard.
            Données primitives stockées dans la mémoire persistante.
             */
            putExtra(EventAlarmReceiver.EXTRA_ID, id)
            putExtra(EventAlarmReceiver.EXTRA_TITLE, title)
            putExtra(EventAlarmReceiver.EXTRA_START_MS, at)
        }
        return PendingIntent.getBroadcast(
            /* Un PendingIntent est un message scellé que le système ouvrira plus tard même si l'app est morte
            Contrairement à un simplie Intent qui est stocké par l'app, le PendingIntent va dans la mémoire
            du système android.
            */
            context,
            (id + "@"+ at).hashCode(), // Construction identifiant unique
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            /* FLAG_UPDATE_CURRENT met à jour le contenu d'une potentielle alarme existante s'il en
            existe une identique.
            FLAG_IMMUTABLE empêche une autre app de modifier le message.
             */
        )
    }
}
