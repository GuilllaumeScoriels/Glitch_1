package Exports

import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * IcsCalendarExporter
 * ---------------------------------------------------------
 * Responsabilité :
 * Transformer une liste de ScheduleItem (créneaux du planner)
 * en un fichier .ics (format iCalendar) importable dans Googlg Calendar.
 *
 * Pourquoi:
 * - Pas besoin d'API Google
 * - Pas besoin de login
 * - Compatible avec Google Calendar/ Outlook/ Apple Calendar
 */
object IcsCalendarExporter {

    private val dtStampUtcFormatter =
        DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneId.of("UTC"))
}