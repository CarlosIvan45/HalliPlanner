package com.example.halliplanner

import android.content.Context
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.os.Environment
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object PdfReportExporter {
    fun export(context: Context, title: String, rows: List<String>): File {
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

        val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: context.filesDir
        val safeTitle = title.lowercase(Locale.US).replace(Regex("[^a-z0-9]+"), "_").trim('_')
        val file = File(dir, "$safeTitle-${System.currentTimeMillis()}.pdf")
        FileOutputStream(file).use { document.writeTo(it) }
        document.close()
        return file
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
}
