// ExportPlanPdfUseCase.kt
package com.example.wakacje1.domain.usecase

import android.app.Activity
import com.example.wakacje1.domain.model.DayPlan
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ExportPlanPdfUseCase {

    suspend fun execute(
        activity: Activity,
        destinationName: String?,
        tripStartDateMillis: Long?,
        plan: List<DayPlan>
    ): String {
        if (plan.isEmpty()) return "Brak planu do eksportu."

        val html = buildHtml(destinationName, tripStartDateMillis, plan)

        // dialog drukowania musi iść na MAIN (tu i tak trzymamy porządek)
        withContext(Dispatchers.Main) {
            WebViewPdfExporter.openPrintDialog(
                activity = activity,
                html = html,
                jobName = "Plan wyjazdu"
            )
        }

        return "Otworzono okno drukowania — wybierz „Zapisz jako PDF”."
    }

    private fun buildHtml(
        destinationName: String?,
        tripStartDateMillis: Long?,
        plan: List<DayPlan>
    ): String {
        val dest = destinationName?.takeIf { it.isNotBlank() } ?: "Wyjazd"
        val start = tripStartDateMillis?.let { formatDate(it) }

        val daysHtml = buildString {
            for (d in plan) {
                append(
                    """
                    <section class="day">
                      <div class="dayHeader">
                        <div class="dayTitle">Dzień ${d.day}</div>
                      </div>
                      <div class="dayBody">
                        ${formatDetailsAsHtml(d.details)}
                      </div>
                    </section>
                    """.trimIndent()
                )
            }
        }

        // Bez budżetu, bez "opcjonalnie", i dzień nie dzieli się na 2 strony:
        return """
            <!doctype html>
            <html lang="pl">
            <head>
              <meta charset="utf-8" />
              <style>
                @page { size: A4; margin: 14mm; }
                body { font-family: sans-serif; color: #111; }
                .header { margin-bottom: 12px; }
                .h1 { font-size: 20px; font-weight: 700; margin: 0 0 4px 0; }
                .sub { font-size: 12px; margin: 0; color: #333; }

                .day {
                  border: 1px solid #ddd;
                  border-radius: 10px;
                  padding: 10px 12px;
                  margin: 0 0 10px 0;

                  break-inside: avoid;
                  page-break-inside: avoid;
                }

                .dayTitle { font-size: 16px; font-weight: 700; }
                .dayBody { margin-top: 8px; font-size: 13px; line-height: 1.35; }
                .blockTitle { font-weight: 700; margin-top: 8px; }
                .bullet { margin: 4px 0 0 0; padding-left: 14px; }
                .bullet li { margin: 2px 0; }
              </style>
            </head>
            <body>
              <div class="header">
                <div class="h1">Plan wyjazdu — ${escapeHtml(dest)}</div>
                ${if (start != null) "<div class=\"sub\">Start: ${escapeHtml(start)}</div>" else ""}
              </div>

              $daysHtml
            </body>
            </html>
        """.trimIndent()
    }

    private fun formatDetailsAsHtml(details: String): String {
        val lines = details.split("\n")
        val out = StringBuilder()
        var inList = false

        fun closeList() {
            if (inList) {
                out.append("</ul>")
                inList = false
            }
        }

        for (raw in lines) {
            val line = raw.trim()
            if (line.isBlank()) {
                closeList()
                continue
            }

            if (line.startsWith("Poranek:") || line.startsWith("Południe:") || line.startsWith("Wieczór:")) {
                closeList()
                out.append("<div class=\"blockTitle\">${escapeHtml(line)}</div>")
                continue
            }

            if (line.startsWith("-")) {
                if (!inList) {
                    out.append("<ul class=\"bullet\">")
                    inList = true
                }
                out.append("<li>${escapeHtml(line.removePrefix("-").trim())}</li>")
                continue
            }

            closeList()
            out.append("<div>${escapeHtml(line)}</div>")
        }

        closeList()
        return out.toString()
    }

    private fun escapeHtml(s: String): String =
        s.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;")

    private fun formatDate(millis: Long): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(Date(millis))
}
