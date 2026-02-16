package storage

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.io.IOException


class FileRepositoryImpl(
    private val context: Context,
    private val subfolderInDownloads: String = "Reader5"
) : FileRepository {

    private fun timeStampName(): String {
        val fmt = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
        return "reader_5_${fmt.format(Date())}.txt"
    }

    override suspend fun saveTextToDownloads(text: String, suggestedName: String?): Uri =
        withContext(Dispatchers.IO) {
            val fileName = (suggestedName?.takeIf { it.isNotBlank() } ?: timeStampName())
                .let { if (it.endsWith(".txt", true)) it else "$it.txt" }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Android 10+ : MediaStore, pas besoin de permission d’écriture
                val contentValues = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                    put(MediaStore.Downloads.MIME_TYPE, "text/plain")
                    put(
                        MediaStore.Downloads.RELATIVE_PATH,
                        "${Environment.DIRECTORY_DOWNLOADS}/$subfolderInDownloads"
                    )
                }
                val resolver = context.contentResolver
                val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                val itemUri = resolver.insert(collection, contentValues)
                    ?: error("Échec de la création du fichier dans MediaStore")

                resolver.openOutputStream(itemUri)?.use { out ->
                    out.write(text.toByteArray(Charsets.UTF_8))
                    out.flush()
                } ?: error("Impossible d’ouvrir OutputStream pour $itemUri")

                itemUri
            } else {
                // < Android 10 : fallback dans /Downloads/Reader5 (+ permission WRITE_EXTERNAL_STORAGE si nécessaire)
                val base = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val dir = File(base, subfolderInDownloads).apply { if (!exists()) mkdirs() }
                val file = File(dir, fileName)
                file.outputStream().use { it.write(text.toByteArray(Charsets.UTF_8)) }
                Uri.fromFile(file)
            }
        }

    override suspend fun listSavedTexts(): List<DownloadedText> =
        withContext(Dispatchers.IO) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = context.contentResolver
                val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                val projection = arrayOf(
                    MediaStore.Downloads._ID,
                    MediaStore.Downloads.DISPLAY_NAME,
                    MediaStore.Downloads.SIZE,
                    MediaStore.Downloads.DATE_MODIFIED,
                    MediaStore.Downloads.RELATIVE_PATH
                )
                val selection = "${MediaStore.Downloads.RELATIVE_PATH} LIKE ? AND ${MediaStore.Downloads.MIME_TYPE}=?"
                val args = arrayOf(
                    "%${Environment.DIRECTORY_DOWNLOADS}/$subfolderInDownloads%",
                    "text/plain"
                )

                val result = mutableListOf<DownloadedText>()
                resolver.query(collection, projection, selection, args, "${MediaStore.Downloads.DATE_MODIFIED} DESC")
                    ?.use { cursor ->
                        val iId = cursor.getColumnIndexOrThrow(MediaStore.Downloads._ID)
                        val iName = cursor.getColumnIndexOrThrow(MediaStore.Downloads.DISPLAY_NAME)
                        val iSize = cursor.getColumnIndexOrThrow(MediaStore.Downloads.SIZE)
                        val iDate = cursor.getColumnIndexOrThrow(MediaStore.Downloads.DATE_MODIFIED)
                        while (cursor.moveToNext()) {
                            val id = cursor.getLong(iId)
                            val name = cursor.getString(iName)
                            val size = cursor.getLong(iSize)
                            val modified = cursor.getLong(iDate) * 1000 // to ms
                            val uri = Uri.withAppendedPath(collection, id.toString())
                            result.add(DownloadedText(name, uri, size, modified))
                        }
                    }
                result
            } else {
                val base = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val dir = File(base, subfolderInDownloads)
                if (!dir.exists()) return@withContext emptyList()
                dir.listFiles { f -> f.isFile && f.name.endsWith(".txt", true) }
                    ?.sortedByDescending { it.lastModified() }
                    ?.map { f -> DownloadedText(f.name, Uri.fromFile(f), f.length(), f.lastModified()) }
                    ?: emptyList()
            }
        }

    override suspend fun readText(uri: Uri): String =
        withContext(Dispatchers.IO) {
            when (uri.scheme?.lowercase()) {
                ContentResolver.SCHEME_CONTENT -> {
                    context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use {
                        it.readText()
                    } ?: throw IOException("InputStream nul pour $uri")
                }
                ContentResolver.SCHEME_FILE, "file" -> {
                    val path = uri.path ?: throw IOException("Chemin introuvable pour $uri")
                    File(path).bufferedReader(Charsets.UTF_8).use { it.readText() }
                }
                else -> {
                    throw IOException("Schéma d'URI non supporté: ${uri.scheme}")
                }
            }
        }

}
