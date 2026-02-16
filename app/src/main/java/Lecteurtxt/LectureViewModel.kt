package Lecteurtxt

import android.net.Uri
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.a18.TextFileImporter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlin.math.roundToLong
import Settings.SettingsRepository
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch


class LectureViewModel(
    private val importer: TextFileImporter,
    private val settingsRepo: SettingsRepository
) : ViewModel() {
    var inputText by mutableStateOf("")
    /*observable, by permet de ne pas écrire le get/set() explicitement.
    va avec le jetpack compose et le concept de recomposition.
    mécanisme qui permet à l'interface utilisateur de se mettre à jour automatiquement
    quand les données changent.
     */
    private val _currentWord = MutableStateFlow("")
    val currentWord: StateFlow<String> get() = _currentWord

    private val _isReading = MutableStateFlow(false)
    val isReading: StateFlow<Boolean> get() = _isReading
    private var cancelReading = false

    private val _words = MutableStateFlow<List<String>>(emptyList())
    val words = _words
    private var currentIndex = 0
    val _currentIndex: Int
        get() = currentIndex

    private var readingJob: Job? = null

    var wpm by mutableStateOf(300)
    private fun delayMsPerWord() = (60_000.0 / wpm).roundToLong()

    private val _prevWord = MutableStateFlow("")
    private val _nextWord = MutableStateFlow("")
    val prevWord: StateFlow<String> get() = _prevWord
    val nextWord: StateFlow<String> get() = _nextWord

    var newtxt: Boolean = false

    var indexInit = 0

    private var wordDelayMillis by mutableStateOf(500)
    init {
        // Synchronise la vitesse de lecture avec la vitesse par défaut des paramètres
        viewModelScope.launch {
            settingsRepo.settings.collect { app ->
                setWordDelay(app.defaultWordDelayMs)
            }
        }
    }

    val _wordDelayMillis: Int get() = wordDelayMillis
    fun setWordDelay(ms: Int) {
        wordDelayMillis = ms.coerceIn(1, 10000)
    }

    // ————— Paramètres (tu peux les exposer via un slider ou un menu) —————
    private var baseDelayMs by mutableStateOf(200)   // délai minimum par mot (ms)
    private var perCharMs   by mutableStateOf(25)    // coût par caractère (ms/char)
    private var minDelayMs  by mutableStateOf(5)    // borne inférieure
    private var maxDelayMs  by mutableStateOf(3000)  // borne supérieure
    private var punctuationBoost by mutableStateOf(1.3f) // boost si .,;:!? à la fin

    // Option : un facteur global venant de ton slider existant (ex: wordDelayMillis) :
    private fun globalSpeedScale(): Float {
        // Exemple :  wordDelayMillis ∈ [5..5000] → on le mappe à un facteur
        // 500 ms ~ 1.0 ; plus petit = plus rapide ; plus grand = plus lent
        val ref = 500f
        return (wordDelayMillis / ref).coerceIn(0.1f, 5f)
    }

    // ————— Calcul du délai par mot —————
    fun computeDelayFor(word: String): Long {
        val len = word.length

        // Base linéaire : délai minimal + coût par caractère
        var delay = baseDelayMs + perCharMs * len

        // Si ponctuation “forte” en fin de mot, on ralentit un peu
        val endsWithPunct = word.lastOrNull()?.let { it in charArrayOf('.', ',', ';', ':', '!', '?') } == true
        if (endsWithPunct) {
            delay = (delay * punctuationBoost).toInt()
        }

        // Appliquer le facteur global (slider vitesse)
        delay = (delay * globalSpeedScale()).toInt()

        // Bornes min/max
        delay = delay.coerceIn(minDelayMs, maxDelayMs)
        return delay.toLong()
    }

    fun clearText() {
        cancelReading = true
        _isReading.value = false
        inputText = ""
        _words.value = emptyList()
        currentIndex = 0
        _currentWord.value = ""
        _prevWord.value = ""
        _nextWord.value = ""
    }

    // ✅ Corrige tryStartReading pour déléguer 100% à startReading
    fun tryStartReading() = startReading(false)

    fun updateInputText(newText: String) {
        inputText = newText
        _words.value = emptyList()
        _words.value = _words.value + importer.extractWords(newText)
        currentIndex = 0
        _currentWord.value = ""
        _prevWord.value = ""
        _nextWord.value = ""
    }

    fun setWordsFromImported(text: String) {
        _words.value = tokenize(text)
        currentIndex = 0
    }

    fun initializeIndex(){
        currentIndex = 0
    }


    fun prepareWordsFromInputIfNeeded() {
        if (inputText.isNotBlank() && newtxt) {
            _words.value = tokenize(inputText)
            currentIndex = 0
            newtxt = false
        }
    }

    private fun tokenize(raw: String): List<String> =
        raw.replace("\\s+".toRegex(), " ").trim()
            .split(" ")
            .filter { it.isNotBlank() }


    fun loadWordsFromUri(uri: Uri) {
        val text = importer.readTextOrPdfFromUri(uri)
        updateInputText(text)
    }

    fun startReading(remember: Boolean) {
        // Pas de double démarrage
        if (_isReading.value) return

        // Réinitialise proprement une éventuelle lecture précédente
        if (remember == false) readingJob?.cancel() else {}
        _isReading.value = true

        if (indexInit > 0) currentIndex = indexInit
        indexInit = 0

        readingJob = viewModelScope.launch {
            try {
                // Reprise à l'endroit actuel
                val startAt = maxOf(0, currentIndex)
                val segment = _words.value.drop(startAt)

                WordReader(
                    words = segment,
                    onWordUpdate = { word ->
                        // idx = position du mot courant dans la séquence globale
                        val idx = currentIndex
                        _currentWord.value = word

                        val all = _words.value
                        _prevWord.value = if (idx - 1 in all.indices) all[idx - 1] else ""
                        _nextWord.value = if (idx + 1 in all.indices) all[idx + 1] else ""

                        currentIndex++ // on avance ensuite l’index global
                    },
                    onFinished = {
                        _isReading.value = false
                        currentIndex = 0
                    },
                    // === VITESSE DYNAMIQUE ===
                    // === DÉLAI DYNAMIQUE PAR MOT ===
                    delayProvider = { w -> computeDelayFor(w) }
                ).StartReading()

            } finally {
                // Annulation (pause) ou fin : ici on ne reset PAS currentIndex (reprise possible)
                _isReading.value = false
            }
        }
    }

    private val _isFullScreenMode = MutableStateFlow(false)
    val isFullScreenMode: StateFlow<Boolean> get() = _isFullScreenMode

    fun setFullScreenMode(enabled: Boolean) {
        _isFullScreenMode.value = enabled
    }
    fun pauseReading() {
        readingJob?.cancel()
    }

    fun stopReading() {
        readingJob?.cancel()
        currentIndex = 0
        _isReading.value = false
    }

    fun togglePlayPause() {
        Log.d("VM", "togglePlayPause, isReading=${_isReading.value}, index=$currentIndex")

        if (_isReading.value) {
            // On est en lecture -> PAUSE
            pauseReading()
        } else {
            // On est à l'arrêt -> PLAY
            // Si tu veux relancer depuis le début quand on est au bout :
            val size = /* remplace par ta source effective */ _words.value.size
            if (currentIndex >= size) currentIndex = 0 else ""
            startReading(true)
        }
    }

    fun setStartFromCharOffset(charOffset: Int) {
        // 1) On prend le texte brut
        val text = inputText
        val safeOffset = charOffset.coerceIn(0, text.length)

        // 2) Sous-chaîne avant le curseur
        val before = text.substring(0, safeOffset)

        // 3) Compter les mots avant l’offset.
        //    Ici on suppose la même logique de séparation que pour _words : \s+.
        //    Si ta tokenisation est différente, remplace Regex(...) par la tienne.
        val wordsBefore = if (before.isBlank()) {
            emptyList()
        } else {
            before.trimStart().split(Regex("\\s+")) //Attention cette logique de découpe des mots
            //avec \s+ est différente de cette dans les autres écrans.
        }

        // 4) On positionne l’index courant sur ce mot
        indexInit = wordsBefore.size.coerceIn(0, (_words.value.size - 1).coerceAtLeast(0)) - 1
        currentIndex = indexInit
    }

    // === Navigation manuelle : reculer / avancer d’un mot ===
    fun stepBackwardOne() {
        prepareWordsFromInputIfNeeded()
        val all = _words.value
        if (all.isEmpty()) return
        if (_isReading.value) pauseReading()

        val newIdx = (currentIndex - 1).coerceAtLeast(0)
        _currentWord.value = all[newIdx]
        _prevWord.value = if (newIdx - 1 in all.indices) all[newIdx - 1] else ""
        _nextWord.value = if (newIdx + 1 in all.indices) all[newIdx + 1] else ""
        currentIndex = newIdx
    }

    fun stepForwardOne() {
        prepareWordsFromInputIfNeeded()
        val all = _words.value
        if (all.isEmpty()) return
        if (_isReading.value) pauseReading()

        val idx = currentIndex.coerceAtMost(all.lastIndex)
        _currentWord.value = all[idx]
        _prevWord.value = if (idx - 1 in all.indices) all[idx - 1] else ""
        _nextWord.value = if (idx + 1 in all.indices) all[idx + 1] else ""
        currentIndex = (idx + 1).coerceAtMost(all.size)
    }

    fun setTexte(newText: String, resetIndex: Boolean = true) {
        // Si la lecture est en cours, on la stoppe proprement
        stopReading()

        // Met à jour le texte source de l’écran
        updateInputText(newText)

        // Remet l’index au début si souhaité
        if (resetIndex) {
            initializeIndex()
        }

        // La préparation des mots est lazy chez toi (prepareWordsFromInputIfNeeded)
        // donc pas besoin de faire plus ici. Tu le feras au moment de lancer la lecture.
    }

}
