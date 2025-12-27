package com.example.wakacje1.data.local

import android.content.Context
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import com.example.wakacje1.data.model.DayPlan
import java.io.File
import java.io.FileOutputStream

object PdfExporter {

    fun exportPlanToPdf(
        context: Context,
        fileName: String,
        title: String,
        plan: List<DayPlan>
    ): File {
        val pdf = PdfDocument()
        val paint = Paint().apply { textSize = 12f }

        val pageWidth = 595   // A4 ~ 72dpi
        val pageHeight = 842

        var pageNumber = 1
        var y = 40

        fun newPage(): PdfDocument.Page {
            val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
            pageNumber++
            y = 40
            return pdf.startPage(pageInfo)
        }

        var page = newPage()
        var canvas = page.canvas

        // Nagłówek
        paint.textSize = 16f
        canvas.drawText(title, 40f, y.toFloat(), paint)
        y += 24
        paint.textSize = 12f
        canvas.drawText("Wygenerowano w aplikacji.", 40f, y.toFloat(), paint)
        y += 24

        for (d in plan) {
            val header = "Dzień ${d.day}: ${d.title}"
            val body = d.details

            val lines = buildString {
                appendLine(header)
                appendLine(body)
                appendLine()
            }.lines()

            for (line in lines) {
                if (y > pageHeight - 40) {
                    pdf.finishPage(page)
                    page = newPage()
                    canvas = page.canvas
                }
                canvas.drawText(line.take(110), 40f, y.toFloat(), paint)
                y += 16
            }
        }

        pdf.finishPage(page)

        val outFile = File(context.getExternalFilesDir(null), fileName)
        FileOutputStream(outFile).use { fos ->
            pdf.writeTo(fos)
        }
        pdf.close()

        return outFile
    }
}