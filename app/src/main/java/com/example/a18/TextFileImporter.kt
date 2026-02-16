package com.example.a18

import Lecteurtxt.PdfConverter
import android.content.Context
import android.net.Uri
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import java.io.File
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.FileInputStream
import java.io.FileNotFoundException

/**
 * Classe responsable de l'importation de fichiers texte depuis un Uri.
 * Respecte le principe de responsabilité unique.
 */
class TextFileImporter(private val context: Context) {

    init {
        // Initialise PDFBox-Android une seule fois
        PDFBoxResourceLoader.init(context)
    }


    fun importTextFromUri(uri: Uri): List<String> {
        val inputStream = context.contentResolver.openInputStream(uri)
        val reader = BufferedReader(InputStreamReader(inputStream))
        val text = reader.use { it.readText() }
        return text.split("\\s+".toRegex()).filter { it.isNotBlank() }
    }
    fun extractWords(text: String): List<String> {
        return text.split("\\s+".toRegex()).filter { it.isNotBlank() }
    }
    fun readTextFromUri(uri: Uri): String {
        val inputStream = context.contentResolver.openInputStream(uri)
        val reader = BufferedReader(InputStreamReader(inputStream))
        return reader.use { it.readText() }
    }

    private fun copyUriToFile(uri: Uri, dest: File): File {
        return try {
            // Chemin standard
            context.contentResolver.openInputStream(uri)?.use { input ->
                dest.outputStream().use { output -> input.copyTo(output) }
            } ?: run {
                // Fallback via ParcelFileDescriptor (Google Drive / Docs)
                context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                    FileInputStream(pfd.fileDescriptor).use { input ->
                        dest.outputStream().use { output -> input.copyTo(output) }
                    }
                } ?: throw FileNotFoundException("Impossible d'ouvrir l'URI: $uri")
            }
            dest
        } catch (e: FileNotFoundException) {
            // On remonte une erreur explicite (utile au debug)
            throw e
        } catch (e: Exception) {
            throw FileNotFoundException("Échec de lecture de l'URI: $uri (${e.message})")
        }
    }


    /**
     * Lit un Uri texte OU PDF. Si c'est un PDF, il est converti en .txt via PdfConverter.
     */
    fun readTextOrPdfFromUri(uri: Uri): String {
        val mime = context.contentResolver.getType(uri) ?: ""
        val isPdf = mime == "application/pdf" || uri.toString().lowercase().endsWith(".pdf")
        return if (isPdf) {
            val pdfTmp = File(context.cacheDir, "import.pdf")
            val txtTmp = File(context.cacheDir, "import.txt")
            copyUriToFile(uri, pdfTmp)
            // Conversion PDF -> TXT
            PdfConverter.pdfToTxt(pdfTmp.absolutePath, txtTmp.absolutePath)
            txtTmp.readText()
        } else {
            readTextFromUri(uri)
        }
    }
}