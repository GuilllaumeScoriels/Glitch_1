package Calendars

import planner.PlannedFile
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

class Calendrier (
    val startDate: LocalDate,
    val endDate: LocalDate,
    val workDays: Set<DayOfWeek>,
    val dayStart: LocalTime,
    val dayEnd: LocalTime,
    val files: List<PlannedFile>,
    var breakEverySlots: Int,
    var breakMinutes: Int,
    val slotMinutes : Int
){

    init {
        require(!endDate.isBefore(startDate)) { "endDate must be >= startDate" }
        require(workDays.isNotEmpty()) { "workDays must not be empty" }
        require(dayEnd.isAfter(dayStart)) { "dayEnd must be after dayStart" }
        require(breakEverySlots >= 0) { "breakEverySlots must be >= 0" }
        require(breakMinutes >= 0) { "breakMinutes must be >= 0" }
        require(slotMinutes >= 0) {"slotMinutes must be >= 0"}
    }
}