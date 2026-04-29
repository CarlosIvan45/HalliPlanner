package com.example.halliplanner

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object PdfReportExporter {
    fun export(context: Context, title: String, rows: List<String>): ExportedPdf {
        val document = PdfDocument()
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val pageWidth = 595
        val pageHeight = 842
        var pageNumber = 1
        var y = 54

        fun newPage(): PdfDocument.Page {
            val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber++).create()
            val page = document.startPage(pageInfo)
            paint.textSize = 20f
            paint.isFakeBoldText = true
            page.canvas.drawText(title, 40f, 42f, paint)
            paint.textSize = 10f
            paint.isFakeBoldText = false
            page.canvas.drawText("Generado: ${SimpleDateFormat("d/M/yyyy HH:mm", Locale("es", "MX")).format(Date())}", 40f, 62f, paint)
            y = 88
            return page
        }

        var page = newPage()
        paint.textSize = 12f

        val content = if (rows.isEmpty()) listOf("Sin registros para exportar.") else rows
        content.forEachIndexed { index, row ->
            if (y > pageHeight - 72) {
                document.finishPage(page)
                page = newPage()
                paint.textSize = 12f
            }

            paint.isFakeBoldText = true
            page.canvas.drawText("${index + 1}.", 40f, y.toFloat(), paint)
            paint.isFakeBoldText = false
            wrap(row, 76).forEach { line ->
                page.canvas.drawText(line, 62f, y.toFloat(), paint)
                y += 18
            }
            y += 8
        }

        document.finishPage(page)

        val safeTitle = title.lowercase(Locale.US).replace(Regex("[^a-z0-9]+"), "_").trim('_')
        val fileName = "$safeTitle-${System.currentTimeMillis()}.pdf"
        val exported = saveDocument(context, fileName, document)
        document.close()
        return exported
    }

    fun share(context: Context, exportedPdf: ExportedPdf) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, exportedPdf.uri)
            putExtra(Intent.EXTRA_SUBJECT, exportedPdf.fileName)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Compartir reporte PDF"))
    }

    private fun saveDocument(context: Context, fileName: String, document: PdfDocument): ExportedPdf {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, "application/pdf")
                put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/HalliPlanner")
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: return savePrivateDocument(context, fileName, document)
            resolver.openOutputStream(uri)?.use { document.writeTo(it) }
                ?: return savePrivateDocument(context, fileName, document)
            return ExportedPdf(uri, fileName, "Descargas/HalliPlanner/$fileName")
        }

        return savePrivateDocument(context, fileName, document)
    }

    private fun savePrivateDocument(context: Context, fileName: String, document: PdfDocument): ExportedPdf {
        val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: context.filesDir
        val file = File(dir, fileName)
        FileOutputStream(file).use { document.writeTo(it) }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        return ExportedPdf(uri, fileName, file.absolutePath)
    }

    private fun wrap(text: String, max: Int): List<String> {
        if (text.length <= max) return listOf(text)
        val lines = mutableListOf<String>()
        var remaining = text
        while (remaining.length > max) {
            val breakAt = remaining.take(max).lastIndexOf(' ').takeIf { it > 20 } ?: max
            lines.add(remaining.take(breakAt).trim())
            remaining = remaining.drop(breakAt).trim()
        }
        if (remaining.isNotBlank()) lines.add(remaining)
        return lines
    }

    data class ExportedPdf(
        val uri: Uri,
        val fileName: String,
        val displayPath: String
    )
}
