package com.example.wakacje1.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect

import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable

import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.wakacje1.data.model.Destination
import com.example.wakacje1.ui.viewmodel.VacationViewModel
import kotlin.math.roundToInt
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.OutlinedButton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DestinationSelectionScreen(
    viewModel: VacationViewModel,
    onDestinationChosen: () -> Unit,
    onBack: () -> Unit
) {
    val snack = remember { SnackbarHostState() }

    val prefs = viewModel.preferences
    val suggestions = viewModel.destinationSuggestions
    val chosen = viewModel.chosenDestination

    // Jeżeli ktoś wszedł tu "na skróty" i nie ma propozycji, spróbuj je przygotować.
    LaunchedEffect(Unit) {
        if (suggestions.isEmpty()) {
            viewModel.prepareDestinationSuggestions()
        }
    }

    val selectedIndex = rememberSaveable { mutableIntStateOf(-1) }
    val localError = remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Propozycje wyjazdu") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Wstecz")
                    }
                },
                actions = {
                    // Odśwież pogodę (jeśli jest wybrany kierunek)
                    IconButton(
                        onClick = {
                            val d = viewModel.chosenDestination
                            if (d != null && d.apiQuery.isNotBlank()) {
                                viewModel.loadWeatherForCity(d.apiQuery, force = true)
                                viewModel.loadForecastForTrip(force = true)
                            }
                        }
                    ) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Odśwież pogodę")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(hostState = snack) }
    ) { padding ->
        Column(
            modifier = Modifier.Companion
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            // Nagłówek / opis
            Text(
                text = "Wybierz jedno z proponowanych miejsc. Potem wygenerujemy plan dzienny (poranek/południe/wieczór).",
                style = MaterialTheme.typography.bodyMedium
            )

            Spacer(Modifier.Companion.height(12.dp))

            // Krótki box pogody bieżącej (dla wybranego miejsca)
            WeatherCard(
                cityLabel = chosen?.displayName,
                weatherCity = viewModel.weather.city,
                temp = viewModel.weather.temperature,
                desc = viewModel.weather.description,
                loading = viewModel.weather.loading,
                error = viewModel.weather.error
            )

            Spacer(Modifier.Companion.height(12.dp))
            Divider()
            Spacer(Modifier.Companion.height(12.dp))

            if (prefs == null) {
                Text(
                    text = "Brak preferencji — wróć i uzupełnij formularz.",
                    color = MaterialTheme.colorScheme.error
                )
                Spacer(Modifier.Companion.height(12.dp))
                OutlinedButton(onClick = onBack) { Text("Wróć") }
                return@Column
            }

            if (suggestions.isEmpty()) {
                Text(
                    text = "Brak propozycji dla tych preferencji (spróbuj zmienić budżet/region/klimat).",
                    color = MaterialTheme.colorScheme.error
                )
                Spacer(Modifier.Companion.height(12.dp))
                OutlinedButton(onClick = onBack) { Text("Wróć") }
                return@Column
            }

            Text(
                text = "Propozycje (3):",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Companion.SemiBold
            )
            Spacer(Modifier.Companion.height(8.dp))

            LazyColumn(
                modifier = Modifier.Companion.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                itemsIndexed(suggestions) { idx, item ->
                    DestinationCard(
                        destination = item,
                        selected = selectedIndex.intValue == idx,
                        prefsBudget = prefs.budget,
                        prefsDays = prefs.days,
                        onSelect = {
                            selectedIndex.intValue = idx
                            localError.value = null
                            viewModel.chooseDestination(item) // ustawia chosenDestination + odpala pogodę
                        }
                    )
                }
            }

            localError.value?.let {
                Spacer(Modifier.Companion.height(8.dp))
                Text(text = it, color = MaterialTheme.colorScheme.error)
            }

            Spacer(Modifier.Companion.height(12.dp))

            // Footer z przyciskami
            Row(
                modifier = Modifier.Companion.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onBack,
                    modifier = Modifier.Companion.weight(1f)
                ) {
                    Text("Wróć")
                }

                ElevatedButton(
                    onClick = {
                        val sel = selectedIndex.intValue
                        val d = suggestions.getOrNull(sel)
                        if (d == null) {
                            localError.value = "Najpierw wybierz miejsce."
                            return@ElevatedButton
                        }

                        // twarda walidacja budżetu
                        val days = prefs.days.coerceAtLeast(1)
                        val budgetPerDay = (prefs.budget.toDouble() / days).roundToInt()
                        if (budgetPerDay < d.minBudgetPerDay) {
                            localError.value =
                                "Budżet za niski: ok. $budgetPerDay zł/dzień, a minimalnie dla tego miejsca potrzeba ${d.minBudgetPerDay} zł/dzień."
                            return@ElevatedButton
                        }

                        // generowanie + przejście
                        viewModel.generatePlan()
                        onDestinationChosen()
                    },
                    modifier = Modifier.Companion.weight(1f)
                ) {
                    Text("Pokaż plan")
                }
            }
        }
    }
}

@Composable
private fun DestinationCard(
    destination: Destination,
    selected: Boolean,
    prefsBudget: Int,
    prefsDays: Int,
    onSelect: () -> Unit
) {
    val days = prefsDays.coerceAtLeast(1)
    val budgetPerDay = (prefsBudget.toDouble() / days).roundToInt()

    val okBudget = budgetPerDay >= destination.minBudgetPerDay

    val container = if (selected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surface
    }

    val content = if (selected) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    Card(
        modifier = Modifier.Companion.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = container, contentColor = content),
        elevation = CardDefaults.cardElevation(defaultElevation = if (selected) 6.dp else 1.dp),
        onClick = onSelect
    ) {
        Column(Modifier.Companion.padding(14.dp)) {
            Row(
                modifier = Modifier.Companion.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(Modifier.Companion.weight(1f)) {
                    Text(
                        text = destination.displayName,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Companion.Bold
                    )
                    Text(
                        text = destination.country,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                Text(
                    text = if (okBudget) "OK" else "Za niski budżet",
                    color = if (okBudget) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Companion.SemiBold
                )
            }

            Spacer(Modifier.Companion.height(10.dp))

            Text(
                text = "Region: ${destination.region} • Klimat: ${destination.climate}",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.Companion.height(6.dp))

            Text(
                text = "Budżet: min ${destination.minBudgetPerDay} zł/dzień, typowo ${destination.typicalBudgetPerDay} zł/dzień",
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.Companion.height(6.dp))

            if (destination.tags.isNotEmpty()) {
                Text(
                    text = "Tagi: ${destination.tags.joinToString(", ")}",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun WeatherCard(
    cityLabel: String?,
    weatherCity: String?,
    temp: Double?,
    desc: String?,
    loading: Boolean,
    error: String?
) {
    Card(
        modifier = Modifier.Companion.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
    ) {
        Column(Modifier.Companion.padding(14.dp)) {
            Text(
                text = "Pogoda",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Companion.SemiBold
            )
            Spacer(Modifier.Companion.height(6.dp))

            val label = cityLabel ?: weatherCity ?: "—"

            when {
                loading -> Text("Ładowanie pogody dla: $label…")
                error != null -> Text(
                    text = "Błąd: $error",
                    color = MaterialTheme.colorScheme.error
                )

                temp != null || !desc.isNullOrBlank() -> {
                    val t = temp?.let { "${it.roundToInt()}°C" } ?: ""
                    val d = desc ?: ""
                    Text("Miejsce: $label")
                    Text("Teraz: $d $t")
                }

                else -> Text("Wybierz miejsce, aby zobaczyć pogodę.")
            }
        }
    }
}