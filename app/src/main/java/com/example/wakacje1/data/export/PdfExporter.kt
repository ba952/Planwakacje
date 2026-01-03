package com.example.wakacje1.data.export

import android.content.ContentValues
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import com.example.wakacje1.domain.model.DayPlan
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.util.Calendar
import kotlin.math.max
import kotlin.math.min

object PdfExporter {

    fun exportPlanToDownloads(
        context: Context,
        fileName: String,
        destinationName: String?,
        tripStartDateMillis: Long?,
        plan: List<DayPlan>
    ) {
        if (plan.isEmpty()) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            exportToDownloadsApi29Plus(context, fileName, destinationName, tripStartDateMillis, plan)
        } else {
            exportToDownloadsLegacy(context, fileName, destinationName, tripStartDateMillis, plan)
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun exportToDownloadsApi29Plus(
        context: Context,
        fileName: String,
        destinationName: String?,
        tripStartDateMillis: Long?,
        plan: List<DayPlan>
    ) {
        val resolver = context.contentResolver

        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }

        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: throw IllegalStateException("Nie udało się utworzyć pliku w Pobrane (MediaStore).")

        try {
            resolver.openOutputStream(uri)?.use { out ->
                writePdf(out, destinationName, tripStartDateMillis, plan)
            } ?: throw IllegalStateException("Nie udało się otworzyć strumienia do zapisu PDF.")
        } finally {
            val done = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
            resolver.update(uri, done, null, null)
        }
    }

    private fun exportToDownloadsLegacy(
        context: Context,
        fileName: String,
        destinationName: String?,
        tripStartDateMillis: Long?,
        plan: List<DayPlan>
    ) {
        val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        if (!dir.exists()) dir.mkdirs()

        val file = File(dir, fileName)
        FileOutputStream(file).use { out ->
            writePdf(out, destinationName, tripStartDateMillis, plan)
        }
    }

    private fun writePdf(
        out: OutputStream,
        destinationName: String?,
        tripStartDateMillis: Long?,
        plan: List<DayPlan>
    ) {
        val pdf = PdfDocument()

        val pageWidth = 595
        val pageHeight = 842

        val marginX = 44f
        val topY = 52f
        val bottomY = pageHeight - 52f
        val contentWidth = pageWidth - marginX * 2

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 12f }

        var pageNumber = 1
        var y = topY

        // >>> KLUCZ: deklaracja PRZED funkcjami, żeby Kotlin ją widział
        var currentCanvas: Canvas? = null

        fun newPage(): PdfDocument.Page {
            val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
            pageNumber++
            y = topY
            return pdf.startPage(pageInfo)
        }

        fun lineHeightFor(size: Float): Float {
            paint.textSize = size
            val fm = paint.fontMetrics
            return (fm.descent - fm.ascent) + 4f
        }

        fun drawCentered(text: String, size: Float, bold: Boolean = false) {
            val canvas = currentCanvas ?: return
            paint.textSize = size
            paint.isFakeBoldText = bold
            val textWidth = paint.measureText(text)
            val x = ((pageWidth - textWidth) / 2f).coerceAtLeast(marginX)
            val lh = lineHeightFor(size)
            y += lh
            canvas.drawText(text, x, y, paint)
            paint.isFakeBoldText = false
        }

        fun wrap(text: String, size: Float): List<String> {
            paint.textSize = size
            val result = mutableListOf<String>()
            val paragraphs = text.split("\n")

            for (p in paragraphs) {
                val paragraph = p.trimEnd()
                if (paragraph.isBlank()) {
                    result += ""
                    continue
                }

                var s = paragraph
                while (s.isNotEmpty()) {
                    val count = paint.breakText(s, true, contentWidth, null)
                    val take = max(1, count)

                    var cut = min(take, s.length)
                    val chunk = s.substring(0, cut)
                    val lastSpace = chunk.lastIndexOf(' ')
                    if (lastSpace >= 10 && lastSpace < chunk.length - 1) {
                        cut = lastSpace
                    }

                    val line = s.substring(0, cut).trim()
                    result += line
                    s = s.substring(cut).trimStart()
                }
            }
            return result
        }

        fun estimateDayHeight(day: DayPlan): Float {
            var h = 0f
            h += lineHeightFor(16f) * 1.2f
            h += lineHeightFor(11f) * 1.2f
            if (!destinationName.isNullOrBlank()) h += lineHeightFor(11f) * 1.2f
            h += 10f

            val blocks = parseDetailsToBlocks(day.details)
            for (b in blocks) {
                h += lineHeightFor(12f)
                h += 2f
                h += wrap(b.title, 12f).size * lineHeightFor(12f)
                if (b.desc.isNotBlank()) {
                    h += 2f
                    h += wrap(b.desc, 11f).size * lineHeightFor(11f)
                }
                h += 8f
            }
            return h
        }

