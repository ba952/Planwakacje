package com.example.wakacje1.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.wakacje1.ui.DayPlan
import com.example.wakacje1.ui.DaySlot
import com.example.wakacje1.ui.DayWeatherUi
import com.example.wakacje1.ui.UiDaySlot
import com.example.wakacje1.ui.VacationViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanScreen(
    viewModel: VacationViewModel,
    onBack: () -> Unit
) {
    val chosen = viewModel.chosenDestination
    val plan = viewModel.plan
    val weather = viewModel.weather
    val prefs = viewModel.preferences

    var showRegenerateAllDialog by remember { mutableStateOf(false) }

    LaunchedEffect(chosen) {
        if (chosen != null && plan.isEmpty()) {
            viewModel.generatePlan()
        }
    }

    // ładowanie prognozy dla całego zakresu podróży
    LaunchedEffect(chosen?.id, prefs?.startDateMillis, prefs?.days) {
        val p = prefs
        if (chosen != null && p != null && p.startDateMillis != null && p.days > 0) {
            viewModel.loadForecastForTrip()
        }
    }

    val dateFormatter = remember {
        SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
    }

    fun formatDate(millis: Long?): String? {
        if (millis == null) return null
        return dateFormatter.format(Date(millis))
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Plan podróży") },
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Text("Wstecz")
                    }
                }
            )
        }
    ) { innerPadding ->
        if (chosen == null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "Nie wybrano miejsca.",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.error
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Wróć do poprzedniego ekranu i wybierz jedną z propozycji.",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Nagłówek planu
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Text(
                    text = chosen.displayName,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold
                )

                if (prefs != null) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Budżet: ${prefs.budget} zł na ${prefs.days} dni, styl: ${prefs.style}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    val startStr = formatDate(prefs.startDateMillis)
                    val endStr = formatDate(prefs.endDateMillis)
                    if (startStr != null && endStr != null) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = "Termin: $startStr – $endStr (${prefs.days} dni)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Sekcja pogody ogólnej
            WeatherSummarySectionSimple(
                cityLabel = weather.city ?: chosen.displayName,
                loading = weather.loading,
                error = weather.error,
                temperature = weather.temperature,
                description = weather.description,
                onReload = { viewModel.loadWeatherForCity(chosen.apiQuery) },
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 12.dp)
            )

            // Akcje globalne
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Button(onClick = { showRegenerateAllDialog = true }) {
                    Text("Przegeneruj cały plan")
                }
            }

            Spacer(Modifier.height(12.dp))

            if (plan.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                ) {
                    Text(
                        text = "Plan nie został jeszcze wygenerowany.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            } else {
                Text(
                    text = "Szczegółowy harmonogram (edycja poranka, południa i wieczoru):",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 8.dp)
                )

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    itemsIndexed(plan) { index, day ->
                        val uiSlots = viewModel.getUiSlotsForDay(index)
                        val dayWeather = viewModel.getWeatherForDayIndex(index)

                        EnhancedDayPlanCard(
                            dayIndex = index,
                            dayPlan = day,
                            slots = uiSlots,
                            dayWeather = dayWeather,
                            onMoveUp = { viewModel.moveDayUp(index) },
                            onMoveDown = { viewModel.moveDayDown(index) },
                            canMoveUp = index > 0,
                            canMoveDown = index < plan.lastIndex,
                            onRegenerateDay = { viewModel.regenerateDay(index) },
                            onRollMorning = { viewModel.rollNewActivity(index, DaySlot.MORNING) },
                            onRollMidday = { viewModel.rollNewActivity(index, DaySlot.MIDDAY) },
                            onRollEvening = { viewModel.rollNewActivity(index, DaySlot.EVENING) },
                            onSetCustomMorning = { title, desc ->
                                viewModel.setCustomActivity(index, DaySlot.MORNING, title, desc)
                            },
                            onSetCustomMidday = { title, desc ->
                                viewModel.setCustomActivity(index, DaySlot.MIDDAY, title, desc)
                            },
                            onSetCustomEvening = { title, desc ->
                                viewModel.setCustomActivity(index, DaySlot.EVENING, title, desc)
                            }
                        )
                    }
                }
            }
        }

        if (showRegenerateAllDialog) {
            AlertDialog(
                onDismissRequest = { showRegenerateAllDialog = false },
                title = { Text("Przegenerować cały plan?") },
                text = {
                    Text(
                        "Przegenerowanie planu spowoduje utratę wszystkich ręcznych zmian (własne pomysły, podmiany slotów). " +
                                "Czy na pewno chcesz kontynuować?"
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.generatePlan()
                        showRegenerateAllDialog = false
                    }) {
                        Text("Przegeneruj")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showRegenerateAllDialog = false }) {
                        Text("Anuluj")
                    }
                }
            )
        }
    }
}

