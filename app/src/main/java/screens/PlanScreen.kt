package com.example.wakacje1.ui.screens

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
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.wakacje1.data.model.DayPlan
import com.example.wakacje1.data.model.DaySlot
import com.example.wakacje1.ui.theme.VacationViewModel
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanScreen(
    viewModel: VacationViewModel,
    onBack: () -> Unit
) {
    val plan = viewModel.plan
    val dest = viewModel.chosenDestination

    // jeśli ktoś wszedł tu bez planu, spróbuj go wygenerować
    LaunchedEffect(Unit) {
        if (plan.isEmpty() && dest != null) {
            viewModel.generatePlan()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Plan wyjazdu") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Wstecz")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        val d = viewModel.chosenDestination
                        if (d != null && d.apiQuery.isNotBlank()) {
                            viewModel.loadWeatherForCity(d.apiQuery, force = true)
                            viewModel.loadForecastForTrip(force = true)
                        }
                    }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Odśwież pogodę")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            HeaderWeather(viewModel)

            Spacer(Modifier.height(12.dp))

            if (dest != null) {
                Text(
                    text = "${dest.displayName}, ${dest.country}",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Region: ${dest.region} • Klimat: ${dest.climate}",
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Spacer(Modifier.height(12.dp))

            if (plan.isEmpty()) {
                Text(
                    text = "Brak wygenerowanego planu. Wróć i wybierz miejsce, albo spróbuj ponownie.",
                    color = MaterialTheme.colorScheme.error
                )
                Spacer(Modifier.height(10.dp))
                OutlinedButton(onClick = { viewModel.generatePlan() }) {
                    Text("Wygeneruj plan")
                }
                return@Column
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                itemsIndexed(plan) { index, day ->
                    DayCard(
                        day = day,
                        dayIndex = index,
                        canMoveUp = index > 0,
                        canMoveDown = index < plan.size - 1,
                        onMoveUp = { viewModel.moveDayUp(index) },
                        onMoveDown = { viewModel.moveDayDown(index) },
                        onRegenerateDay = { viewModel.regenerateDay(index) },
                        onRollSlot = { slot -> viewModel.rollNewActivity(index, slot) },
                        onCustomSlot = { slot, title, desc ->
                            viewModel.setCustomActivity(index, slot, title, desc)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun HeaderWeather(viewModel: VacationViewModel) {
    val w = viewModel.weather
    val label = viewModel.chosenDestination?.displayName ?: w.city ?: "—"

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(
                text = "Pogoda (teraz)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(6.dp))

            when {
                w.loading -> Text("Ładowanie pogody dla: $label…")
                w.error != null -> Text(
                    text = "Błąd: ${w.error}",
                    color = MaterialTheme.colorScheme.error
                )
                w.temperature != null || !w.description.isNullOrBlank() -> {
                    val temp = w.temperature?.let { "${it.roundToInt()}°C" } ?: ""
                    val desc = w.description ?: ""
                    Text("Miejsce: $label")
                    Text("Teraz: $desc $temp")
                }
                else -> Text("Brak danych pogodowych.")
            }
        }
    }
}

@Composable
private fun DayCard(
    day: DayPlan,
    dayIndex: Int,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRegenerateDay: () -> Unit,
    onRollSlot: (DaySlot) -> Unit,
    onCustomSlot: (DaySlot, String, String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "Dzień ${day.day}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = day.title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }

                Row {
                    IconButton(onClick = onMoveUp, enabled = canMoveUp) {
                        Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Dzień wyżej")
                    }
                    IconButton(onClick = onMoveDown, enabled = canMoveDown) {
                        Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Dzień niżej")
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // pełny opis (czytelny tekst)
            Text(
                text = day.details,
                style = MaterialTheme.typography.bodyMedium
            )

            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onRegenerateDay,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Generuj dzień jeszcze raz")
                }
            }

            Spacer(Modifier.height(12.dp))

            Text(
                text = "Edycja slotów:",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(8.dp))

            SlotEditorRow(
                label = "Poranek",
                onRoll = { onRollSlot(DaySlot.MORNING) },
                onCustom = { t, d -> onCustomSlot(DaySlot.MORNING, t, d) }
            )
            Spacer(Modifier.height(8.dp))
            SlotEditorRow(
                label = "Południe",
                onRoll = { onRollSlot(DaySlot.MIDDAY) },
                onCustom = { t, d -> onCustomSlot(DaySlot.MIDDAY, t, d) }
            )
            Spacer(Modifier.height(8.dp))
            SlotEditorRow(
                label = "Wieczór",
                onRoll = { onRollSlot(DaySlot.EVENING) },
                onCustom = { t, d -> onCustomSlot(DaySlot.EVENING, t, d) }
            )
        }
    }
}

@Composable
private fun SlotEditorRow(
    label: String,
    onRoll: () -> Unit,
    onCustom: (String, String) -> Unit
) {
    val showDialog = rememberSaveable { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        OutlinedButton(
            onClick = onRoll,
            modifier = Modifier.weight(1f)
        ) {
            Text("Wylosuj nowy ($label)")
        }
        Button(
            onClick = { showDialog.value = true },
            modifier = Modifier.weight(1f)
        ) {
            Text("Wpisz własny ($label)")
        }
    }

    if (showDialog.value) {
        CustomSlotDialog(
            label = label,
            onDismiss = { showDialog.value = false },
            onSave = { title, desc ->
                onCustom(title, desc)
                showDialog.value = false
            }
        )
    }
}

@Composable
private fun CustomSlotDialog(
    label: String,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit
) {
    val title = rememberSaveable { mutableStateOf("") }
    val desc = rememberSaveable { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Własny pomysł — $label") },
        text = {
            Column {
                OutlinedTextField(
                    value = title.value,
                    onValueChange = { title.value = it },
                    label = { Text("Tytuł") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = desc.value,
                    onValueChange = { desc.value = it },
                    label = { Text("Opis (opcjonalnie)") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3
                )
            }
        },
        confirmButton = {
            Button(onClick = { onSave(title.value, desc.value) }) {
                Text("Zapisz")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Anuluj")
            }
        }
    )
}
