// WebViewPdfExporter.kt
package com.example.wakacje1.domain.usecase

import android.app.Activity
import android.content.Context
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object WebViewPdfExporter {

    /**
     * Otwiera systemowe okno "Drukuj / Zapisz jako PDF" dla podanego HTML.
     * To jest wspierana droga (bez zabaw w LayoutResultCallback).
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
                    val printManager = activity.getSystemService(Context.PRINT_SERVICE) as PrintManager

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
