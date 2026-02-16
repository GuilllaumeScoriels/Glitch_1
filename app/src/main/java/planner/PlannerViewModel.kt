package planner

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.*

data class PlannerFormState(
    val startDate: LocalDate = LocalDate.now(),
    val endDate: LocalDate = LocalDate.now().plusWeeks(2),
    val workDays: Set<DayOfWeek> = setOf(
        DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
        DayOfWeek.THURSDAY, DayOfWeek.FRIDAY
    ),
    val dayStart: LocalTime = LocalTime.of(9, 0),
    val dayEnd: LocalTime = LocalTime.of(17, 0),
    val slotMinutes: Int = 50,
    val breakEverySlots: Int = 1,
    val breakMinutes: Int = 10
)

class PlannerViewModel : ViewModel() {

    private suspend fun pushCompletionIfHasId(items: List<ScheduleItem>) {
        val id = currentCalendarId.value
        if (id.isNullOrBlank()) return
        try { social.RemoteCalendarService.updateCompletionIfPublished(id, items) } catch (_: Throwable) {}
    }

    private val _form = MutableStateFlow(PlannerFormState())
    val form = _form.asStateFlow()

    private val _items = MutableStateFlow<List<ScheduleItem>>(emptyList())
    val items = _items.asStateFlow()

    private val _currentCalendarId = MutableStateFlow<String?>(null)
    val currentCalendarId = _currentCalendarId.asStateFlow()

    // Sélection des fichiers à planifier (mode "par fichiers")
    private val _selectedFiles = MutableStateFlow<List<PlannedFile>>(emptyList())
    val selectedFiles = _selectedFiles.asStateFlow()
    fun addPlannedFile(file: PlannedFile) {
        _selectedFiles.value = _selectedFiles.value + file
    }
    fun removePlannedFile(displayName: String) {
        _selectedFiles.value = _selectedFiles.value.filterNot { it.displayName == displayName }
    }
    fun clearSelectedFiles() {
        _selectedFiles.value = emptyList()
    }

    fun updateRepeatCount(displayName: String, count: Int) {
        _selectedFiles.value = _selectedFiles.value.map {
            if (it.displayName == displayName)
                it.copy(repeatCount = if (count < 1) 1 else count)
            else it
        }
    }

    fun updateDifficulty(displayName: String, difficulty: Int) {
        _selectedFiles.value = _selectedFiles.value.map { pf ->
            if (pf.displayName == displayName) {
                val diff = difficulty.coerceIn(1, 5)
                val eff = DifficultyUtils.effectiveMinutes(pf.durationMinutes, diff)
                // Conserver le NOMBRE de séances, ajuster la durée/séance
                val s = pf.sessionsPerRead.coerceAtLeast(1)
                val per = kotlin.math.ceil(eff / s.toDouble()).toInt().coerceAtLeast(1)
                pf.copy(difficulty = diff, sessionDurationMinutes = per)
            } else pf
        }
    }

    fun updateSessionsPerRead(displayName: String, sessions: Int) {
        _selectedFiles.value = _selectedFiles.value.map { it ->
            if (it.displayName == displayName) {
                val s = sessions.coerceIn(1, 12)
                val eff = DifficultyUtils.effectiveMinutes(it.durationMinutes, it.difficulty)
                val per = kotlin.math.ceil(eff / s.toDouble()).toInt().coerceAtLeast(1)
                it.copy(
                    sessionsPerRead = s,
                    sessionDurationMinutes = per // on bascule en "piloté par N séances"
                )
            } else it
        }
    }

    fun updateSessionDuration(displayName: String, minutes: Int) {
        _selectedFiles.value = _selectedFiles.value.map { it ->
            if (it.displayName == displayName) {
                val m = minutes.coerceAtLeast(1)
                val eff = DifficultyUtils.effectiveMinutes(it.durationMinutes, it.difficulty)
                val s = kotlin.math.ceil(eff / m.toDouble()).toInt().coerceAtLeast(1)
                it.copy(
                    sessionDurationMinutes = m,
                    sessionsPerRead = s
                )
            } else it
        }
    }

    fun updateImportance(displayName: String, level: Int) {
        _selectedFiles.value = _selectedFiles.value.map {
            if (it.displayName == displayName) it.copy(importance = level.coerceIn(1, 3)) else it
        }
    }

    /** Item en cours d’édition (piloté par l’UI). */
    val editingItem = MutableStateFlow<ScheduleItem?>(null)

    /** Snapshot de la liste au début de l’édition (pour aperçu/annulation). */
    private var editBackup: List<ScheduleItem>? = null

