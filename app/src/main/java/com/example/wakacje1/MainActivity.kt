package com.example.wakacje1

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.wakacje1.presentation.viewmodel.VacationViewModel
import com.example.wakacje1.ui.navigation.NavGraph
import com.example.wakacje1.ui.theme.Wakacje1Theme
import com.google.firebase.auth.FirebaseAuth
// ZMIANA: Dodaj ten import
import org.koin.androidx.viewmodel.ext.android.viewModel

class MainActivity : ComponentActivity() {

    // ZMIANA: Koin automatycznie wstrzyknie ViewModel wraz z Repozytoriami zdefiniowanymi w AppModule
    // Stary kod z ViewModelProvider można usunąć.
    private val viewModel: VacationViewModel by viewModel()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Pobieramy UID tylko po to, by przekazać do nawigacji (jeśli logika tego wymaga)
        val uid = FirebaseAuth.getInstance().currentUser?.uid

        setContent {
            Wakacje1Theme {
                // Przekazujemy wstrzyknięty ViewModel do grafu nawigacji
                NavGraph(
                    vacationViewModel = viewModel,
                    startUid = uid
                )
            }
        }
    }
}