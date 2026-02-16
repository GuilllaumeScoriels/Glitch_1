package storage

import android.net.Uri

data class DownloadedText(
    val name: String,
    val uri: Uri,
    val sizeBytes: Long?,
    val modifiedAt: Long?
)

interface FileRepository {
    /**
     * Sauvegarde [text] dans Téléchargements (Downloads) sous forme de .txt
     * et renvoie l'URI du fichier créé.
     */
    suspend fun saveTextToDownloads(text: String, suggestedName: String? = null): Uri

    /** Liste les .txt créés par l’app dans Téléchargements. */
    suspend fun listSavedTexts(): List<DownloadedText>

    /** Lit le contenu texte d’un .txt via son [uri]. */
    suspend fun readText(uri: Uri): String
}
