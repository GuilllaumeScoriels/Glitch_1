package Downloads

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import java.io.File

/**
 * Repository compatible minSdk 21 pour lister/renommer des fichiers dans "Téléchargements".
 * - API 29+ : via MediaStore.Downloads.getContentUri(VOLUME_EXTERNAL) (aucun usage d'EXTERNAL_CONTENT_URI).
 * - API <29 : fallback legacy via File() sur le dossier public Download/.
 *
 * ATTENTION : ce fichier ne doit contenir AUCUN import ou référence à
 * MediaStore.Downloads.EXTERNAL_CONTENT_URI.
 */
class MediaStoreDownloadedFilesRepository(
    private val context: Context
) {
    /**
     * Renomme un item existant.
     * - 29+ : update DISPLAY_NAME via MediaStore, fallback DocumentsContract.renameDocument si possible.
     * - <29 : rename via File (legacy) ou app-private via File.
     */
    fun rename(item: DownloadItem, newDisplayName: String): DownloadItem? {
        val clean = newDisplayName.sanitizeFileName()
        if (clean.isEmpty()) return null

        return if (item.uri != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            renameMediaStoreApi29Plus(context, item, clean)
        } else {
            renameWithFile(item, clean)
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun renameMediaStoreApi29Plus(
        context: Context,
        item: DownloadItem,
        cleanName: String
    ): DownloadItem? {
        val resolver = context.contentResolver
        val uri = item.uri ?: return null
        val safeNew = cleanName.withKeptExtensionFrom(item.displayName)

        // 1) Tenter via DISPLAY_NAME (MediaStore)
        runCatching {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, safeNew)
            }
            val rows = resolver.update(uri, values, null, null)
            if (rows != null && rows > 0) {
                return requerySingle(resolver, uri) ?: item.copy(displayName = safeNew)
            }
        }

        // 2) Fallback : SAF renameDocument si c’est un DocumentUri
        if (DocumentsContract.isDocumentUri(context, uri)) {
            runCatching {
                val renamed = DocumentsContract.renameDocument(resolver, uri, safeNew)
                if (renamed != null) {
                    return requerySingle(resolver, renamed) ?: item.copy(
                        id = renamed.toString(),
                        displayName = safeNew,
                        uri = renamed
                    )
                }
            }
        }

        return null
    }

    // ------------------------------------------------------------
    // Pré-29 : fallback via File() sur Download/ (legacy)
    // ------------------------------------------------------------

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

    private fun renameWithFile(item: DownloadItem, cleanName: String): DownloadItem? {
        val oldPath = item.absolutePath ?: return null
        val oldFile = File(oldPath)
        if (!oldFile.exists()) return null

        val safeNewName = cleanName.withKeptExtensionFrom(oldFile.name)

        val candidate = File(oldFile.parentFile, safeNewName)
        val finalFile = if (candidate.exists()) {
            File(oldFile.parentFile!!, uniqueName(oldFile.parentFile!!, safeNewName))
        } else candidate

        if (!oldFile.renameTo(finalFile)) return null

        return item.copy(
            id = finalFile.absolutePath,
            displayName = finalFile.name,
            sizeBytes = finalFile.length(),
            lastModified = finalFile.lastModified(),
            uri = null,
            absolutePath = finalFile.absolutePath
        )
    }

    // ------------------------------------------------------------
    // Utilitaires
    // ------------------------------------------------------------

    private fun uniqueName(dir: File, baseName: String): String {
        val dot = baseName.lastIndexOf('.')
        val stem = if (dot > 0) baseName.substring(0, dot) else baseName
        val ext = if (dot > 0) baseName.substring(dot) else ""
        var i = 1
        var candidate: String
        do {
            candidate = "$stem ($i)$ext"
            i++
        } while (File(dir, candidate).exists())
        return candidate
    }

    private fun requerySingle(resolver: ContentResolver, uri: Uri): DownloadItem? {
        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.MIME_TYPE,
            MediaStore.MediaColumns.DATE_MODIFIED
        )
        resolver.query(uri, projection, null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                val id = c.getLong(c.getColumnIndexOrThrow(MediaStore.MediaColumns._ID))
                val name = c.getString(c.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)) ?: "file"
                val size = c.getLong(c.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE))
                val mime = c.getString(c.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE))
                val modifiedSeconds = c.getLong(c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED))
                val clean = uri.buildUpon().clearQuery().build()
                return DownloadItem(
                    id = clean.toString(),
                    displayName = name,
                    mimeType = mime,
                    sizeBytes = size,
                    lastModified = if (modifiedSeconds > 0) modifiedSeconds * 1000 else null,
                    uri = clean,
                    absolutePath = null
                )
            }
        }
        return null
    }
}
