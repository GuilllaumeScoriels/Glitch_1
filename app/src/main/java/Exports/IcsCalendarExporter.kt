package Exports

import java.time.ZoneId
import java.time.format.DateTimeFormatter
import android.content.Context
import planner.ScheduleItem
import java.io.File
import java.security.MessageDigest
import java.time.ZonedDateTime
import kotlin.text.Regex.Companion.escape

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

    /* Formatter permet de transformer une date/heure interne en texte standardisé,
    càd un langage que tous les calendriers comprennent.
    Nécessaire pour obtenir un format respectant le protocole des ficheirs .ics.

    DTSTAMP est la date de création/édition de l'événement, utile pour éviter doublons...
    Le pattern indique comment écrire la date (année, mois, jour, séparateur obligatoire,
    heure, minute, secondes, indice UTC).
    DTSTAMP toujours en UTC selon la norme iCalendar (fait via .withZone(ZoneId.of("UTC")))
    (Belgique: UTC+1)
     */
    private val dtStampUtcFormatter =
        DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneId.of("UTC"))

    // Création formatter pour les dates locales des événements (début/fin) <-> UTC
    private val localDateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")

    fun exportToCacheFile( // Génère et retourne un fichier .ics
        context: Context, // Context permet l'accès aux système android; ~carte ID android; utile ici pour écrire dans le cache
        items: List<ScheduleItem>, // Créneaux à transformer en événements
        calendarName: String = "Planning Glitch" // Nom par défaut du calendrier lors de l'import
    ): File { // File retourné par la fonction
        val exportsDir =
            File(context.cacheDir, "exports") // Création objet File, écrit dans le cache
        if (!exportsDir.exists()) exportsDir.mkdirs() // mkdir crée le dossier après vérification qu'il n'existe pas encore

        val fileName =
            "planning_${System.currentTimeMillis()}.ics" // Nom unique basé sur l'heure actuelle
        val outFile = File(exportsDir, fileName) // Objet fichier final

        val zone =
            ZoneId.systemDefault() // Récupère le fuseau du téléphone pour ques les heures restent correctes en local
        val dtStamp =
            dtStampUtcFormatter.format(ZonedDateTime.now(ZoneId.of("UTC"))) // Sélectionne "maintenant" en UTC et le formatte

        /* Accumulateur de texte: permet d'écrire à la suite sans devoir réécrire à chaque fois tout ce qui précède.
        Résous le problème de l'immutabilité des strings en Java/Kotlin.
         */
        val builder = StringBuilder()
        builder.append("BEGIN:VCALENDAR\r\n") // Début du fichier iCalendar
        builder.append("VERSION:2.0\r\n") // Version iCalendar attendue par la plupart des calendriers modernes
        builder.append("PRODID:-//Glitch//FR\r\n") // Sert à dire qui a généré le fichier
        builder.append("CALSCALE:GREGORIAN\r\n") // Calendrier grégorien standard
        builder.append("METHOD:PUBLISH\r\n") // Indique que c'est un calendrier publié, pas une invitation meeting request
        builder.append("X-WR-CALNAME:${escape(calendarName)}\r\n") // Nom affiché du calendrier dans certains outils
        // escape protège le texte contre ,,;,... qui ont un sens particulier en iCalendar

        items.sortedBy { it.start }.forEach { item -> // Trie les items par date de début
            val startZoned =
                item.start.atZone(zone) // Convertit le début (start) en ZonedDateTime dans le fuseau local
            val endZoned = item.end.atZone(zone)
            val dtStart =
                localDateTimeFormatter.format(startZoned) // Produit une chaine string YYYYMMDDTHHMMSS
            val dtEnd = localDateTimeFormatter.format(endZoned)
            /* Créér identifiant d'événement unique
            escape protège les caractères spéciaux qui pourraient casser le format
            item.id pour réutiliser le nom de l'item dans son id, garde un lien avec son item source
            randomUUID pour générer un identifiant aléatoire, nécessaire car item.id n'est pas garanti unique
            @Glitch garantit unicité dans le monde (partie de gauche pour l'unicité locale)
            hashCode transforme un objet/string en entier 32bits = raccourci pour ranger les objets*/

            /* ${...} permet de convertir l'intérieur de l'expression par sa valeur convertie en texte.
            | ("caractère pipe") utilisé en tant que séparateur non ambigu (apparait rarement dans texte utilisateur)*/
            val stableKey = "${item.id}|${item.start}|${item.end}|${item.title}"
            val uid = "${sha256(stableKey)}@Glitch"

            // Un VEVENT est un événement unique dans un calendrier
            builder.append("BEGIN:VEVENT\r\n") // Début d'un événement
            builder.append("UID:$uid\r\n")
            builder.append("DTSTAMP:$dtStamp\r\n") // Timestamp de création/ édition
            builder.append("DTSTART;TZID=${zone.id}:$dtStart\r\n") // Date de début avec fuseau explicite
            builder.append("DTEND;TZID=${zone.id}:$dtEnd\r\n")
            builder.append("SUMMARY:${escape(item.title)}\r\n") // Titre visible de l'événement dans le calendrier

            if (item.notes.isNotBlank()) {
                builder.append("DESCRIPTION:${escape(item.notes)}\r\n")
            }

            builder.append("END:VEVENT\r\n")
        }
        builder.append("END:VCALENDAR\r\n")

        outFile.writeText(builder.toString())
        return outFile
    }

    private fun sha256(input: String): String { // Prend stableKey en entrée et renvoie un String
        val bytes = MessageDigest // classe Java qui sait calculer empreintes comme SHA-256
            .getInstance("SHA-256") // Demande au système une instance de SHA-256
            .digest(input.toByteArray(Charsets.UTF_8)) // Applique l'algoritme SHA-256 et renvoie l'empreinte finale sous forme de ByteArray

        return bytes.joinToString("") { "%02x".format(it) } // Concatène en hexadécimal à deux caractères

    }

    private fun escape(value: String): String {
        return value
            .replace("\\", "\\\\")
            .replace("\n", "\\n")
            .replace("\r", "")
            .replace(",", "\\,")
            .replace(";", "\\;")
    }
}