package com.example.wakacje1.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.wakacje1.R
import com.example.wakacje1.domain.model.Destination
import com.example.wakacje1.presentation.common.UiText
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
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val prefs = uiState.preferences
    val suggestions = uiState.destinationSuggestions
    val chosen = uiState.chosenDestination
    val weather = uiState.weather

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
                title = { Text(stringResource(R.string.title_destination_selection)) },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text(stringResource(R.string.btn_return)) }
                },
                actions = {
                    TextButton(
                        onClick = {
                            val d = uiState.chosenDestination
                            if (d != null && d.apiQuery.isNotBlank()) {
                                viewModel.loadWeatherForCity(d.apiQuery, force = true)
                                viewModel.loadForecastForTrip(force = true)
                            }
                        }
                    ) { Text(stringResource(R.string.btn_refresh_weather)) }
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
                    text = stringResource(R.string.msg_select_destination_instruction),
                    style = MaterialTheme.typography.bodyMedium
                )

                WeatherCard(
                    cityLabel = chosen?.displayName,
                    weatherCity = weather.city,
                    temp = weather.temperature,
                    desc = weather.description,
                    loading = weather.loading,
                    error = weather.error
                )

                HorizontalDivider()

                if (prefs == null) {
                    Text(stringResource(R.string.msg_no_preferences), color = MaterialTheme.colorScheme.error)
                    OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.btn_return))
                    }
                    return@Centered
                }

                if (suggestions.isEmpty()) {
                    Text(
                        text = stringResource(R.string.msg_no_suggestions),
                        color = MaterialTheme.colorScheme.error
                    )
                    OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.btn_return))
                    }
                    return@Centered
                }

                Text(
                    text = stringResource(R.string.msg_suggestions_header, 3, ""),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 8.dp)
                ) {
                    itemsIndexed(suggestions) { idx, item ->
                        DestinationCard(
                            viewModel = viewModel,
                            destination = item,
                            selected = selectedIndex.intValue == idx,
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
                    OutlinedButton(onClick = onBack, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.btn_return))
                    }

                    ElevatedButton(
                        onClick = {
                            val sel = selectedIndex.intValue
                            val d = suggestions.getOrNull(sel)
                            if (d == null) {
                                localError.value = context.getString(R.string.error_select_place)
                                return@ElevatedButton
                            }

                            val netDailyBudget = viewModel.getBudgetPerDayWithTransport(d)
                            val minRequired = (d.minBudgetPerDay * 0.5).toInt()

                            if (netDailyBudget < minRequired) {
                                val tLabel = "szacunkowe koszty"
                                val transport = viewModel.getTransportCostUsedForSuggestions(d)

                                localError.value = context.getString(
                                    R.string.error_budget_too_low_daily,
                                    tLabel,
                                    netDailyBudget, // Tyle mamy
                                    transport,
                                    d.transportCostRoundTripPlnMin,
                                    d.transportCostRoundTripPlnMax,
                                    minRequired // Tyle potrzebujemy
                                )
                                // ZMIANA KLUCZOWA: BLOKADA PRZEJŚCIA
                                // Jeśli kasy jest za mało, przerywamy funkcję (return).
                                // Nie generujemy planu, nie przechodzimy dalej.
                                return@ElevatedButton
                            }

                            // Przejście następuje TYLKO, jeśli budżet jest OK
                            viewModel.generatePlan()
                            onDestinationChosen()
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text(stringResource(R.string.btn_show_plan)) }
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
    onSelect: () -> Unit
) {
    val netDailyBudget = viewModel.getBudgetPerDayWithTransport(destination)
    val okBudget = netDailyBudget >= (destination.minBudgetPerDay * 0.5)

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
                    text = if (okBudget) stringResource(R.string.status_ok) else stringResource(R.string.status_budget_low),
                    color = if (okBudget) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(Modifier.height(8.dp))

            Text(
                text = stringResource(R.string.labels_region_climate, destination.region, destination.climate),
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(6.dp))

            Text(
                text = stringResource(
                    R.string.msg_transport_simple_range,
                    destination.transportCostRoundTripPlnMin,
                    destination.transportCostRoundTripPlnMax
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Text(
                text = stringResource(R.string.msg_budget_info, netDailyBudget, destination.minBudgetPerDay),
                style = MaterialTheme.typography.bodySmall
            )

            if (destination.tags.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.msg_tags, destination.tags.joinToString(", ")),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

// WeatherCard components zostają bez zmian
@Composable
private fun WeatherCard(cityLabel: String?, weatherCity: String?, temp: Double?, desc: String?, loading: Boolean, error: String?) {
    WeatherCardInternal(cityLabel, weatherCity, temp, desc, loading, error)
}

@Composable
private fun WeatherCard(cityLabel: String?, weatherCity: String?, temp: Double?, desc: String?, loading: Boolean, error: UiText?) {
    WeatherCardInternal(cityLabel, weatherCity, temp, desc, loading, error?.asString())
}

@Composable
private fun WeatherCardInternal(cityLabel: String?, weatherCity: String?, temp: Double?, desc: String?, loading: Boolean, errorText: String?) {
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Column(Modifier.padding(14.dp)) {
            Text(text = stringResource(R.string.title_weather), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            val label = cityLabel ?: weatherCity ?: "—"
            when {
                loading -> Text(stringResource(R.string.msg_weather_loading, label))
                errorText != null -> Text(stringResource(R.string.error_prefix, errorText), color = MaterialTheme.colorScheme.error)
                temp != null || !desc.isNullOrBlank() -> {
                    val t = temp?.let { "${it.roundToInt()}°C" } ?: ""
                    val d = desc ?: ""
                    Text(stringResource(R.string.msg_weather_now, label, d, t))
                }
                else -> Text(stringResource(R.string.msg_select_place_first))
            }
        }
    }
}