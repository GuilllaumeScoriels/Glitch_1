package planner
/**
 * Scheduler
 *
 * Rôle :
 * Ce composant est responsable de la génération du planning à partir
 * des paramètres temporels du calendrier et, éventuellement, d’une
 * liste de fichiers à planifier.
 *
 * Il constitue le cœur de la logique métier de planification :
 * - il transforme une configuration de calendrier (dates, horaires,
 *   jours travaillés, durée des créneaux, règles de pause)
 *   en une liste ordonnée de créneaux planifiés (`ScheduleItem`)
 * - il ne dépend ni de l’UI ni du ViewModel, et peut être utilisé
 *   indépendamment (tests, réutilisation, etc.)
 *
 * Fonctionnalités principales :
 * - Génération d’un planning à créneaux fixes (mode sans fichiers)
 * - Génération d’un planning basé sur des fichiers/lectures
 *   (mode avec fichiers), avec :
 *     • prise en compte de la difficulté et des répétitions
 *     • découpage des lectures en séances
 *     • répartition des séances dans le temps
 *     • respect de la durée maximale d’un créneau
 *     • gestion des pauses après un nombre donné de créneaux
 *
 * Le Scheduler ne conserve aucun état persistant :
 * chaque appel produit un planning complet à partir des paramètres fournis.

ScheduleItem représente un élément planifié dans le calendrier,
c'est une modèle métier, une unité atomique du planning.
data class ScheduleItem est dans un autre fichier.
 */

import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.UUID
import Calendars.Calendrier

object Scheduler {
    fun buildSchedule(
        startDate: LocalDate,
        endDate: LocalDate,
        workDays: Set<DayOfWeek>,
        dayStart: LocalTime,
        dayEnd: LocalTime,
        slotMinutes: Int,
        breakEverySlots: Int,
        breakMinutes: Int
    ): List<ScheduleItem> {
        val items = mutableListOf<ScheduleItem>()
        var date = startDate
        while (!date.isAfter(endDate)) {
            if (workDays.contains(date.dayOfWeek)) {
                var cursor = LocalDateTime.of(date, dayStart)
                val limit = LocalDateTime.of(date, dayEnd)
                var count = 0
                while (cursor < limit) {
                    val remaining = Duration.between(cursor, limit).toMinutes().toInt()
                    if (remaining <= 0) break
                    val dur = minOf(slotMinutes, remaining)

                    items += ScheduleItem(
                        type = ScheduleItemType.WORK,
                        id = UUID.randomUUID().toString(),
                        start = cursor,
                        durationMinutes = dur,
                        title = "Créneau",
                        notes = ""
                    )

                    cursor = cursor.plusMinutes(dur.toLong())
                    count += 1
                    cursor = addBreakIfNeeded(
                        items = items,
                        cursor = cursor,
                        limit = limit,
                        breakEverySlots = breakEverySlots,
                        breakMinutes = breakMinutes,
                        workSlotCount = count

                    )
                }
            }
            date = date.plusDays(1)
        }
        return items
    }

