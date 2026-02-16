package Calendars

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.text.Typography.dagger

@HiltViewModel
class UserPublishedCalendarsViewModel @Inject constructor(
    private val getUserPublishedCalendars: GetUserPublishedCalendarsUseCase
) : ViewModel() {

    data class UiState(
        val isLoading: Boolean = true,
        val items: List<CalendarSummary> = emptyList()
    )

    private val _state = MutableStateFlow(UiState(isLoading = true))
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            getUserPublishedCalendars.execute().collect { list ->
                _state.value = UiState(isLoading = false, items = list)
            }
        }
    }

    fun onCalendarClick(calendar: CalendarSummary) {
        // Laisse vide pour ne rien casser; la navigation peut être gérée par l'écran si besoin
    }
}
