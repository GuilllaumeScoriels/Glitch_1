package Downloads

import android.app.Application
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import androidx.activity.result.IntentSenderRequest
import androidx.documentfile.provider.DocumentFile
import android.app.RecoverableSecurityException
import android.content.ContentUris
import com.example.a18.TextFileImporter
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch



/**
 * minSdk 21 compatible :
 * - API 29+ : MediaStore.Downloads.getContentUri(VOLUME_EXTERNAL) dans une méthode @RequiresApi(29)
 * - API <29 : fallback via File() sur Download/
 * - Aucune référence à MediaStore.Downloads.EXTERNAL_CONTENT_URI
 * - Pas de helpers dupliqués ici (utilise ceux de FileNameExtensions.kt)
 */

data class DownloadsUiState(
    val items: List<DownloadItem> = emptyList(),
    val isLoading: Boolean = false,
    val renameTarget: DownloadItem? = null,
    val errorMessage: String? = null,
    val infoMessage: String? = null
)

class DownloadsViewModel(app: Application) : AndroidViewModel(app) {

    private var pendingDeleteUri: Uri? = null
    private val downloadsRepo = MediaStoreDownloadedFilesRepository(getApplication())
    //Centralise la logique de renommage dans un repository déjà présent : MediaStoreDownloadedFilesRepository

    private val _state = MutableStateFlow(DownloadsUiState(isLoading = true))
    val state: StateFlow<DownloadsUiState> = _state

    private val _wordCounts = MutableStateFlow<Map<String, Int>>(emptyMap())
    val wordCounts: StateFlow<Map<String, Int>> = _wordCounts

    // Carte: item.id -> difficulté (1..5) choisie dans l'écran Downloads
    private val _itemDifficulties = MutableStateFlow<Map<String, Int>>(emptyMap())
    val itemDifficulties: StateFlow<Map<String, Int>> = _itemDifficulties

    fun setItemDifficulty(id: String, level: Int) {
        val v = level.coerceIn(1, 5)
        _itemDifficulties.value = _itemDifficulties.value + (id to v)
    }