    fun buildScheduleForFiles(
        startDate: LocalDate,
        endDate: LocalDate,
        workDays: Set<DayOfWeek>,
        dayStart: LocalTime,
        dayEnd: LocalTime,
        files: List<PlannedFile>,
        breakEverySlots: Int,
        breakMinutes: Int,
        slotMinutes : Int
    ): List<ScheduleItem> {
        if (files.isEmpty()) {
            return emptyList()
        }
        val filesOrdered = distributeForSpacing(files, startDate, endDate, workDays)

        val items = mutableListOf<ScheduleItem>()

        // Répartit "total" en "parts" entiers aussi égaux que possible (ex: 10 → [3,3,2,2]).
        fun splitEvenly(total: Int, parts: Int): MutableList<Int> {
            val p = if (parts < 1) 1 else parts
            val base = total / p
            val rem = total % p
            val out = MutableList(p) { base }
            for (i in 0 until rem) out[i] = out[i] + 1
            // 1 minute minimum par séance
            for (i in 0 until out.size) if (out[i] < 1) out[i] = 1
            return out
        }

        fun splitByFixedLength(totalMinutes: Int, perSession: Int): MutableList<Int> {
            if (totalMinutes <= 0) return mutableListOf()
            if (perSession <= 0) return mutableListOf(totalMinutes)

            val full = totalMinutes / perSession
            val rem = totalMinutes % perSession
            val parts = MutableList(full) { perSession }
            if (rem > 0) parts.add(rem)
            // Garantit au moins 1 séance si total > 0
            return if (parts.isEmpty()) mutableListOf(totalMinutes) else parts
        }

        var date = startDate
        var fileIndex = 0
        var count = 0
        var pendingSessions: MutableList<Int>? = null
        var pendingFile: PlannedFile? = null
        var pendingTotalSessions: Int? = null

        while (!date.isAfter(endDate) && (fileIndex < filesOrdered.size || pendingSessions != null)) {
            if (workDays.contains(date.dayOfWeek)) {
                var cursor = LocalDateTime.of(date, dayStart)
                val limit = LocalDateTime.of(date, dayEnd)

                while (cursor < limit && (fileIndex < filesOrdered.size || pendingSessions != null)) {

                    // Prépare la série de séances à poser
                    if (pendingSessions == null) {
                        val f = filesOrdered[fileIndex]
                        val dur = DifficultyUtils.effectiveMinutes(f.durationMinutes, f.difficulty)
                        val eff = dur  // minutes effectives déjà calculées ligne 108

                        pendingSessions =
                            if (f.sessionDurationMinutes != null && f.sessionDurationMinutes > 0) {
                                // Durée par séance fixée -> on découpe en blocs fixes ; la dernière peut être plus courte.
                                splitByFixedLength(eff, f.sessionDurationMinutes)
                            } else {
                                val sessions = if (f.sessionsPerRead < 1) 1 else f.sessionsPerRead
                                splitEvenly(eff, sessions)
                            }
                        pendingFile = f // le fichier en cours de plannification
                        pendingTotalSessions = pendingSessions!!.size
                    }

                    val ps = pendingSessions!! // liste de durées qui représentent les séances qu'il reste à placer pour un fichier.
                    // !! est l'opérateur d'assertion de non nullité, qui sert à forcer une valeur nullable à être considérée comme non null
                    if (ps.isEmpty()) {
                        pendingSessions = null
                        pendingFile = null
                        pendingTotalSessions = null
                        fileIndex += 1
                        continue
                    }
                    else { // s'arrête si toutes les séances sont déjà placées
                        val sd = ps.first() // durée de la première séance qu'on essaye de placer
                        val available = Duration.between(cursor, limit).toMinutes().toInt()
                        if (available <= 0) break

                        val slotDur = minOf(slotMinutes, available)

                        val planned = minOf(sd, slotDur)
                        val end = cursor.plusMinutes(planned.toLong()) // calcule l'heure de fin

                        val total = pendingTotalSessions ?: 1 // si null on met 1, par sécurité
                        val index = total - ps.size + 1 // numéro de la scéance actuelle
                        val base = "lecture de ${pendingFile!!.displayName}"
                        val note = when{
                            total > 1 && planned < sd -> "$base, séance numéro $index (suite demain)"
                            total > 1 -> "$base, séance numéro $index"
                            planned < sd && end == limit -> "$base (suite demain)"
                            else -> base
                        }

                        items += ScheduleItem( // ajoute un créneau planifié à l'heure cursor
                            type = ScheduleItemType.WORK,
                            id = UUID.randomUUID().toString(),
                            start = cursor,
                            durationMinutes = planned,
                            title = "Créneau",
                            notes = note
                        )
                        cursor = end
                        count += 1 //#slots planifiés (utile pour les pauses)
                        cursor = addBreakIfNeeded(
                            items = items,
                            cursor = cursor,
                            limit = limit,
                            breakEverySlots = breakEverySlots,
                            breakMinutes = breakMinutes,
                            workSlotCount = count
                        )
                        if (planned == sd) {
                            ps.removeAt(0)
                        } else {
                            ps[0] = sd - planned
                        }
                    }

                    // Si toutes les séances de cette lecture sont posées, on passe à la suivante
                    if (ps.isEmpty()) {
                        pendingSessions = null
                        pendingFile = null
                        pendingTotalSessions = null
                        fileIndex += 1
                    }
                }
            }
            date = date.plusDays(1)
        }
        return items
    }


