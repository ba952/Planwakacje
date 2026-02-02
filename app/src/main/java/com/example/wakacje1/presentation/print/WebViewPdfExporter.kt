package com.example.wakacje1.presentation.print

import android.app.Activity
import android.content.Context
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Narzędzie UI/Platform do renderowania HTML w systemowym oknie druku (Zapisz jako PDF).
 * To NIE jest domena — zależy od Androida (WebView/PrintManager).
 */
object WebViewPdfExporter {

    /**
     * Inicjuje proces drukowania.
     * Wymaga Dispatchers.Main, bo WebView nie może powstać na wątku tła.
     */
    suspend fun openPrintDialog(
        activity: Activity,
        html: String,
        jobName: String
    ) {
        withContext(Dispatchers.Main) {
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
        }
    }

    private fun createPrintJob(activity: Activity, view: WebView, jobName: String) {
        val printManager = activity.getSystemService(Context.PRINT_SERVICE) as? PrintManager
        if (printManager == null) {
            view.destroy()
            return
        }

        val adapter = view.createPrintDocumentAdapter(jobName)

        val attributes = PrintAttributes.Builder()
            .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
            .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
            .build()

        printManager.print(jobName, adapter, attributes)

        // Workaround: nie niszczymy WebView od razu (inaczej potrafi wygenerować pustą stronę)
        view.postDelayed({
            cleanUpWebViewSafely(activity, view)
        }, 12_000)
    }

    private fun cleanUpWebViewSafely(activity: Activity, view: WebView) {
        if (activity.isFinishing || activity.isDestroyed) {
            try { view.destroy() } catch (_: Throwable) {}
        } else {
            try {
                view.parent?.let { (it as? android.view.ViewGroup)?.removeView(view) }
                view.destroy()
            } catch (_: Throwable) {}
        }
    }
}
