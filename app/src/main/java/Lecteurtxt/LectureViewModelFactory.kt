package Lecteurtxt

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.a18.TextFileImporter
import Settings.SettingsRepository

class LectureViewModelFactory(
    private val importer: TextFileImporter,
    private val settingsRepo: SettingsRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(LectureViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return LectureViewModel(importer, settingsRepo) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