        var page = newPage()
        currentCanvas = page.canvas

        // tytuł dokumentu
        drawCentered("Plan wyjazdu", size = 18f, bold = true)
        drawCentered(destinationName?.takeIf { it.isNotBlank() } ?: "—", size = 12f, bold = false)
        y += 10f

        for (day in plan) {
            val needed = estimateDayHeight(day)
            val remaining = bottomY - y
            if (needed > remaining && y > topY + 40f) {
                pdf.finishPage(page)
                page = newPage()
                currentCanvas = page.canvas
            }

            // nagłówek dnia
            drawCentered("Dzień ${day.day}", size = 16f, bold = true)

            val dateLine = tripStartDateMillis?.let { formatDate(it, addDays = day.day - 1) } ?: " "
            drawCentered(dateLine, size = 11f, bold = false)

            if (!destinationName.isNullOrBlank()) {
                drawCentered(destinationName, size = 11f, bold = false)
            }

            y += 10f

            val canvas = currentCanvas!! // tu już MUSI być ustawione
            val blocks = parseDetailsToBlocks(day.details)

            for (b in blocks) {
                paint.isFakeBoldText = true
                val lhLabel = lineHeightFor(12f)
                y += lhLabel
                canvas.drawText(b.label, marginX, y, paint)
                paint.isFakeBoldText = false
                y += 2f

                val titleLines = wrap(b.title, 12f)
                val lhTitle = lineHeightFor(12f)
                for (l in titleLines) {
                    y += lhTitle
                    canvas.drawText(l, marginX, y, paint)
                }

                if (b.desc.isNotBlank()) {
                    y += 2f
                    val descLines = wrap(b.desc, 11f)
                    val lhDesc = lineHeightFor(11f)
                    for (l in descLines) {
                        y += lhDesc
                        canvas.drawText(l, marginX + 14f, y, paint)
                    }
                }

                y += 8f
                if (y > bottomY) {
                    pdf.finishPage(page)
                    page = newPage()
                    currentCanvas = page.canvas
                }
            }

            y += 6f
        }

        pdf.finishPage(page)
        pdf.writeTo(out)
        pdf.close()
    }

    private data class Block(val label: String, val title: String, val desc: String)

    private fun parseDetailsToBlocks(details: String): List<Block> {
        val lines = details.lines().map { it.trimEnd() }

        fun parseSection(prefix: String): Pair<String, MutableList<String>>? {
            val idx = lines.indexOfFirst { it.startsWith(prefix) }
            if (idx == -1) return null
            val title = lines[idx].removePrefix(prefix).trim()
            val descLines = mutableListOf<String>()
            var i = idx + 1
            while (i < lines.size) {
                val l = lines[i]
                if (l.startsWith("Poranek:") || l.startsWith("Południe:") || l.startsWith("Wieczór:")) break
                if (l.startsWith("-")) descLines += l.removePrefix("-").trim()
                i++
            }
            return title to descLines
        }

        val m = parseSection("Poranek:")
        val n = parseSection("Południe:")
        val e = parseSection("Wieczór:")

        if (m == null && n == null && e == null) {
            val text = details.trim()
            if (text.isBlank()) return emptyList()
            return listOf(Block(label = "Plan dnia", title = text, desc = ""))
        }

        val blocks = mutableListOf<Block>()
        m?.let { blocks += Block("Poranek", it.first.ifBlank { "—" }, it.second.joinToString("\n")) }
        n?.let { blocks += Block("Południe", it.first.ifBlank { "—" }, it.second.joinToString("\n")) }
        e?.let { blocks += Block("Wieczór", it.first.ifBlank { "—" }, it.second.joinToString("\n")) }
        return blocks
    }

    private fun formatDate(startMillis: Long, addDays: Int): String {
        val cal = Calendar.getInstance()
        cal.timeInMillis = startMillis
        cal.add(Calendar.DAY_OF_MONTH, addDays)
        val y = cal.get(Calendar.YEAR)
        val m = cal.get(Calendar.MONTH) + 1
        val d = cal.get(Calendar.DAY_OF_MONTH)
        return "%04d-%02d-%02d".format(y, m, d)
    }
}