    fun load(context: Context) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, errorMessage = null, infoMessage = null)
            val list = withContext(Dispatchers.IO) {
                val fromDownloads = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    queryDownloadsApi29Plus(context.contentResolver)
                } else {
                    queryDownloadsLegacy()
                }
                val fromAppPrivate = queryAppPrivate(context)
                (fromDownloads + fromAppPrivate).sortedByDescending { it.lastModified ?: 0L }
            }
            _state.value = _state.value.copy(items = list, isLoading = false)
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun queryDownloadsApi29Plus(resolver: ContentResolver): List<DownloadItem> {
        val collection: Uri = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL)

        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.MIME_TYPE,
            MediaStore.MediaColumns.DATE_MODIFIED
        )
        val sortOrder = "${MediaStore.MediaColumns.DATE_MODIFIED} DESC"

        val result = mutableListOf<DownloadItem>()
        resolver.query(collection, projection, null, null, sortOrder)?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val nameCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
            val sizeCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
            val mimeCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE)
            val dateCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED)
            while (c.moveToNext()) {
                val id = c.getLong(idCol)
                val name = c.getString(nameCol) ?: "file"
                val size = c.getLong(sizeCol)
                val mime = c.getString(mimeCol)
                val modifiedSeconds = c.getLong(dateCol)
                val contentUri = Uri.withAppendedPath(collection, id.toString())
                result.add(
                    DownloadItem(
                        id = contentUri.toString(),
                        displayName = name,
                        mimeType = mime,
                        sizeBytes = size,
                        lastModified = if (modifiedSeconds > 0) modifiedSeconds * 1000 else null,
                        uri = contentUri,
                        absolutePath = null
                    )
                )
            }
        }
        return result
    }

    // --------- Listing : pré-29 via File() sur Download/ ---------

    @Suppress("DEPRECATION")
    private fun queryDownloadsLegacy(): List<DownloadItem> {
        val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val files = dir?.listFiles()?.toList().orEmpty()
        return files.sortedByDescending { it.lastModified() }.map { f ->
            DownloadItem(
                id = f.absolutePath,
                displayName = f.name,
                mimeType = null,
                sizeBytes = f.length(),
                lastModified = f.lastModified(),
                uri = null,
                absolutePath = f.absolutePath
            )
        }
    }

    // --------- Listing : app-private ---------

    private fun queryAppPrivate(context: Context): List<DownloadItem> {
        val dir = context.getExternalFilesDir(null) ?: return emptyList()
        val files = dir.listFiles()?.toList().orEmpty()
        return files.map { f ->
            DownloadItem(
                id = f.absolutePath,
                displayName = f.name,
                mimeType = null,
                sizeBytes = f.length(),
                lastModified = f.lastModified(),
                uri = null,
                absolutePath = f.absolutePath
            )
        }
    }

    /** Lit le texte du fichier sélectionné (MediaStore/SAF ou chemin absolu). */
    fun readItemText(context: Context, item: DownloadItem): String? {
        return try {
            when {
                item.uri != null -> {
                    context.contentResolver.openInputStream(item.uri).use { ins ->
                        ins?.bufferedReader(Charsets.UTF_8)?.readText()
                    }
                }
                item.absolutePath != null -> {
                    File(item.absolutePath).takeIf { it.exists() }?.readText(Charsets.UTF_8)
                }
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }

    fun deleteByName(
        context: Context,
        displayName: String,
        onNeedsConsent: (IntentSenderRequest) -> Unit = {}
    ) {
        val item = _state.value.items.firstOrNull { it.displayName == displayName }
        if (item == null) {
            _state.value = _state.value.copy(errorMessage = "Fichier introuvable: $displayName")
            return
        }

        viewModelScope.launch {
            _state.value = _state.value.copy(infoMessage = null, errorMessage = null)

            val result = withContext(Dispatchers.IO) {
                // 1) Fichier privé de l’app (chemin absolu connu) → suppression directe
                item.absolutePath?.let { path ->
                    return@withContext if (java.io.File(path).delete()) {
                        DeleteResult.Deleted
                    } else {
                        DeleteResult.Failed("Échec de la suppression du fichier privé.")
                    }
                }

                // 2) Uri (MediaStore / SAF / Downloads)
                val u = item.uri
                    ?: return@withContext DeleteResult.Failed("Emplacement du fichier inconnu.")

                // Essai direct via ContentResolver.delete(...)
                try {
                    val rows = context.contentResolver.delete(u, null, null)
                    if (rows > 0) {
                        DeleteResult.Deleted
                    } else {
                        // Pas supprimé, mais pas d’exception : probablement droit manquant
                        DeleteResult.Failed("Suppression refusée (aucune ligne affectée).")
                    }
                } catch (se: SecurityException) {
                    // Android 10+ peut fournir une RecoverableSecurityException
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        val rse = se as? RecoverableSecurityException
                        if (rse != null) {
                            pendingDeleteUri = u
                            val intentSender = rse.userAction.actionIntent.intentSender
                            DeleteResult.NeedsConsent(
                                IntentSenderRequest.Builder(intentSender).build()
                            )
                        } else {
                            DeleteResult.Failed("Suppression protégée par le système.")
                        }
                    } else {
                        DeleteResult.Failed("Suppression protégée par le système (Android < 10).")
                    }
                }
            }


                when (result) {
                is DeleteResult.Deleted -> {
                    load(context)
                    _state.value = _state.value.copy(infoMessage = "Fichier supprimé.")
                }
                is DeleteResult.NeedsConsent -> {
                    onNeedsConsent(result.intentSenderRequest)
                }
                is DeleteResult.Failed -> {
                    _state.value = _state.value.copy(errorMessage = result.reason ?: "Échec de la suppression.")
                }
            }
        }
    }

    /** Calcule et met en cache le nombre de mots pour un item (si absent). */
    fun ensureWordCount(context: Context, item: DownloadItem) {
        if (_wordCounts.value.containsKey(item.id)) return

        viewModelScope.launch(Dispatchers.IO) {
            val count = runCatching {
                // 1) Lire le texte du fichier (TXT/PDF), sinon 0
                val text: String? = when {
                    item.uri != null -> {
                        val importer = TextFileImporter(context.applicationContext)
                        importer.readTextOrPdfFromUri(item.uri)
                    }
                    item.absolutePath != null -> {
                        java.io.File(item.absolutePath).takeIf { it.exists() }?.readText()
                    }
                    else -> null
                }
                // 2) Compter les mots (robuste, espaces multiples)
                text?.let { Regex("\\S+").findAll(it).count() } ?: 0
            }.getOrDefault(0)

            // 3) Cache immuable : nouvelle map = ancienne + (id -> count)
            val old = _wordCounts.value
            _wordCounts.value = old + (item.id to count)
        }
    }

    fun onDeleteConsentResult(context: Context, confirmed: Boolean) {
        val target = pendingDeleteUri
        pendingDeleteUri = null

        if (!confirmed || target == null) return

        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.delete(target, null, null) > 0
                }.getOrDefault(false)
            }
            if (ok) {
                load(context)
                _state.value = _state.value.copy(infoMessage = "Fichier supprimé.")
            } else {
                _state.value = _state.value.copy(errorMessage = "Suppression refusée après consentement.")
            }
        }
    }

    fun requestRename(item: DownloadItem) {
        _state.value = _state.value.copy(
            renameTarget = item,
            errorMessage = null,
            infoMessage = null
        )
    }

    fun cancelRename() {
        _state.value = _state.value.copy(renameTarget = null)
    }

    fun confirmRename(context: Context, newName: String) {
        val target = _state.value.renameTarget ?: return
        viewModelScope.launch {
            val updated = withContext(Dispatchers.IO) {
                downloadsRepo.rename(target, newName)
            }
            if (updated != null) {
                // Resserre la source de vérité : on relit la liste depuis MediaStore
                load(context)
                _state.value = _state.value.copy(
                    renameTarget = null,
                    infoMessage = "Renommé en ${updated.displayName}"
                )
            } else {
                _state.value = _state.value.copy(
                    renameTarget = null,
                    errorMessage = "Échec du renommage."
                )
            }
        }
    }

}
