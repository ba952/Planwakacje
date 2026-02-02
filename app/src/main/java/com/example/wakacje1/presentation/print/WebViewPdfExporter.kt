package com.example.wakacje1.presentation.print

import android.app.Activity
import android.content.Context
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.WebView
import android.webkit.WebViewClient

/**
 * Narzędzie do druku.
 * POPRAWKA: Zamiast trzymać WebView na siłę przez 12 sekund,
 * metoda zwraca utworzony WebView do Activity. Activity musi zadbać o jego wyczyszczenie (onDestroy).
 */
object WebViewPdfExporter {

    fun printHtml(
        activity: Activity,
        html: String,
        jobName: String
    ): WebView {
        val webView = WebView(activity)

        webView.settings.apply {
            javaScriptEnabled = false
            domStorageEnabled = false
            loadWithOverviewMode = true
            useWideViewPort = true
        }

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String?) {
                createPrintJob(activity, view, jobName)
            }
        }

        webView.loadDataWithBaseURL(null, html, "text/html", "utf-8", null)

        return webView
    }

    private fun createPrintJob(activity: Activity, view: WebView, jobName: String) {
        val printManager = activity.getSystemService(Context.PRINT_SERVICE) as? PrintManager
        if (printManager == null) {
            return
        }

        val adapter = view.createPrintDocumentAdapter(jobName)

        val attributes = PrintAttributes.Builder()
            .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
            .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
            .build()

        printManager.print(jobName, adapter, attributes)

        // Usunięto dirty hack z postDelayed.
        // WebView żyje teraz tyle co Activity (jeśli przypiszemy go do pola w Activity).
    }
}