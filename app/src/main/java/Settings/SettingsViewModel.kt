package Settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val repo: SettingsRepository
) : ViewModel() {

    val uiState: StateFlow<AppSettings> = repo.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppSettings())

    fun onFullScreenChange(enabled: Boolean) = viewModelScope.launch {
        repo.setDefaultFullScreen(enabled)
    }

    fun onWpmChange(wpm: Int) = viewModelScope.launch {
        repo.setWordsPerMinute(wpm)
    }

    fun onTtsEnabledChange(enabled: Boolean) = viewModelScope.launch {
        repo.setTtsEnabled(enabled)
    }

    fun onTtsRateChange(rate: Float) = viewModelScope.launch {
        repo.setTtsRate(rate)
    }

    fun onKeepScreenOnChange(enabled: Boolean) = viewModelScope.launch {
        repo.setKeepScreenOn(enabled)
    }
    fun onDefaultWordDelayChange(ms: Int) = viewModelScope.launch {
        repo.setDefaultWordDelayMs(ms)
    }
    fun onDefaultDifficultyChange(level: Int) = viewModelScope.launch {
        repo.setDefaultDifficulty(level)
    }

    fun onReadMsD1Change(ms: Int) = viewModelScope.launch { repo.setReadMsD1(ms) }
    fun onReadMsD2Change(ms: Int) = viewModelScope.launch { repo.setReadMsD2(ms) }
    fun onReadMsD3Change(ms: Int) = viewModelScope.launch { repo.setReadMsD3(ms) }
    fun onReadMsD4Change(ms: Int) = viewModelScope.launch { repo.setReadMsD4(ms) }
    fun onReadMsD5Change(ms: Int) = viewModelScope.launch { repo.setReadMsD5(ms) }
}
