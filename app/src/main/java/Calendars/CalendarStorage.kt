package Calendars

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import planner.ScheduleItem
import planner.ScheduleStatus
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

object CalendarStorage {
    private const val FILE_NAME = "calendar.json"
    private val ISO: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME

    fun save(context: Context, items: List<ScheduleItem>) {
        val arr = JSONArray()
        items.forEach { item ->
            val o = JSONObject().apply {
                put("id", item.id)
                put("start", item.start.format(ISO))
                put("durationMinutes", item.durationMinutes)
                put("title", item.title)
                put("notes", item.notes)
                put("status", item.status.name)
            }
            arr.put(o)
        }
        File(context.filesDir, FILE_NAME).writeText(arr.toString(2))
    }

    fun load(context: Context): List<ScheduleItem> {
        val file = File(context.filesDir, FILE_NAME)
        if (!file.exists()) return emptyList()
        val text = file.readText()
        val arr = JSONArray(text)
        val out = ArrayList<ScheduleItem>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            out += ScheduleItem(
                id = o.getString("id"),
                start = LocalDateTime.parse(o.getString("start"), ISO),
                durationMinutes = o.getInt("durationMinutes"),
                title = o.optString("title", "Créneau"),
                notes = o.optString("notes", ""),
                status = runCatching { ScheduleStatus.valueOf(o.optString("status", "NONE")) }
                    .getOrElse { ScheduleStatus.NONE }
            )
        }
        return out.sortedBy { it.start }
    }
}
