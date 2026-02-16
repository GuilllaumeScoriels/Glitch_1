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

data class DownloadItem(
    val id: String,
    val displayName: String,
    val mimeType: String?,
    val sizeBytes: Long?,
    val lastModified: Long?,
    val uri: Uri?,            // 29+ seulement
    val absolutePath: String? // pré-29 (ou app-private)
)

/** Liste les éléments du dossier Téléchargements sans jamais référencer EXTERNAL_CONTENT_URI. */
fun listDownloadsCompat(context: Context): List<DownloadItem> {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        queryDownloadsApi29Plus(context.contentResolver)
    } else {
        queryDownloadsLegacy()
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

    val out = mutableListOf<DownloadItem>()
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
            val uri = Uri.withAppendedPath(collection, id.toString())
            out.add(
                DownloadItem(
                    id = uri.toString(),
                    displayName = name,
                    mimeType = mime,
                    sizeBytes = size,
                    lastModified = if (modifiedSeconds > 0) modifiedSeconds * 1000 else null,
                    uri = uri,
                    absolutePath = null
                )
            )
        }
    }
    return out
}

/** Pré-29 : parcours le dossier public Download/ via File (nécessite permissions legacy si tu y accèdes). */
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