    fun update(block: (PlannerFormState) -> PlannerFormState) {
        _form.value = block(_form.value)
    }

    fun regenerate() {
        val s = _form.value
        if (_selectedFiles.value.isNotEmpty()) {
            regenerateFromFiles()
        }
        _items.value = Scheduler.buildSchedule(
            startDate = s.startDate,
            endDate = s.endDate,
            workDays = s.workDays,
            dayStart = s.dayStart,
            dayEnd = s.dayEnd,
            slotMinutes = s.slotMinutes,
            breakEverySlots = s.breakEverySlots,
            breakMinutes = s.breakMinutes
        )
    }

    fun regenerateFromFiles() {
        val s = _form.value
        _items.value = Scheduler.buildScheduleForFiles(
            startDate = s.startDate,
            endDate = s.endDate,
            workDays = s.workDays,
            dayStart = s.dayStart,
            dayEnd = s.dayEnd,
            files = expandFilesByRepeatCount(_selectedFiles.value),
            breakEverySlots = s.breakEverySlots,
            breakMinutes = s.breakMinutes,
            slotMinutes = s.slotMinutes
        )
    }

    private fun expandFilesByRepeatCount(files: List<PlannedFile>): List<PlannedFile> = /* = indique une
    fonction "expression", càd qui retourne directement le résultat de l'expression qui suit sans bloc {}*/
        files.flatMap { pf -> // flatmap transforme chaque élément d'une liste en une liste puis les fusionne en une grande liste
            val w = DifficultyUtils.frequencyWeight(pf.difficulty) // poinds w lié à la difficulté du fichier
            val repeats = maxOf(1, kotlin.math.ceil(pf.repeatCount * w).toInt())
            List(repeats) { pf }
        }

    /* ---------- Edition & aperçu en direct ---------- */

    fun startEditing(itemId: String) {
        val cur = _items.value.sortedBy { it.start }
        editingItem.value = cur.firstOrNull { it.id == itemId }
        editBackup = cur
    }

    fun cancelEditing() {
        editBackup?.let { _items.value = it }
        editBackup = null
        editingItem.value = null
    }

    /** Aperçu instantané avec décalage des suivants du même jour. */
    fun previewItemAndShiftFollowing(temp: ScheduleItem) {
        val base = (editBackup ?: _items.value).sortedBy { it.start }
        val idx = base.indexOfFirst { it.id == temp.id }
        if (idx == -1) return

        val old = base[idx]
        val deltaMinutes = Duration.between(old.end, temp.end).toMinutes().toInt()

        val out = base.toMutableList()
        out[idx] = temp

        if (deltaMinutes != 0) {
            val day = temp.start.toLocalDate()
            var i = idx + 1
            while (i < out.size && out[i].start.toLocalDate() == day) {
                val it = out[i]
                out[i] = it.copy(start = it.start.plusMinutes(deltaMinutes.toLong()))
                i++
            }
        }

        _items.value = out
        editingItem.value = temp
    }

    /** Valide l’édition en cours. */
    fun commitEditing() {
        editBackup = null
        editingItem.value = null
    }

    /* ---------- Utilitaires ---------- */

    fun setItems(newItems: List<ScheduleItem>) {
        _items.value = newItems.sortedBy { it.start }
    }

    fun setCurrentCalendarId(id: String?) {
        _currentCalendarId.value = id
    }

    fun updateItemAndShiftFollowing(updated: ScheduleItem) {
        val cur = _items.value.sortedBy { it.start }
        val idx = cur.indexOfFirst { it.id == updated.id }
        if (idx == -1) return

        val old = cur[idx]
        val deltaMinutes = Duration.between(old.end, updated.end).toMinutes().toInt()

        val out = cur.toMutableList()
        out[idx] = updated

        if (deltaMinutes != 0) {
            val day = updated.start.toLocalDate()
            var i = idx + 1
            while (i < out.size && out[i].start.toLocalDate() == day) {
                val it = out[i]
                out[i] = it.copy(start = it.start.plusMinutes(deltaMinutes.toLong()))
                i++
            }
        }

        _items.value = out
        editingItem.value = null
    }

    fun setStatus(id: String, status: ScheduleStatus) {
        _items.value = _items.value.map { if (it.id == id) it.copy(status = status) else it }
        viewModelScope.launch(Dispatchers.IO) { pushCompletionIfHasId(_items.value) }
    }

    fun updateNotes(id: String, newNotes: String) {
        _items.value = _items.value.map { if (it.id == id) it.copy(notes = newNotes) else it }
    }
}
