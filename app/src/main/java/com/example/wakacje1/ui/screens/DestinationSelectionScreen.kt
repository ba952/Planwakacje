package com.example.wakacje1.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.example.wakacje1.domain.model.Destination
import com.example.wakacje1.presentation.viewmodel.VacationViewModel
import kotlin.math.roundToInt

private val MaxContentWidth = 520.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DestinationSelectionScreen(
    viewModel: VacationViewModel,
    onDestinationChosen: () -> Unit,
    onBack: () -> Unit
) {
    val prefs = viewModel.preferences
    val suggestions = viewModel.destinationSuggestions
    val chosen = viewModel.chosenDestination

    // przeliczaj propozycje, gdy zmienią się preferencje
    LaunchedEffect(
        prefs?.budget,
        prefs?.days,
        prefs?.region,
        prefs?.climate,
        prefs?.style
    ) {
        if (prefs != null) viewModel.prepareDestinationSuggestions()
    }

    val selectedIndex = rememberSaveable { mutableIntStateOf(-1) }
    val localError = remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Propozycje wyjazdu") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Wróć") } },
                actions = {
                    TextButton(
                        onClick = {
                            val d = viewModel.chosenDestination
                            if (d != null && d.apiQuery.isNotBlank()) {
                                viewModel.loadWeatherForCity(d.apiQuery, force = true)
                                viewModel.loadForecastForTrip(force = true)
                            }
                        }
                    ) { Text("Odśwież pogodę") }
                }
            )
        }
    ) { padding ->
        Centered(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Wybierz jedno z proponowanych miejsc. Potem wygenerujemy plan dzienny (poranek/południe/wieczór).",
                    style = MaterialTheme.typography.bodyMedium
                )

                WeatherCard(
                    cityLabel = chosen?.displayName,
                    weatherCity = viewModel.weather.city,
                    temp = viewModel.weather.temperature,
                    desc = viewModel.weather.description,
                    loading = viewModel.weather.loading,
                    error = viewModel.weather.error
                )

                Divider()

                if (prefs == null) {
                    Text("Brak preferencji — wróć i uzupełnij formularz.", color = MaterialTheme.colorScheme.error)
                    OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Wróć") }
                    return@Centered
                }

                if (suggestions.isEmpty()) {
                    Text(
                        text = "Brak propozycji dla tych preferencji (spróbuj zmienić budżet/region/klimat).",
                        color = MaterialTheme.colorScheme.error
                    )
                    OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Wróć") }
                    return@Centered
                }

                Text(
                    text = "Propozycje (3) • scenariusz transportu: ${viewModel.getTransportScenarioLabel()}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 8.dp)
                ) {
                    itemsIndexed(suggestions) { idx, item ->
                        DestinationCard(
                            viewModel = viewModel,
                            destination = item,
                            selected = selectedIndex.intValue == idx,
                            prefsBudget = prefs.budget,
                            prefsDays = prefs.days,
                            onSelect = {
                                selectedIndex.intValue = idx
                                localError.value = null
                                viewModel.chooseDestination(item)
                            }
                        )
                    }
                }

                localError.value?.let { Text(text = it, color = MaterialTheme.colorScheme.error) }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(onClick = onBack, modifier = Modifier.weight(1f)) { Text("Wróć") }

                    ElevatedButton(
                        onClick = {
                            val sel = selectedIndex.intValue
                            val d = suggestions.getOrNull(sel)
                            if (d == null) {
                                localError.value = "Najpierw wybierz miejsce."
                                return@ElevatedButton
                            }

                            val days = prefs.days.coerceAtLeast(1)
                            val transportUsed = viewModel.getTransportCostUsedForSuggestions(d)
                            val remaining = prefs.budget - transportUsed

                            if (remaining <= 0) {
                                localError.value =
                                    "Budżet za niski po doliczeniu transportu (${viewModel.getTransportScenarioLabel()}): użyte $transportUsed zł RT (zakres ~${d.transportCostRoundTripPlnMin}–${d.transportCostRoundTripPlnMax} zł)."
                                return@ElevatedButton
                            }

                            val budgetPerDay = (remaining.toDouble() / days).roundToInt()
                            if (budgetPerDay < d.minBudgetPerDay) {
                                localError.value =
                                    "Budżet za niski po transporcie (${viewModel.getTransportScenarioLabel()}): ok. $budgetPerDay zł/dzień (RT użyte: $transportUsed zł; zakres ~${d.transportCostRoundTripPlnMin}–${d.transportCostRoundTripPlnMax} zł), a minimum to ${d.minBudgetPerDay} zł/dzień."
                                return@ElevatedButton
                            }

                            viewModel.generatePlan()
                            onDestinationChosen()
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text("Pokaż plan") }
                }

                Spacer(Modifier.height(4.dp))
            }
        }
    }
}

@Composable
private fun Centered(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(modifier = modifier, contentAlignment = Alignment.TopCenter) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = MaxContentWidth)
        ) { content() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DestinationCard(
    viewModel: VacationViewModel,
    destination: Destination,
    selected: Boolean,
    prefsBudget: Int,
    prefsDays: Int,
    onSelect: () -> Unit
) {
    val days = prefsDays.coerceAtLeast(1)

    // SPÓJNIE Z ALGORYTMEM: używamy tego samego transportu co VM (Tmax/Tavg/Tmin)
    val transportUsed = viewModel.getTransportCostUsedForSuggestions(destination)
    val remaining = prefsBudget - transportUsed
    val budgetPerDay = if (remaining > 0) (remaining.toDouble() / days).roundToInt() else 0
    val okBudget = remaining > 0 && budgetPerDay >= destination.minBudgetPerDay

    val container = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
    val content = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = container, contentColor = content),
        elevation = CardDefaults.cardElevation(defaultElevation = if (selected) 6.dp else 1.dp),
        onClick = onSelect
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = destination.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(destination.country, style = MaterialTheme.typography.bodyMedium)
                }

                Text(
                    text = if (okBudget) "OK" else "Za niski budżet",
                    color = if (okBudget) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(Modifier.height(8.dp))

            Text(
                text = "Region: ${destination.region} • Klimat: ${destination.climate}",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(6.dp))

            Text(
                text = "Transport (RT): ~${destination.transportCostRoundTripPlnMin}–${destination.transportCostRoundTripPlnMax} zł • użyte: $transportUsed zł (${viewModel.getTransportScenarioLabel()})",
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                text = "Budżet po transporcie: ~${budgetPerDay} zł/dzień • Min: ${destination.minBudgetPerDay} zł/dzień",
                style = MaterialTheme.typography.bodySmall
            )

            if (destination.tags.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
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
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(
                text = "Pogoda",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(6.dp))

            val label = cityLabel ?: weatherCity ?: "—"

            when {
                loading -> Text("Ładowanie pogody dla: $label…")
                error != null -> Text("Błąd: $error", color = MaterialTheme.colorScheme.error)
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
