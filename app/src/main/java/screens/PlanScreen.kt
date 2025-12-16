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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.wakacje1.data.model.DayPlan
import com.example.wakacje1.data.model.DaySlot
import com.example.wakacje1.ui.theme.VacationViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanScreen(
    viewModel: VacationViewModel,
    onBack: () -> Unit
) {
    val plan = viewModel.plan
    val dest = viewModel.chosenDestination
    val canEdit = viewModel.canEditPlan

    val info = rememberSaveable { mutableStateOf<String?>(null) }

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
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Wstecz")
                    }
                },
                actions = {
                    // zostawiamy odświeżanie (na przyszłość, jak dodasz prognozę/dopinki),
                    // ale bez pokazywania kafelka "teraz"
                    IconButton(onClick = {
                        val d = viewModel.chosenDestination
                        if (d != null && d.apiQuery.isNotBlank()) {
                            viewModel.loadForecastForTrip(force = true)
                        }
                    }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Odśwież prognozę")
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
            // --- Nagłówek miejsca (bez pogody "teraz") ---
            if (dest != null) {
                Text(
                    text = if (dest.country.isBlank()) dest.displayName else "${dest.displayName}, ${dest.country}",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                if (dest.region.isNotBlank() || dest.climate.isNotBlank()) {
                    Text(
                        text = "Region: ${dest.region} • Klimat: ${dest.climate}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // --- akcje lokalne / PDF (zostają) ---
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = { info.value = viewModel.savePlanLocally() },
                    modifier = Modifier.weight(1f)
                ) { Text("Zapisz") }

                OutlinedButton(
                    onClick = { info.value = viewModel.loadPlanLocally() },
                    modifier = Modifier.weight(1f)
                ) { Text("Wczytaj") }

                Button(
                    onClick = { info.value = viewModel.exportCurrentPlanToPdf() },
                    modifier = Modifier.weight(1f)
                ) { Text("PDF") }
            }

            info.value?.let {
                Spacer(Modifier.height(10.dp))
                Text(it, style = MaterialTheme.typography.bodyMedium)
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
                    DayCardCompact(
                        day = day,
                        index = index,
                        canEdit = canEdit,
                        canMoveUp = canEdit && index > 0,
                        canMoveDown = canEdit && index < plan.size - 1,
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
private fun DayCardCompact(
    day: DayPlan,
    index: Int,
    canEdit: Boolean,
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
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
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
            Text(text = day.details, style = MaterialTheme.typography.bodyMedium)

            Spacer(Modifier.height(10.dp))

            // Mniejsze, pojedyncze działanie dla dnia
            OutlinedButton(
                onClick = onRegenerateDay,
                enabled = canEdit,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Wygeneruj ten dzień ponownie")
            }

            Spacer(Modifier.height(10.dp))
            Divider()
            Spacer(Modifier.height(8.dp))

            Text(
                text = "Sloty (kompaktowo)",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(6.dp))

            SlotRowCompact(
                label = "Poranek",
                enabled = canEdit,
                onRoll = { onRollSlot(DaySlot.MORNING) },
                onCustom = { t, d -> onCustomSlot(DaySlot.MORNING, t, d) }
            )
            SlotRowCompact(
                label = "Południe",
                enabled = canEdit,
                onRoll = { onRollSlot(DaySlot.MIDDAY) },
                onCustom = { t, d -> onCustomSlot(DaySlot.MIDDAY, t, d) }
            )
            SlotRowCompact(
                label = "Wieczór",
                enabled = canEdit,
                onRoll = { onRollSlot(DaySlot.EVENING) },
                onCustom = { t, d -> onCustomSlot(DaySlot.EVENING, t, d) }
            )

            if (!canEdit) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Edycja wyłączona (plan wczytany z dysku).",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun SlotRowCompact(
    label: String,
    enabled: Boolean,
    onRoll: () -> Unit,
    onCustom: (String, String) -> Unit
) {
    val showDialog = rememberSaveable { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f)
        )

        // małe, ikonowe akcje (oszczędzają miejsce)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            IconButton(onClick = onRoll, enabled = enabled) {
                Icon(Icons.Filled.Refresh, contentDescription = "Losuj nowy")
            }
            IconButton(onClick = { showDialog.value = true }, enabled = enabled) {
                Icon(Icons.Filled.Edit, contentDescription = "Wpisz własny")
            }
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
            Button(onClick = { onSave(title.value, desc.value) }) { Text("Zapisz") }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) { Text("Anuluj") }
        }
    )
}
