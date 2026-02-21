package Notifications
/**

* ================================================================
* NotificationHelper
* ================================================================
* Rôle :
* Ce fichier centralise toute la logique d’AFFICHAGE des notifications
* Android de l’application.
*
* Il ne décide PAS quand envoyer une notification.
* -> Les AlarmReceiver / Scheduler déclenchent l’appel
* -> Ce fichier construit et affiche réellement la notification système
*
* Responsabilités principales :
* 1. Créer le canal de notification Android (obligatoire depuis Android 8+)
* 2. Construire la notification (titre, texte, icône, comportement)
* 3. Définir l’action au clic (ouvrir l’app sur l’écran notifications)
* 4. Envoyer la notification au système Android
*
* Architecture :
* L’application suit le pattern suivant :
*
* Scheduler / Receiver  --->  NotificationHelper  --->  Android System UI
* ```
   (QUAND ?)                 (COMMENT ?)             (AFFICHAGE)
  ```
*
* Ce fichier doit rester purement technique et ne contenir
* aucune logique métier ou décision temporelle.
*
* Important :
* Toute modification du texte, son, vibration, priorité,
* icône ou comportement au clic doit être faite ici.
*

*/

import Calendars.Calendrier
import android.Manifest
import com.example.a18.R
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import androidx.annotation.RequiresPermission
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.lecturemotparmotapp.MainActivity
import kotlinx.coroutines.launch
import your.pkg.ui.navigation.Routes
import java.time.LocalDateTime
import java.time.ZoneId
import androidx.core.graphics.drawable.toBitmap

object NotificationHelper { // Unique gestionnaire de notifications pour l'app
    const val CHANNEL_ID = "calendar_slots" // Identifiant canal android; utilisateur choisit l'activation de ce canal

    fun ensureChannel(context: Context) { // Créer le canal de notifications
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mgr = context.getSystemService(NotificationManager::class.java)
            val ch = NotificationChannel(
                CHANNEL_ID,
                "Créneaux du calendrier",
                NotificationManager.IMPORTANCE_HIGH // Notif. intrusives (popup + son + tête haute)
            ).apply {
                description = "Alerte au début des créneaux planifiés"
                enableLights(true); lightColor = Color.RED
                enableVibration(true)
                setShowBadge(true)
            }
            mgr.createNotificationChannel(ch) // Création réelle
        }
    }

    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    fun notifySlotStart( // Fonction qui affiche la notif.
        context: Context,
        itemId: String,
        slotTitle: String,
        calendarTitle: String,
        start: LocalDateTime
    ) {
        ensureChannel(context)

        // Ouverture de l'app au clic
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("navigate_to", Routes.NOTIFICATIONS)
        }

        // Ticket pour android pour ouvrir au clic
        val openPi = PendingIntent.getActivity(
            context, 100, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Afficher l'heure dans la notif. et trier correctement
        val whenMillis = start.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

        /* Créer la largeIcon utilisée ci-dessous:
        * context = point d'accès à l'environnement android
          packageManager = service système qui connait toutes les apps installées et leurs métadonnées.
        * context.packageName = nom du package de l'app.
        * getApplicationicon récupère l'icône officielle de l'appli, renvoie un Drawable.
        * toBitmap permet de transformer le drawable en format accepté par setLargeIcon().
         */
        val largeIcon = context.packageManager
            .getApplicationIcon(context.packageName)
            .toBitmap()

        // Assemblage de la notif.
        val notif = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_small) // monochrome
            .setLargeIcon(largeIcon) // couleur
            .setContentTitle("C’est l’heure de s’atteler à la tâche suivante")
            .setSubText(calendarTitle.ifBlank { "Calendrier principal"})
            .setContentText(slotTitle.ifBlank { "Créneau" })
            .setContentIntent(openPi)
            .setWhen(whenMillis)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setOnlyAlertOnce(true)
            .build()

        // Envoi au système android
        NotificationManagerCompat.from(context).notify(itemId.hashCode(), notif)

        // Ecriture DB:
        val entity = AppNotificationEntity(
            key = "SLOT_START|$itemId|$whenMillis", // identifiant unique de la notification dans la base.
            type = "SLOT_START", // pour filtrer les notifications de créneau/les autres
            title = calendarTitle.ifBlank {"Calendrier"}, // Stocker les informations de la notif pour les remontrer à l'utilisateur.
            texte = slotTitle.ifBlank {"Créneau"}, // ifBlank = fonction kotlin, sexécute si vide ou seulement espaces.
            timestamp = whenMillis
        )
        /* Android interdit l'accès disque sur le thread UI => lancer Room sur une coroutine.
        UI thread: afficher
        IO thread: écrire en base.
         */

        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch { // Exécuter l'instruction en arrière-plan sans bloquer l'app.
            AppNotificationsRepository.get(context).upsert(entity)
        }
    }
}
