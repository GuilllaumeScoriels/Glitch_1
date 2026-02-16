package Lecteurtxt

class WordReader(
    private val words: List<String>,
    private val onWordUpdate: (String) -> Unit,
    private val onFinished: () -> Unit,
    private val delayProvider: (String) -> Long, // ← vitesse lue à la volée
) {
    suspend fun StartReading() {
        for (w in words) {
            onWordUpdate(w)
            kotlinx.coroutines.delay(delayProvider(w))
        }
        onFinished()
    }
}
