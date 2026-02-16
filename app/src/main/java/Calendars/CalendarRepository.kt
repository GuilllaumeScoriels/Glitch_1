package Calendars

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import planner.ScheduleItem
import planner.ScheduleStatus
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

data class CalendarSummary(
    val id: String,
    val title: String,
    val updatedAt: LocalDateTime,
    val count: Int
)

data class StoredCalendar(
    val id: String,
    val title: String,
    val items: List<ScheduleItem>,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime
)

object CalendarRepository {
    private const val DIR_NAME = "calendars"
    private const val EXT = ".json"
    private val ISO = DateTimeFormatter.ISO_LOCAL_DATE_TIME

    private fun dir(context: Context): File {
        val d = File(context.filesDir, DIR_NAME)
        if (!d.exists()) d.mkdirs()
        return d
    }

    private fun fileFor(context: Context, id: String) =
        File(dir(context), "$id$EXT")

    /** Crée un nouveau calendrier avec un titre et retourne l'id. */
    fun saveNew(context: Context, title: String, items: List<ScheduleItem>): String {
        val id = UUID.randomUUID().toString()
        val now = LocalDateTime.now()
        val o = JSONObject().apply {
            put("id", id)
            put("title", title.ifBlank { "Calendrier" })
            put("createdAt", now.format(ISO))
            put("updatedAt", now.format(ISO))
            put("items", items.toJsonArray())
        }
        fileFor(context, id).writeText(o.toString(2))
        return id
    }

    /** Écrase un calendrier existant (peut aussi renommer). */
    fun saveOverwrite(context: Context, id: String, title: String?, items: List<ScheduleItem>) {
        val f = fileFor(context, id)
        val now = LocalDateTime.now()
        val prev = if (f.exists()) JSONObject(f.readText()) else JSONObject().put("id", id)
        val new = JSONObject().apply {
            put("id", id)
            put("title", (title ?: prev.optString("title")).ifBlank { "Calendrier" })
            put("createdAt", prev.optString("createdAt", now.format(ISO)))
            put("updatedAt", now.format(ISO))
            put("items", items.toJsonArray())
        }
        f.writeText(new.toString(2))
    }

    fun rename(context: Context, id: String, newTitle: String) {
        val sc = load(context, id) ?: return
        saveOverwrite(context, id, newTitle, sc.items)
    }

    fun delete(context: Context, id: String) {
        fileFor(context, id).delete()
    }

    fun list(context: Context): List<CalendarSummary> {
        val d = dir(context)
        return d.listFiles { f -> f.isFile && f.name.endsWith(EXT) }
            ?.mapNotNull { f ->
                runCatching {
                    val o = JSONObject(f.readText())
                    CalendarSummary(
                        id = o.getString("id"),
                        title = o.optString("title", "Calendrier"),
                        updatedAt = LocalDateTime.parse(o.getString("updatedAt"), ISO),
                        count = o.optJSONArray("items")?.length() ?: 0
                    )
                }.getOrNull()
            }?.sortedByDescending { it.updatedAt } ?: emptyList()
    }

    fun load(context: Context, id: String): StoredCalendar? {
        val f = fileFor(context, id)
        if (!f.exists()) return null
        val o = JSONObject(f.readText())
        val items = o.getJSONArray("items").toScheduleItems()
        return StoredCalendar(
            id = o.getString("id"),
            title = o.optString("title", "Calendrier"),
            items = items,
            createdAt = LocalDateTime.parse(o.getString("createdAt"), ISO),
            updatedAt = LocalDateTime.parse(o.getString("updatedAt"), ISO)
        )
    }

    /* ---------- Helpers JSON ---------- */

    private fun List<ScheduleItem>.toJsonArray(): JSONArray {
        val arr = JSONArray()
        forEach { item ->
            arr.put(JSONObject().apply {
                put("id", item.id)
                put("start", item.start.format(ISO))
                put("durationMinutes", item.durationMinutes)
                put("title", item.title)
                put("notes", item.notes)
                put("status", item.status.name)
            })
        }
        return arr
    }

    private fun JSONArray.toScheduleItems(): List<ScheduleItem> {
        val out = ArrayList<ScheduleItem>(length())
        for (i in 0 until length()) {
            val o = getJSONObject(i)
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
