package com.example.wakacje1.domain.usecase

import android.app.Activity
import android.content.Context
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.WebView
import android.webkit.WebViewClient
import com.example.wakacje1.domain.model.InternalDayPlan
import com.example.wakacje1.domain.model.SlotPlan
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object WebViewPdfExporter {

    /**
     * Główna metoda eksportu. Przyjmuje pełne dane (InternalDayPlan).
     */
    suspend fun export(
        activity: Activity,
        destinationName: String,
        tripStartDateMillis: Long?,
        plan: List<InternalDayPlan>
    ): String {
        // 1. Generujemy profesjonalny HTML
        val htmlContent = generateProHtml(destinationName, tripStartDateMillis, plan)

        // 2. Uruchamiamy drukowanie
        openPrintDialog(
            activity = activity,
            html = htmlContent,
            jobName = "Plan_$destinationName"
        )

        return "Przygotowano podgląd wydruku."
    }

    private fun generateProHtml(
        title: String,
        dateMillis: Long?,
        days: List<InternalDayPlan>
    ): String {
        val sb = StringBuilder()

        // Formatowanie daty
        val dateStr = if (dateMillis != null && dateMillis > 0) {
            SimpleDateFormat("d MMMM yyyy", Locale("pl", "PL")).format(Date(dateMillis))
        } else {
            "Termin do ustalenia"
        }

        // --- CSS STYLES (Tu dzieje się magia wyglądu) ---
        sb.append("""
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <style>
                    body {
                        font-family: 'Segoe UI', Roboto, Helvetica, Arial, sans-serif;
                        color: #333;
                        margin: 0;
                        padding: 20px;
                        line-height: 1.5;
                        font-size: 14px;
                    }
                    /* HEADER */
                    .header {
                        text-align: center;
                        margin-bottom: 30px;
                        border-bottom: 2px solid #0056b3;
                        padding-bottom: 10px;
                    }
                    .header h1 {
                        margin: 0;
                        color: #0056b3;
                        font-size: 28px;
                        text-transform: uppercase;
                        letter-spacing: 1px;
                    }
                    .header p {
                        margin: 5px 0 0 0;
                        color: #666;
                        font-style: italic;
                    }
                    
                    /* DAY CONTAINER - KLUCZ DO NIEDZIELENIA STRON */
                    .day-container {
                        border: 1px solid #e0e0e0;
                        border-radius: 8px;
                        margin-bottom: 20px;
                        overflow: hidden;
                        background-color: #fff;
                        /* To zapobiega łamaniu strony w środku dnia: */
                        page-break-inside: avoid;
                        break-inside: avoid;
                        box-shadow: 0 2px 5px rgba(0,0,0,0.05);
                    }
                    
                    .day-header {
                        background-color: #f8f9fa;
                        padding: 12px 15px;
                        border-bottom: 1px solid #e0e0e0;
                        display: flex;
                        justify-content: space-between;
                        align-items: center;
                    }
                    .day-title {
                        font-weight: bold;
                        font-size: 16px;
                        color: #2c3e50;
                    }
                    .day-number {
                        background-color: #2c3e50;
                        color: #fff;
                        padding: 2px 8px;
                        border-radius: 12px;
                        font-size: 12px;
                        font-weight: bold;
                    }
                    
                    /* SLOTY */
                    .slots-wrapper {
                        padding: 10px 15px;
                    }
                    .slot {
                        margin-bottom: 12px;
                        padding-left: 12px;
                        border-left: 4px solid #ccc;
                    }
                    .slot:last-child {
                        margin-bottom: 0;
                    }
                    
                    /* Kolory pasków bocznych dla pór dnia */
                    .slot.morning { border-left-color: #f1c40f; } /* Żółty */
                    .slot.midday  { border-left-color: #3498db; } /* Niebieski */
                    .slot.evening { border-left-color: #9b59b6; } /* Fiolet */

                    .slot-label {
                        font-size: 10px;
                        text-transform: uppercase;
                        color: #7f8c8d;
                        font-weight: bold;
                        margin-bottom: 2px;
                    }
                    .slot-title {
                        font-size: 14px;
                        font-weight: 600;
                        color: #000;
                    }
                    .slot-desc {
                        font-size: 13px;
                        color: #555;
                        margin-top: 2px;
                        font-style: italic;
                    }
                    
                    /* FOOTER */
                    .footer {
                        margin-top: 40px;
                        text-align: center;
                        font-size: 10px;
                        color: #aaa;
                        border-top: 1px solid #eee;
                        padding-top: 10px;
                    }
                </style>
            </head>
            <body>
        """.trimIndent())

        // --- BODY CONTENT ---
        sb.append("""
            <div class="header">
                <h1>$title</h1>
                <p>Data rozpoczęcia podróży: $dateStr</p>
            </div>
        """.trimIndent())

        days.forEach { day ->
            sb.append("""
                <div class="day-container">
                    <div class="day-header">
                        <span class="day-title">Dzień ${day.day}</span>
                        </div>
                    <div class="slots-wrapper">
            """.trimIndent())

            // Funkcja pomocnicza do generowania slotu
            fun appendSlotHtml(cssClass: String, label: String, slot: SlotPlan?) {
                if (slot == null || slot.title.isBlank()) return

                val safeDesc = if (!slot.description.isBlank()) """<div class="slot-desc">${slot.description}</div>""" else ""

                sb.append("""
                    <div class="slot $cssClass">
                        <div class="slot-label">$label</div>
                        <div class="slot-title">${slot.title}</div>
                        $safeDesc
                    </div>
                """.trimIndent())
            }

            appendSlotHtml("morning", "Rano", day.morning)
            appendSlotHtml("midday", "Południe", day.midday)
            appendSlotHtml("evening", "Wieczór", day.evening)

            sb.append("""
                    </div> </div> """.trimIndent())
        }

        // Stopka
        sb.append("""
            <div class="footer">
                Wygenerowano automatycznie przez aplikację PlanWakacje • ${SimpleDateFormat("yyyy-MM-dd HH:mm").format(Date())}
            </div>
            </body>
            </html>
        """.trimIndent())

        return sb.toString()
    }

    /**
     * Otwiera systemowe okno "Drukuj / Zapisz jako PDF" dla podanego HTML.
     */
    suspend fun openPrintDialog(
        activity: Activity,
        html: String,
        jobName: String
    ) {
        withContext(Dispatchers.Main) {
            val webView = WebView(activity)

            webView.settings.javaScriptEnabled = false
            webView.settings.domStorageEnabled = false
            webView.settings.loadWithOverviewMode = true
            webView.settings.useWideViewPort = true

            webView.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String?) {
                    val printManager = activity.getSystemService(Context.PRINT_SERVICE) as? PrintManager
                    if (printManager == null) {
                        // Log błąd lub obsłuż brak serwisu
                        return
                    }

                    val adapter = view.createPrintDocumentAdapter(jobName)

                    val attributes = PrintAttributes.Builder()
                        .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
                        .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
                        .build()

                    printManager.print(jobName, adapter, attributes)

                    // Zostaw WebView na chwilę, żeby system zdążył wystartować podgląd
                    view.postDelayed({
                        try { view.destroy() } catch (_: Throwable) {}
                    }, 12_000)
                }
            }

            webView.loadDataWithBaseURL(
                null,
                html,
                "text/html",
                "utf-8",
                null
            )
        }
    }
}