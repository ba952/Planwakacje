package com.example.wakacje1

import android.os.Bundle
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import com.example.wakacje1.presentation.common.UiEvent
import com.example.wakacje1.presentation.print.WebViewPdfExporter
import com.example.wakacje1.presentation.viewmodel.VacationViewModel
import com.example.wakacje1.ui.navigation.NavGraph
import com.example.wakacje1.ui.theme.Wakacje1Theme
import org.koin.androidx.viewmodel.ext.android.viewModel

class MainActivity : ComponentActivity() {

    private val viewModel: VacationViewModel by viewModel()

    // POPRAWKA: Przechowujemy referencję do WebView w Activity.
    // Zapobiega to zebraniu obiektu przez GC podczas drukowania i umożliwia bezpieczne czyszczenie.
    private var printWebView: WebView? = null

    // POPRAWKA: Sprzątanie zasobów przy niszczeniu Activity.
    override fun onDestroy() {
        super.onDestroy()
        printWebView?.destroy()
        printWebView = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            Wakacje1Theme {
                LaunchedEffect(Unit) {
                    viewModel.events.collect { event ->
                        if (event is UiEvent.PrintPdf) {
                            // 1. Sprzątamy poprzedni WebView, jeśli istnieje
                            printWebView?.destroy()

                            // 2. Tworzymy nowy WebView i przypisujemy do pola klasy.
                            // Metoda printHtml została zaktualizowana w poprzednim kroku, aby zwracać WebView.
                            printWebView = WebViewPdfExporter.printHtml(
                                activity = this@MainActivity,
                                html = event.html,
                                jobName = "Plan Wakacyjny"
                            )
                        }
                    }
                }

                NavGraph(
                    vacationViewModel = viewModel
                )
            }
        }
    }
}