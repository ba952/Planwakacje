package com.example.wakacje1.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.wakacje1.data.model.DayPlan
import com.example.wakacje1.data.model.DaySlot
import com.example.wakacje1.ui.viewmodel.VacationViewModel
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanScreen(
    viewModel: VacationViewModel,
    uid: String?,
    onBack: () -> Unit,
    onGoMyPlans: () -> Unit
) {
    val plan = viewModel.plan
    val dest = viewModel.chosenDestination

    // domyślnie kompaktowy PODGLĄD
    val editMode = rememberSaveable { mutableStateOf(false) }

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
                    TextButton(onClick = onBack) { Text("Wstecz") }
                },
                actions = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Edycja",
                            style = MaterialTheme.typography.labelLarge
                        )
                        Spacer(Modifier.padding(horizontal = 4.dp))
                        Switch(
                            checked = editMode.value,
                            onCheckedChange = { editMode.value = it },
                            enabled = !viewModel.isBlockingUi
                        )
                        Spacer(Modifier.padding(horizontal = 6.dp))
                        TextButton(onClick = onGoMyPlans) { Text("Moje plany") }
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(14.dp)
            ) {
                if (dest != null) {
                    Text(
                        text = if (dest.country.isBlank()) dest.displayName else "${dest.displayName}, ${dest.country}",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }

                viewModel.forecastNotice?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(it, color = MaterialTheme.colorScheme.error)
                }

                viewModel.uiMessage?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it)
                }

                Spacer(Modifier.height(10.dp))

                // akcje globalne (zostają, ale wciąż dość kompaktowe)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = { viewModel.savePlanLocally() },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                        enabled = !viewModel.isBlockingUi
                    ) { Text("Zapisz") }

                    OutlinedButton(
                        onClick = { viewModel.loadPlanLocally() },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                        enabled = !viewModel.isBlockingUi
                    ) { Text("Wczytaj") }

                    Button(
                        onClick = { viewModel.exportCurrentPlanToPdf() },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                        enabled = !viewModel.isBlockingUi
                    ) { Text("PDF") }
                }

                Spacer(Modifier.height(10.dp))

                if (plan.isEmpty()) {
                    Text(
                        text = "Brak planu. Wróć i wybierz miejsce albo wygeneruj ponownie.",
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = { viewModel.generatePlan() },
                        enabled = !viewModel.isBlockingUi
                    ) { Text("Wygeneruj plan") }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        itemsIndexed(plan) { index, day ->
                            DayCardCompact(
                                viewModel = viewModel,
                                day = day,
                                dayIndex = index,
                                editMode = editMode.value,
                                enabled = !viewModel.isBlockingUi && viewModel.canEditPlan && editMode.value,
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

            // overlay: blokada klikania + spinner
            if (viewModel.isBlockingUi) {
                val interaction = remember { MutableInteractionSource() }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.35f))
                        .clickable(
                            interactionSource = interaction,
                            indication = null
                        ) { },
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CircularProgressIndicator()
                            Text("Przetwarzanie…")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCardCompact(
    viewModel: VacationViewModel,
    day: DayPlan,
    dayIndex: Int,
    editMode: Boolean,
    enabled: Boolean,
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
        Column(Modifier.padding(12.dp)) {

            // nagłówek dnia
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "Dzień ${day.day}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                // przyciski przesuwania tylko w trybie edycji
                if (editMode) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedButton(
                            onClick = onMoveUp,
                            enabled = enabled,
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                        ) { Text("↑") }

                        OutlinedButton(
                            onClick = onMoveDown,
                            enabled = enabled,
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                        ) { Text("↓") }
                    }
                }
            }

            // prognoza dzienna
            val w = viewModel.getDayWeatherForIndex(dayIndex)
            if (w != null && (!w.description.isNullOrBlank() || w.tempMin != null || w.tempMax != null)) {
                Spacer(Modifier.height(6.dp))
                val temps = if (w.tempMin != null && w.tempMax != null) {
                    " (${w.tempMin.roundToInt()}°C – ${w.tempMax.roundToInt()}°C)"
                } else ""
                val note = if (w.isBadWeather) " • słaba → indoor" else ""
                Text(
                    text = "Prognoza: ${w.description.orEmpty()}$temps$note",
                    style = MaterialTheme.typography.bodySmall
                )
            }

            // regeneracja dnia tylko w trybie edycji
            if (editMode) {
                Spacer(Modifier.height(10.dp))
                OutlinedButton(
                    onClick = onRegenerateDay,
                    enabled = enabled,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
                ) { Text("Wygeneruj dzień ponownie") }
            }

            Spacer(Modifier.height(10.dp))

            // sloty – kompaktowo, bez dodatkowych kart w kartach
            Text("Sloty:", fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))

            SlotRow(
                label = "Poranek",
                slot = DaySlot.MORNING,
                dayIndex = dayIndex,
                viewModel = viewModel,
                editMode = editMode,
                enabled = enabled,
                onRoll = { onRollSlot(DaySlot.MORNING) },
                onCustom = { t, d -> onCustomSlot(DaySlot.MORNING, t, d) }
            )

            Spacer(Modifier.height(8.dp))

            SlotRow(
                label = "Południe",
                slot = DaySlot.MIDDAY,
                dayIndex = dayIndex,
                viewModel = viewModel,
                editMode = editMode,
                enabled = enabled,
                onRoll = { onRollSlot(DaySlot.MIDDAY) },
                onCustom = { t, d -> onCustomSlot(DaySlot.MIDDAY, t, d) }
            )

            Spacer(Modifier.height(8.dp))

            SlotRow(
                label = "Wieczór",
                slot = DaySlot.EVENING,
                dayIndex = dayIndex,
                viewModel = viewModel,
                editMode = editMode,
                enabled = enabled,
                onRoll = { onRollSlot(DaySlot.EVENING) },
                onCustom = { t, d -> onCustomSlot(DaySlot.EVENING, t, d) }
            )
        }
    }
}

