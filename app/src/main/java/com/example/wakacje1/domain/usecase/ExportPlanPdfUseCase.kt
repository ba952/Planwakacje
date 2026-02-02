package com.example.wakacje1.domain.usecase

import com.example.wakacje1.R
import com.example.wakacje1.domain.model.DayPlan
import com.example.wakacje1.domain.util.StringProvider
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * UseCase generujący kod HTML planu wycieczki.
 * Poprawiony: korzysta ze StringProvidera, aby nie trzymać hardcoded stringów.
 */
class ExportPlanPdfUseCase(
    private val stringProvider: StringProvider
) {

    suspend fun execute(
        destinationName: String?,
        tripStartDateMillis: Long?,
        plan: List<DayPlan>
    ): String {
        if (plan.isEmpty()) return ""
        return buildHtml(destinationName, tripStartDateMillis, plan)
    }

    private fun buildHtml(
        destinationName: String?,
        tripStartDateMillis: Long?,
        plan: List<DayPlan>
    ): String {
        // ZMIANA: Pobieranie stringów z zasobów
        val dest = destinationName?.takeIf { it.isNotBlank() }
            ?: stringProvider.getString(R.string.pdf_default_title)

        val start = tripStartDateMillis?.let {
            stringProvider.getString(R.string.pdf_label_start, formatDate(it))
        }

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
                ${if (start != null) "<div class=\"sub\">${escapeHtml(start)}</div>" else ""}
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