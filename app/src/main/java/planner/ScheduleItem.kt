package planner

import java.time.LocalDateTime

enum class ScheduleStatus { NONE, FAIT, EN_COURS, PAS_FAIT }

enum class ScheduleItemType {WORK, PAUSE}

/** Modèle unique d’un créneau. */
data class ScheduleItem(
    val type: ScheduleItemType = ScheduleItemType.WORK,
    val id: String,
    val start: LocalDateTime,
    val durationMinutes: Int,
    val title: String = "Créneau",
    val notes: String = "",
    val status: ScheduleStatus = ScheduleStatus.NONE
) {
    val end: LocalDateTime get() = start.plusMinutes(durationMinutes.toLong())
}