@Composable
private fun SlotRow(
    label: String,
    slot: DaySlot,
    dayIndex: Int,
    viewModel: VacationViewModel,
    editMode: Boolean,
    enabled: Boolean,
    onRoll: () -> Unit,
    onCustom: (String, String) -> Unit
) {
    val s = viewModel.getSlotOrNull(dayIndex, slot)
    val title = s?.title?.takeIf { it.isNotBlank() } ?: "—"
    val desc = s?.description?.takeIf { it.isNotBlank() } ?: ""

    val expanded = rememberSaveable(dayIndex, "desc_${slot.name}") { mutableStateOf(false) }
    val showDialog = rememberSaveable(dayIndex, "dlg_${slot.name}") { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = desc.isNotBlank() && !editMode) {
                expanded.value = !expanded.value
            }
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        // PODGLĄD: opis dopiero po rozwinięciu
        if (!editMode && desc.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = if (expanded.value) "Zwiń opis" else "Rozwiń opis",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable { expanded.value = !expanded.value }
            )

            if (expanded.value) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = desc,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        // EDYCJA: przyciski dopiero w trybie edycji
        if (editMode) {
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onRoll,
                    enabled = enabled,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
                ) { Text("Losuj") }

                Button(
                    onClick = { showDialog.value = true },
                    enabled = enabled,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
                ) { Text("Własne") }
            }
        }
    }

    if (showDialog.value) {
        CustomSlotDialog(
            label = label,
            onDismiss = { showDialog.value = false },
            onSave = { t, d ->
                onCustom(t, d)
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
            Button(
                onClick = { onSave(title.value, desc.value) },
                enabled = title.value.isNotBlank()
            ) { Text("Zapisz") }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) { Text("Anuluj") }
        }
    )
}
