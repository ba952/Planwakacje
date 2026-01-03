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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.unit.dp
import com.example.wakacje1.domain.model.DayPlan
import com.example.wakacje1.domain.model.DaySlot
import com.example.wakacje1.presentation.common.UiEvent
import com.example.wakacje1.presentation.viewmodel.VacationViewModel
import kotlin.math.roundToInt

private val MaxContentWidth = 520.dp

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

    // Snackbar / Events
    val snack = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.events.collect { ev ->
            when (ev) {
                is UiEvent.Message -> snack.showSnackbar(ev.text)
                is UiEvent.Error -> snack.showSnackbar(ev.error.userMessage)
            }
        }
    }

    // żeby nie odpalać generowania 2x przy wejściu
    val didInit = rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(dest?.displayName) {
        if (!didInit.value) {
            didInit.value = true
            if (plan.isEmpty() && dest != null && !viewModel.isBlockingUi) {
                viewModel.generatePlan()
            }
        }
    }

    val editMode = rememberSaveable { mutableStateOf(false) }
    val editEnabled = viewModel.canEditPlan && !viewModel.isBlockingUi

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Plan wyjazdu") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Wstecz") } },
                actions = { TextButton(onClick = onGoMyPlans) { Text("Moje plany") } }
            )
        },
        snackbarHost = { SnackbarHost(hostState = snack) }
    ) { padding ->

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {

            Centered(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .imePadding(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (dest != null) {
                        Text(
                            text = if (dest.country.isBlank()) dest.displayName else "${dest.displayName}, ${dest.country}",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    viewModel.forecastNotice?.let {
                        Text(it, color = MaterialTheme.colorScheme.error)
                    }

                    // Panel góra: przełącznik edycji + szybkie akcje
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text("Tryb edycji", fontWeight = FontWeight.SemiBold)
                                    Text(
                                        text = if (editMode.value) "Możesz losować i edytować sloty." else "Widok tylko do podglądu.",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                                Switch(
                                    checked = editMode.value,
                                    onCheckedChange = { editMode.value = it },
                                    enabled = editEnabled
                                )
                            }

                            Spacer(Modifier.height(10.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { viewModel.savePlanLocally(uid) },
                                    modifier = Modifier.weight(1f),
                                    enabled = !viewModel.isBlockingUi,
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
                                ) { Text("Zapisz") }

                                Button(
                                    onClick = { viewModel.exportCurrentPlanToPdf() },
                                    modifier = Modifier.weight(1f),
                                    enabled = !viewModel.isBlockingUi,
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
                                ) { Text("PDF") }

                                OutlinedButton(
                                    onClick = { viewModel.loadPlanLocally(uid) },
                                    modifier = Modifier.weight(1f),
                                    enabled = !viewModel.isBlockingUi,
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
                                ) { Text("Wczytaj") }
                            }
                        }
                    }

                    if (plan.isEmpty()) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                        ) {
                            Column(Modifier.padding(12.dp)) {
                                Text(
                                    text = "Brak planu. Jeśli dopiero wybrałeś miejsce, poczekaj chwilę albo wygeneruj ponownie.",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Spacer(Modifier.height(10.dp))
                                OutlinedButton(
                                    onClick = { viewModel.generatePlan() },
                                    enabled = !viewModel.isBlockingUi
                                ) { Text("Wygeneruj plan") }
                            }
                        }
                        return@Centered
                    }

                    Divider()

                    // Lista dni
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(bottom = 24.dp)
                    ) {
                        itemsIndexed(plan) { index, day ->
                            DayCard(
                                viewModel = viewModel,
                                day = day,
                                dayIndex = index,
                                editMode = editMode.value,
                                editEnabled = editEnabled,
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

            if (viewModel.isBlockingUi) {
                val interaction = remember { MutableInteractionSource() }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.35f))
                        .clickable(interactionSource = interaction, indication = null) { },
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

@Composable
private fun DayCard(
    viewModel: VacationViewModel,
    day: DayPlan,
    dayIndex: Int,
    editMode: Boolean,
    editEnabled: Boolean,
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
                    Text("Dzień ${day.day}", fontWeight = FontWeight.SemiBold)
                    Text(
                        text = day.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (editMode) {
                    Column(horizontalAlignment = Alignment.End) {
                        TextButton(onClick = onMoveUp, enabled = editEnabled) { Text("W górę") }
                        TextButton(onClick = onMoveDown, enabled = editEnabled) { Text("W dół") }
                    }
                }
            }

            val w = viewModel.getDayWeatherForIndex(dayIndex)
            if (w != null && (!w.description.isNullOrBlank() || w.tempMin != null || w.tempMax != null)) {
                Spacer(Modifier.height(8.dp))
                val temps = if (w.tempMin != null && w.tempMax != null) {
                    " (${w.tempMin.roundToInt()}°C – ${w.tempMax.roundToInt()}°C)"
                } else ""
                val note = if (w.isBadWeather) " • słaba → indoor" else ""
                Text(
                    text = "Prognoza: ${w.description.orEmpty()}$temps$note",
                    style = MaterialTheme.typography.bodySmall
                )
            }

            if (editMode) {
                Spacer(Modifier.height(10.dp))
                OutlinedButton(
                    onClick = onRegenerateDay,
                    enabled = editEnabled,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
                ) { Text("Losuj cały dzień") }
            }

            Spacer(Modifier.height(8.dp))

            SlotSection(
                label = "Poranek",
                slot = DaySlot.MORNING,
                dayIndex = dayIndex,
                viewModel = viewModel,
                editMode = editMode,
                enabled = editEnabled,
                onRoll = { onRollSlot(DaySlot.MORNING) },
                onCustom = { t, d -> onCustomSlot(DaySlot.MORNING, t, d) }
            )

            Spacer(Modifier.height(8.dp))

            SlotSection(
                label = "Południe",
                slot = DaySlot.MIDDAY,
                dayIndex = dayIndex,
                viewModel = viewModel,
                editMode = editMode,
                enabled = editEnabled,
                onRoll = { onRollSlot(DaySlot.MIDDAY) },
                onCustom = { t, d -> onCustomSlot(DaySlot.MIDDAY, t, d) }
            )

            Spacer(Modifier.height(8.dp))

            SlotSection(
                label = "Wieczór",
                slot = DaySlot.EVENING,
                dayIndex = dayIndex,
                viewModel = viewModel,
                editMode = editMode,
                enabled = editEnabled,
                onRoll = { onRollSlot(DaySlot.EVENING) },
                onCustom = { t, d -> onCustomSlot(DaySlot.EVENING, t, d) }
            )
        }
    }
}

@Composable
private fun SlotSection(
    label: String,
    slot: DaySlot,
    dayIndex: Int,
    viewModel: VacationViewModel,
    editMode: Boolean,
    enabled: Boolean,
    onRoll: () -> Unit,
    onCustom: (String, String) -> Unit
) {
    val showDialog = rememberSaveable(dayIndex, slot.name) { mutableStateOf(false) }
    val descExpanded = rememberSaveable(dayIndex, "desc_${slot.name}") { mutableStateOf(false) }

    val s = viewModel.getSlotOrNull(dayIndex, slot)
    val title = s?.title?.takeIf { it.isNotBlank() } ?: "—"
    val desc = s?.description?.takeIf { it.isNotBlank() } ?: ""

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(Modifier.padding(6.dp)) {
            Text(label, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            Text(title, fontWeight = FontWeight.Bold)

            if (desc.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                ExpandableText(
                    text = desc,
                    expanded = descExpanded.value,
                    onToggle = { descExpanded.value = !descExpanded.value }
                )
            }

            if (editMode) {
                Spacer(Modifier.height(6.dp))
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
                Spacer(Modifier.height(6.dp))
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

@Composable
private fun ExpandableText(
    text: String,
    expanded: Boolean,
    onToggle: () -> Unit
) {
    if (text.isBlank()) return

    if (!expanded) {
        Text(
            text = "Rozwiń",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.clickable { onToggle() }
        )
        return
    }

    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium
    )

    Spacer(Modifier.height(6.dp))

    Text(
        text = "Zwiń",
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.clickable { onToggle() }
    )
}
