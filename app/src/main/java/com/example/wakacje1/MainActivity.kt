package com.example.wakacje1

import android.os.Bundle
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            Wakacje1Theme {
                LaunchedEffect(Unit) {
                    viewModel.events.collect { event ->
                        if (event is UiEvent.PrintPdf) {
                            WebViewPdfExporter.openPrintDialog(
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
