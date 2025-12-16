package com.example.wakacje1.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Button
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.wakacje1.ui.Destination
import com.example.wakacje1.ui.VacationViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DestinationSelectionScreen(
    viewModel: VacationViewModel,
    onDestinationChosen: () -> Unit,
    onBack: () -> Unit
) {
    val prefs = viewModel.preferences
    val suggestions = viewModel.destinationSuggestions

    LaunchedEffect(prefs) {
        // Na wszelki wypadek, jeśli ktoś wejdzie na ekran bez przygotowanych propozycji
        if (prefs != null && suggestions.isEmpty()) {
            viewModel.prepareDestinationSuggestions()
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Propozycje wyjazdu") },
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Text("Wstecz")
                    }
                }
            )
        }
    ) { innerPadding ->
        if (prefs == null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(innerPadding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "Brak ustawionych preferencji.",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.error
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Wróć do poprzedniego ekranu i uzupełnij dane o budżecie, liczbie dni i preferencjach.",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(innerPadding)
                .padding(16.dp)
        ) {
            Text(
                text = "Na podstawie Twoich preferencji wygenerowano propozycje wyjazdu. Wybierz jedno miejsce, aby zobaczyć szczegółowy plan.",
                style = MaterialTheme.typography.bodyMedium
            )

            Spacer(Modifier.height(12.dp))

            if (suggestions.isEmpty()) {
                Text(
                    text = "Nie udało się dobrać sensownych propozycji dla podanego budżetu lub ustawień.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Spróbuj zwiększyć budżet, zmniejszyć liczbę dni lub zmienić region / styl podróży.",
                    style = MaterialTheme.typography.bodySmall
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(suggestions) { dest ->
                        DestinationCard(
                            destination = dest,
                            prefs = prefs,
                            onClick = {
                                viewModel.chooseDestination(dest)
                                viewModel.generatePlan()
                                onDestinationChosen()
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DestinationCard(
    destination: Destination,
    prefs: com.example.wakacje1.ui.Preferences,
    onClick: () -> Unit
) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .clickable(onClick = onClick)
        ) {
            Column(
                modifier = Modifier
                    .padding(12.dp)
            ) {
                Text(
                    text = destination.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "${destination.country} • ${destination.region} • klimat: ${destination.climate}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.height(6.dp))

                // Budżet
                val days = prefs.days.coerceAtLeast(1)
                val budgetPerDay = prefs.budget.toDouble() / days.toDouble()
                Text(
                    text = "Twój budżet: ~${budgetPerDay.toInt()} zł/dzień",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = "Minimalny komfortowy budżet dla tego miejsca: ${destination.minBudgetPerDay}–${destination.typicalBudgetPerDay} zł/dzień",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.height(6.dp))

                // Krótkie podsumowanie dopasowania
                val budgetRatio = budgetPerDay / destination.minBudgetPerDay.toDouble()
                val budgetText = when {
                    budgetRatio < 0.8 ->
                        "Twój budżet jest raczej niski jak na to miejsce – plan będzie dość oszczędny."
                    budgetRatio in 0.8..1.3 ->
                        "Budżet wygląda rozsądnie dla tego miejsca."
                    else ->
                        "Budżet jest komfortowy – można pozwolić sobie na więcej atrakcji."
                }

                Text(
                    text = budgetText,
                    style = MaterialTheme.typography.bodySmall
                )

                if (destination.tags.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        destination.tags.take(3).forEach { tag ->
                            TagChip(text = tag)
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Button(onClick = onClick) {
                        Text("Wybierz to miejsce")
                    }
                }
            }
        }
    }
}

@Composable
private fun TagChip(text: String) {
    ElevatedCard(
        modifier = Modifier
            .height(28.dp)
    ) {
        Box(
            modifier = Modifier
                .padding(horizontal = 8.dp, vertical = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}
