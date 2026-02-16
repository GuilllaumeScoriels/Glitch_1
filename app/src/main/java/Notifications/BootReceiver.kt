package Notifications
/**
 * BootReceiver
 * ---------------------------------------------------------
 * Responsabilité :
 * Reprogrammer toutes les alarmes de créneaux après un redémarrage du téléphone.
 *
 * Pourquoi ?
 * Sur Android, les AlarmManager sont automatiquement supprimés au reboot.
 * Sans ce receiver, aucune notification ne serait envoyée après extinction/allumage.
 *
 * Fonctionnement :
 * 1) Android envoie BOOT_COMPLETED
 * 2) On recharge le calendrier principal sauvegardé
 * 3) On reconstruit les ScheduleItem
 * 4) On redonne tout au EventAlarmScheduler
 *
 * Ce composant garantit la persistance temporelle du système de planification.
 *
 * Dépendances :
 * - MainCalendarStore → récupère le calendrier actif
 * - EventAlarmScheduler → reprogramme les alarmes
 *
 * Important :
 * Doit être déclaré dans le Manifest avec la permission RECEIVE_BOOT_COMPLETED
 */

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import Calendars.CalendarRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val mainId = MainCalendarStore.mainIdFlow(context).first()
                if (!mainId.isNullOrBlank()) {
                    val sc = CalendarRepository.load(context, mainId)
                    val items = sc?.items ?: emptyList()
                    EventAlarmScheduler.scheduleFor(context, items)
                }
            } finally { pending.finish() }
        }
    }
}
