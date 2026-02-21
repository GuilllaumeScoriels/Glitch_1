package Notifications
/**
 * ================================================================
 * NotificationExtras
 * ================================================================
 *
 * Rôle :
 * Ce fichier centralise toutes les clés utilisées dans les Intent extras
 * liés aux notifications de l’application.
 *
 * Contexte technique :
 * Les notifications de début de créneau ne sont pas déclenchées immédiatement.
 * Elles sont planifiées à l’avance (AlarmManager / WorkManager / PendingIntent).
 *
 * Au moment de la planification, l’application connaît toutes les informations :
 * - identifiant du créneau
 * - titre du créneau
 * - titre du calendrier
 * - heure de début
 *
 * Mais lorsque Android déclenche réellement la notification plus tard,
 * l’application peut être :
 * - fermée
 * - tuée par le système
 * - redémarrée
 *
 * Le BroadcastReceiver reçoit donc seulement un Intent envoyé par Android.
 * La seule manière fiable de transmettre les données jusqu’à ce moment est
 * d’utiliser les "extras" de l’Intent.
 *
 * Pourquoi ce fichier est utile :
 * ----------------------------------------------------------------
 * Sans constantes centralisées :
 * - chaque fichier écrirait ses propres clés ("calendarTitle", "calendar_title", etc.)
 * - une simple faute de frappe empêcherait la récupération des données
 * - les bugs seraient silencieux et difficiles à diagnostiquer
 *
 * Avec NotificationExtras :
 * - toutes les clés sont définies une seule fois
 * - le Scheduler écrit les données
 * - le Receiver les lit de façon sûre
 * - NotificationHelper reste indépendant de la logique métier
 *
 * Cela garantit :
 * - robustesse (fonctionne même si l’app est morte)
 * - maintenabilité (refactor simple)
 * - cohérence entre modules
 *
 * Architecture :
 *
 * Scheduler ---> Intent Extras ---> Receiver ---> NotificationHelper ---> UI + DB
 *
 * Le fichier sert donc de "contrat de communication" entre les composants
 * asynchrones de l’application.
 */

object NotificationsExtras{
    const val EXTRA_ITEM_ID ="extra_item_id" // Identifier la notif/ le slot
    const val EXTRA_SLOT_TITLE = "extra_slot_title" // Titre de la notif
    const val EXTRA_CALENDAR_TITLE ="extra_calendar_title" // Titre du calendrier
    const val EXTRA_START_EPOCH_MILLIS = "extra_start_epoch_millis" // Reconstruire l'heure
}