package com.Popov.budgetapp.pdf

import android.content.Context
import android.net.Uri
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper

object PdfTextExtractor {

    @Volatile
    private var pdfBoxInitialized = false

    fun extractText(context: Context, uri: Uri): String {
        ensurePdfBox(context.applicationContext)
        val resolver = context.contentResolver
        val stream = resolver.openInputStream(uri)
            ?: throw IllegalStateException("Не удалось открыть файл")
        stream.use { input ->
            PDDocument.load(input).use { doc ->
                val stripper = PDFTextStripper()
                stripper.sortByPosition = true
                return stripper.getText(doc)
            }
        }
    }

    private fun ensurePdfBox(appContext: Context) {
        if (pdfBoxInitialized) return
        synchronized(this) {
            if (!pdfBoxInitialized) {
                PDFBoxResourceLoader.init(appContext)
                pdfBoxInitialized = true
            }
        }
    }
}
