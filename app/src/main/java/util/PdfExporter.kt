package com.example.wakacje1.util

import android.content.Context
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.os.Environment
import android.widget.Toast
import com.example.wakacje1.ui.DayPlan
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

object PdfExporter {

    fun exportPlan(context: Context, plan: List<DayPlan>) {
        if (plan.isEmpty()) {
            Toast.makeText(context, "Brak planu do zapisania", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val document = PdfDocument()
            // Rozmiar A4 w punktach (72 dpi)
            val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
            val page = document.startPage(pageInfo)

            val canvas = page.canvas
            val paint = Paint().apply { textSize = 12f }

            var y = 40f
            canvas.drawText("Plan podróży", 40f, y, paint)
            y += 24f

            plan.forEach { day ->
                if (y > 800f) {
                    // Przy większej ilości dni można dodać kolejne strony – na razie uproszczone
                    return@forEach
                }
                canvas.drawText("Dzień ${day.day}: ${day.title}", 40f, y, paint)
                y += 16f
                canvas.drawText(day.details, 56f, y, paint)
                y += 24f
            }

            document.finishPage(page)

            val sdf = SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault())
            val fileName = "plan_${sdf.format(Date())}.pdf"

            val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: context.filesDir
            val outFile = File(dir, fileName)

            FileOutputStream(outFile).use { out ->
                document.writeTo(out)
            }
            document.close()

            Toast.makeText(
                context,
                "Zapisano PDF:\n${outFile.absolutePath}",
                Toast.LENGTH_LONG
            ).show()
        } catch (e: Exception) {
            Toast.makeText(context, "Błąd PDF: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }
}
