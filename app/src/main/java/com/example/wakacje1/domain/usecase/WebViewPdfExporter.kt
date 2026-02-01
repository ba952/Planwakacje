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
 * Klasa narzędziowa (Utility) integrująca systemowy PrintManager z silnikiem renderowania WebView.
 * Służy do fizycznego przekształcenia kodu HTML w dokument PDF (poprzez funkcję "Zapisz jako PDF" systemu Android).
 */
object WebViewPdfExporter {

    /**
     * Inicjuje proces drukowania.
     * Wymaga Dispatchers.Main, ponieważ konstruktor WebView rzuca wyjątek na wątkach tła.
     */
    suspend fun openPrintDialog(
        activity: Activity,
        html: String,
        jobName: String
    ) {
        withContext(Dispatchers.Main) {
            // Tworzymy "niewidzialne" WebView tylko na potrzeby renderowania wydruku.
            // Ważne: WebView jest ciężkim obiektem i trzyma referencję do Activity (ryzyko wycieku pamięci).
            val webView = WebView(activity)

            webView.settings.apply {
                javaScriptEnabled = false // Bezpieczeństwo + szybkość (mamy statyczny HTML)
                domStorageEnabled = false
                loadWithOverviewMode = true
                useWideViewPort = true
            }

            // Czekamy na wyrenderowanie HTML zanim przekażemy go do PrintManagera
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
            view.destroy() // Sprzątamy od razu, jeśli brak usługi
            return
        }

        // Adapter konwertujący zawartość WebView na format druku
        val adapter = view.createPrintDocumentAdapter(jobName)

        val attributes = PrintAttributes.Builder()
            .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
            .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
            .build()

        printManager.print(jobName, adapter, attributes)

        // --- PROBLEM TECHNICZNY (RACE CONDITION) ---
        // Android PrintManager nie zwraca callbacka (Success/Error) po zakończeniu generowania PDF.
        // Jeśli zniszczymy WebView (view.destroy()) natychmiast po wywołaniu .print(),
        // systemowy spooler otrzyma pusty widok i wygeneruje białą stronę.
        //
        // WORKAROUND: Opóźniamy zniszczenie WebView o 12 sekund.
        // To czas wystarczający na przetworzenie dokumentu przez system, a jednocześnie
        // pozwala zwolnić pamięć RAM, gdy użytkownik już dawno zamknie ekran drukowania.
        view.postDelayed({
            cleanUpWebViewSafely(activity, view)
        }, 12_000)
    }

    private fun cleanUpWebViewSafely(activity: Activity, view: WebView) {
        // Defensive Programming:
        // Sprawdzamy stan Activity, aby uniknąć crasha przy próbie operacji na martwym kontekście.
        if (activity.isFinishing || activity.isDestroyed) {
            // Activity i tak czyści swoje zasoby, więc wymuszamy tylko destroy() dla pewności
            try { view.destroy() } catch (_: Throwable) {}
        } else {
            // Activity żyje - musimy ręcznie posprzątać WebView, żeby nie zapchać pamięci (Memory Leak)
            try {
                view.parent?.let { (it as? android.view.ViewGroup)?.removeView(view) }
                view.destroy()
            } catch (_: Throwable) {
                // Ignorujemy wewnętrzne błędy WebView przy niszczeniu
            }
        }
    }
}