@Composable
private fun WeatherSummarySectionSimple(
    cityLabel: String,
    loading: Boolean,
    error: String?,
    temperature: Double?,
    description: String?,
    onReload: () -> Unit,
    modifier: Modifier = Modifier
) {
    ElevatedCard(
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "Pogoda – $cityLabel",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(Modifier.height(4.dp))

            when {
                loading -> {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp
                        )
                        Text(
                            text = "Pobieranie prognozy...",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                error != null -> {
                    Text(
                        text = "Błąd pobierania pogody: $error",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(Modifier.height(4.dp))
                    TextButton(onClick = onReload) {
                        Text("Spróbuj ponownie")
                    }
                }

                temperature != null && description != null -> {
                    Text(
                        text = "Aktualnie: ${temperature.toInt()}°C, $description",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                else -> {
                    Text(
                        text = "Brak danych pogodowych.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(Modifier.height(4.dp))
                    TextButton(onClick = onReload) {
                        Text("Pobierz pogodę")
                    }
                }
            }
        }
    }
}

@Composable
private fun EnhancedDayPlanCard(
    dayIndex: Int,
    dayPlan: DayPlan,
    slots: Triple<UiDaySlot, UiDaySlot, UiDaySlot>?,
    dayWeather: DayWeatherUi?,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onRegenerateDay: () -> Unit,
    onRollMorning: () -> Unit,
    onRollMidday: () -> Unit,
    onRollEvening: () -> Unit,
    onSetCustomMorning: (String, String) -> Unit,
    onSetCustomMidday: (String, String) -> Unit,
    onSetCustomEvening: (String, String) -> Unit
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "Dzień ${dayIndex + 1}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = dayPlan.title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )

            // Prognoza dla konkretnego dnia
            if (dayWeather != null && dayWeather.tempMin != null && dayWeather.tempMax != null) {
                Spacer(Modifier.height(2.dp))
                val descText = dayWeather.description?.takeIf { it.isNotBlank() }
                Text(
                    text = buildString {
                        append("Prognoza: ${dayWeather.tempMin.toInt()}–${dayWeather.tempMax.toInt()}°C")
                        if (descText != null) append(", $descText")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(8.dp))

            if (slots != null) {
                DaySlotSection(
                    label = "Poranek",
                    slot = slots.first,
                    onRoll = onRollMorning,
                    onSetCustom = onSetCustomMorning
                )
                Spacer(Modifier.height(6.dp))
                DaySlotSection(
                    label = "Południe",
                    slot = slots.second,
                    onRoll = onRollMidday,
                    onSetCustom = onSetCustomMidday
                )
                Spacer(Modifier.height(6.dp))
                DaySlotSection(
                    label = "Wieczór",
                    slot = slots.third,
                    onRoll = onRollEvening,
                    onSetCustom = onSetCustomEvening
                )
            } else {
                Text(
                    text = dayPlan.details,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onMoveUp, enabled = canMoveUp) {
                    Text("↑ Do góry")
                }
                TextButton(onClick = onMoveDown, enabled = canMoveDown) {
                    Text("↓ W dół")
                }
                TextButton(onClick = onRegenerateDay) {
                    Text("Generuj dzień jeszcze raz")
                }
            }
        }
    }
}

@Composable
private fun DaySlotSection(
    label: String,
    slot: UiDaySlot,
    onRoll: () -> Unit,
    onSetCustom: (String, String) -> Unit
) {
    var showCustomDialog by remember { mutableStateOf(false) }

    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(2.dp))
        Row {
            Text(
                text = slot.title,
                style = MaterialTheme.typography.bodyMedium
            )
            if (slot.isCustom) {
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "(własny pomysł)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        }
        if (slot.description.isNotBlank()) {
            Spacer(Modifier.height(2.dp))
            Text(
                text = slot.description,
                style = MaterialTheme.typography.bodySmall
            )
        }

        Spacer(Modifier.height(4.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(onClick = onRoll) {
                Text("Zmień pomysł")
            }
            TextButton(onClick = { showCustomDialog = true }) {
                Text("Własny pomysł")
            }
        }
    }

    if (showCustomDialog) {
        var title by remember(slot.title) { mutableStateOf(slot.title) }
        var desc by remember(slot.description) { mutableStateOf(slot.description) }

        AlertDialog(
            onDismissRequest = { showCustomDialog = false },
            title = { Text("Ustaw własny plan – $label") },
            text = {
                Column {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Tytuł") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = desc,
                        onValueChange = { desc = it },
                        label = { Text("Opis (opcjonalnie)") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 100.dp)
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    onSetCustom(title, desc)
                    showCustomDialog = false
                }) {
                    Text("Zapisz")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCustomDialog = false }) {
                    Text("Anuluj")
                }
            }
        )
    }
}
