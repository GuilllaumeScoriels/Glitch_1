package Notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.lecturemotparmotapp.MainActivity
import your.pkg.ui.navigation.Routes
import java.time.LocalDateTime
import java.time.ZoneId

object NotificationHelper {
    const val CHANNEL_ID = "calendar_slots"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mgr = context.getSystemService(NotificationManager::class.java)
            val ch = NotificationChannel(
                CHANNEL_ID,
                "Créneaux du calendrier",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alerte au début des créneaux planifiés"
                enableLights(true); lightColor = Color.RED
                enableVibration(true)
                setShowBadge(true)
            }
            mgr.createNotificationChannel(ch)
        }
    }

    fun notifySlotStart(
        context: Context,
        itemId: String,
        slotTitle: String,
        start: LocalDateTime
    ) {
        ensureChannel(context)

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("navigate_to", Routes.NOTIFICATIONS)
        }
        val openPi = PendingIntent.getActivity(
            context, 100, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val whenMillis = start.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

        val notif = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle("C’est l’heure de s’atteler à la tâche suivante")
            .setContentText(slotTitle.ifBlank { "Créneau" })
            .setContentIntent(openPi)
            .setWhen(whenMillis)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setOnlyAlertOnce(true)
            .build()

        NotificationManagerCompat.from(context).notify(itemId.hashCode(), notif)
    }
}
