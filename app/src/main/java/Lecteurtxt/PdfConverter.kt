package Lecteurtxt

import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.File

object PdfConverter {
    fun pdfToTxt(pdfPath: String, txtPath: String) {
        val document = PDDocument.load(File(pdfPath))
        val stripper = PDFTextStripper()
        val text = stripper.getText(document)
        document.close()
        File(txtPath).writeText(text)
    }

}