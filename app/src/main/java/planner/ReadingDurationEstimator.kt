package planner

import android.content.Context
import Lecteurtxt.PdfConverter
import com.example.a18.TextFileImporter
import Settings.DataStoreSettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File

/** Calcule la durée de lecture estimée d’un item de la liste Téléchargements. */
object ReadingDurationEstimator {

    /** Estime la durée (minutes) en fonction du texte ou PDF et des réglages (ms/mot). */
    suspend fun estimateMinutesFor(
        context: Context,
        item: Downloads.DownloadItem,
        difficulty: Int
    ): Int = withContext(Dispatchers.IO) {
    val text: String = try {
            when {
                item.uri != null -> {
                    // Uri SAF/MediaStore → Text/PDF via l’importeur
                    TextFileImporter(context).readTextOrPdfFromUri(item.uri!!)
                }
                item.absolutePath != null -> {
                    val path = item.absolutePath!!
                    if (path.lowercase().endsWith(".pdf")) {
                        val out = File(context.cacheDir, "tmp_${item.id.hashCode()}.txt")
                        PdfConverter.pdfToTxt(path, out.absolutePath)
                        out.readText()
                    } else {
                        File(path).readText()
                    }
                }
                else -> ""
            }
        } catch (_: Exception) { "" }

        val words = Regex("\\s+").split(text.trim()).count { it.isNotEmpty() }
        val msPerWord = runCatching {
            val s = DataStoreSettingsRepository(context).settings.first()
            when (difficulty.coerceIn(1, 5)) {
                1 -> s.readMsD1
                2 -> s.readMsD2
                3 -> s.readMsD3
                4 -> s.readMsD4
                5 -> s.readMsD5
                else -> s.defaultWordDelayMs
            }
        }.getOrElse { 500 }


        val totalMs = words.toLong() * msPerWord
        val minutes = ((totalMs + 59_999) / 60_000).toInt()
        if (minutes <= 0) 1 else minutes
    }
}