    /** Calcule la liste des jours de travail entre deux dates (incluses). */
    private fun workDates(
        startDate: LocalDate,
        endDate: LocalDate,
        workDays: Set<DayOfWeek>
    ): List<LocalDate> {
        val out = mutableListOf<LocalDate>()
        var d = startDate
        while (!d.isAfter(endDate)) {
            if (workDays.contains(d.dayOfWeek)) out += d
            d = d.plusDays(1)
        }
        return out
    }

    /**
     * Réordonne la liste (éventuellement avec doublons) pour respecter :
     *  - Les 2 premières lectures d’un même fichier à ~4 jours d’intervalle.
     *  - Les suivantes le plus espacées possible jusqu’à la fin du planning.
     * Si aucun fichier n’est répété, on conserve l’ordre d’origine.
     */
    private fun distributeForSpacing(
        files: List<PlannedFile>,
        startDate: LocalDate,
        endDate: LocalDate,
        workDays: Set<DayOfWeek>
    ): List<PlannedFile> {
        if (files.isEmpty()) return emptyList()
        val names = files.map { it.displayName }
        if (names.toSet().size == names.size) return files // aucun doublon -> ordre inchangé

        val days = workDates(startDate, endDate, workDays)
        if (days.isEmpty()) return files

        data class Slot(val dayIndex: Int, val file: PlannedFile)

        val byName = files.groupBy { it.displayName }
        val slots = mutableListOf<Slot>()

        for ((_, list) in byName) {
            val n = list.size
            if (n == 1) {
                slots += Slot(0, list[0])
            } else {
                val firstIndex = 0
                val firstDate = days[firstIndex]
                val targetSecondDate = firstDate.plusDays(4)
                var secondIndex = days.indexOfFirst { !it.isBefore(targetSecondDate) }
                if (secondIndex == -1) secondIndex = minOf(days.lastIndex, 1)

                slots += Slot(firstIndex, list[0])
                slots += Slot(secondIndex, list[1])

                val remaining = n - 2
                if (remaining > 0) {
                    val lastIndex = days.lastIndex
                    val available = (lastIndex - secondIndex)
                    val step = if (available > 0) available.toDouble() / (remaining + 1) else 1.0
                    for (i in 1..remaining) {
                        val idxFloat = secondIndex + i * step
                        val idx = kotlin.math.round(idxFloat).toInt()
                        val idxClamped = idx.coerceIn(secondIndex, lastIndex)
                        slots += Slot(idxClamped, list[i + 1])
                    }
                }
            }
        }

        // On trie par « jour cible », puis par nom, et on projette la liste finale
        return slots
            .sortedWith(
                compareBy<Slot> { it.dayIndex }
                    .thenBy { it.file.importance }   // 1 = prioritaire
                    .thenBy { it.file.displayName }
            )
            .map { it.file }
    }

}

private fun addBreakIfNeeded(
    items: MutableList<ScheduleItem>,
    cursor: LocalDateTime,
    limit: LocalDateTime,
    breakEverySlots: Int,
    breakMinutes: Int,
    workSlotCount: Int
): LocalDateTime {
    if (breakEverySlots <= 0) return cursor
    if (breakMinutes <= 0) return cursor
    if (workSlotCount <= 0) return cursor
    if (workSlotCount % breakEverySlots != 0) return cursor

    val available = Duration.between(cursor, limit).toMinutes().toInt()
    if (available <= 0) return cursor

    val pauseDur = minOf(breakMinutes, available)
    if (pauseDur <= 0) return cursor

    items += ScheduleItem(
        type = ScheduleItemType.PAUSE,
        id = UUID.randomUUID().toString(),
        start = cursor,
        durationMinutes = pauseDur,
        title = "Pause",
        notes = ""
    )
    return cursor.plusMinutes(pauseDur.toLong())
}
