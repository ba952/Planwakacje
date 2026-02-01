package com.example.wakacje1.domain.usecase

import android.app.Activity
import android.content.Context
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Klasa pomocnicza (Helper) służąca WYŁĄCZNIE do uruchomienia systemowego okna drukowania.
 * Nie zawiera logiki generowania HTML.
 */
object WebViewPdfExporter {

    /**
     * Otwiera systemowe okno "Drukuj / Zapisz jako PDF" dla podanego kodu HTML.
     */
    suspend fun openPrintDialog(
        activity: Activity,
        html: String,
        jobName: String
    ) {
        withContext(Dispatchers.Main) {
            // Tworzymy WebView dynamicznie. Ważne: WebView trzyma Context (Activity).
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
            // Jeśli brak serwisu drukowania, czyścimy od razu, by nie leakować pamięci
            view.destroy()
            return
        }

        val adapter = view.createPrintDocumentAdapter(jobName)

        val attributes = PrintAttributes.Builder()
            .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
            .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
            .build()

        printManager.print(jobName, adapter, attributes)

        // UWAGA INŻYNIERSKA:
        // Android PrintManager nie udostępnia callbacka o zakończeniu/anulowaniu drukowania.
        // Jeśli zniszczymy WebView (view.destroy()) natychmiast, proces generowania PDF zawiedzie (pusta strona).
        // Rozwiązaniem (workaround) jest opóźnione zwolnienie zasobów.
        // Czas 10-12s jest wystarczający, by systemowy spooler przetworzył dokument.
        view.postDelayed({
            cleanUpWebViewSafely(activity, view)
        }, 12_000)
    }

    private fun cleanUpWebViewSafely(activity: Activity, view: WebView) {
        // Zabezpieczenie przed wyciekiem/crashem:
        // Nie operujemy na WebView, jeśli Activity, która je stworzyła, już nie żyje.
        if (activity.isFinishing || activity.isDestroyed) {
            // System i tak zwolni zasoby Activity, więc możemy tylko spróbować "delikatnie" wyczyścić,
            // ale najważniejsze, że nie wywołujemy logiki na martwym obiekcie.
            try { view.destroy() } catch (_: Throwable) {}
        } else {
            // Activity żyje, więc bezpiecznie niszczymy WebView, by zwolnić pamięć RAM.
            try {
                view.parent?.let { (it as? android.view.ViewGroup)?.removeView(view) }
                view.destroy()
            } catch (_: Throwable) {
                // Ignorujemy błędy wewnętrzne WebView przy niszczeniu
            }
        }
    }
}