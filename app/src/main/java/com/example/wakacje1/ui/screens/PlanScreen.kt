package com.example.wakacje1.ui.screens

import android.app.Activity
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.wakacje1.R
import com.example.wakacje1.domain.model.DayPlan
import com.example.wakacje1.domain.model.DaySlot
import com.example.wakacje1.domain.usecase.WebViewPdfExporter
import com.example.wakacje1.presentation.common.UiEvent
import com.example.wakacje1.presentation.viewmodel.VacationViewModel
import kotlinx.coroutines.launch
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
    val context = LocalContext.current
    val activity = context as? Activity
    val scope = rememberCoroutineScope()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val plan = uiState.plan
    val dest = uiState.chosenDestination
    val isLoading = uiState.isLoading
    val canEdit = uiState.canEditPlan

    val snack = remember { SnackbarHostState() }


    val errorNoActivity = stringResource(R.string.error_no_activity)

    LaunchedEffect(Unit) {
        viewModel.events.collect { ev ->
            when (ev) {
                // Message ma uiText bezpośrednio
                is UiEvent.Message -> {
                    snack.showSnackbar(ev.uiText.asString(context))
                }
                // POPRAWKA TUTAJ:
                // Error ma w sobie obiekt 'error' (typu AppError),
                // a dopiero on ma 'uiText'.
                is UiEvent.Error -> {
                    snack.showSnackbar(ev.error.uiText.asString(context))
                }
                is UiEvent.ExportPdf -> {
                    if (activity != null) {
                        val msg = WebViewPdfExporter.export(
                            activity = activity,
                            destinationName = ev.destinationName,
                            tripStartDateMillis = ev.tripStartDateMillis,
                            plan = ev.plan
                        )
                        snack.showSnackbar(msg)
                    } else {
                        snack.showSnackbar(errorNoActivity)
                    }
                }
            }
        }
    }

    val didInit = rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(dest?.displayName) {
        if (!didInit.value) {
            didInit.value = true
            if (plan.isEmpty() && dest != null && !isLoading) {
                viewModel.generatePlan()
            }
        }
    }

    val editMode = rememberSaveable { mutableStateOf(false) }
    val editEnabled = canEdit && !isLoading

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.title_plan_screen)) },
                navigationIcon = { TextButton(onClick = onBack) { Text(stringResource(R.string.btn_back)) } },
                actions = { TextButton(onClick = onGoMyPlans) { Text(stringResource(R.string.btn_my_plans)) } }
            )
        },
        snackbarHost = { SnackbarHost(hostState = snack) }
    ) { padding ->

        Box(modifier = Modifier.fillMaxSize().padding(padding)) {

            Centered(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp)) {
                Column(
                    modifier = Modifier.fillMaxSize().imePadding(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (dest != null) {
                        Text(
                            text = if (dest.country.isBlank()) dest.displayName else "${dest.displayName}, ${dest.country}",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    uiState.forecastNotice?.let {
                        Text(it, color = MaterialTheme.colorScheme.error)
                    }

                    // Panel sterowania
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
                                    Text(stringResource(R.string.title_edit_mode), fontWeight = FontWeight.SemiBold)
                                    Text(
                                        text = if (editMode.value) stringResource(R.string.label_edit_mode_desc_on)
                                        else stringResource(R.string.label_edit_mode_desc_off),
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
                                    enabled = !isLoading,
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
                                ) { Text(stringResource(R.string.btn_save)) }

                                Button(
                                    onClick = {
                                        if (activity == null) {
                                            scope.launch { snack.showSnackbar(errorNoActivity) }
                                            return@Button
                                        }
                                        viewModel.exportCurrentPlanToPdf()
                                    },
                                    modifier = Modifier.weight(1f),
                                    enabled = activity != null && !isLoading,
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
                                ) { Text(stringResource(R.string.btn_pdf)) }

                                OutlinedButton(
                                    onClick = { viewModel.loadPlanLocally(uid) },
                                    modifier = Modifier.weight(1f),
                                    enabled = !isLoading,
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
                                ) { Text(stringResource(R.string.btn_load)) }
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
                                    text = stringResource(R.string.msg_no_plan),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Spacer(Modifier.height(10.dp))
                                OutlinedButton(
                                    onClick = { viewModel.generatePlan() },
                                    enabled = !isLoading
                                ) { Text(stringResource(R.string.btn_generate)) }
                            }
                        }
                        return@Centered
                    }

                    Divider()

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

            if (isLoading) {
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
                            Text(stringResource(R.string.msg_processing))
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
        Box(modifier = Modifier.fillMaxWidth().widthIn(max = MaxContentWidth)) { content() }
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
                    Text(stringResource(R.string.day_prefix, day.day), fontWeight = FontWeight.SemiBold)
                    Text(
                        text = day.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (editMode) {
                    Column(horizontalAlignment = Alignment.End) {
                        TextButton(onClick = onMoveUp, enabled = editEnabled) { Text(stringResource(R.string.btn_up)) }
                        TextButton(onClick = onMoveDown, enabled = editEnabled) { Text(stringResource(R.string.btn_down)) }
                    }
                }
            }

            val w = viewModel.getDayWeatherForIndexGetter(dayIndex)
            if (w != null && (!w.description.isNullOrBlank() || w.tempMin != null || w.tempMax != null)) {
                Spacer(Modifier.height(8.dp))
                val temps = if (w.tempMin != null && w.tempMax != null) {
                    " (${w.tempMin.roundToInt()}°C – ${w.tempMax.roundToInt()}°C)"
                } else ""
                val note = if (w.isBadWeather) stringResource(R.string.msg_bad_weather_note) else ""

                Text(
                    text = stringResource(R.string.msg_forecast_format, w.description.orEmpty(), temps, note),
                    style = MaterialTheme.typography.bodySmall
                )
            }

            if (editMode) {
                Spacer(Modifier.height(10.dp))
                OutlinedButton(
                    onClick = onRegenerateDay,
                    enabled = editEnabled,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
                ) { Text(stringResource(R.string.btn_roll_day)) }
            }

            Spacer(Modifier.height(8.dp))

            SlotSection(
                label = stringResource(R.string.slot_morning),
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
                label = stringResource(R.string.slot_midday),
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
                label = stringResource(R.string.slot_evening),
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
                    ) { Text(stringResource(R.string.btn_roll)) }

                    Button(
                        onClick = { showDialog.value = true },
                        enabled = enabled,
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
                    ) { Text(stringResource(R.string.btn_custom)) }
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
        title = { Text(stringResource(R.string.dialog_custom_title, label)) },
        text = {
            Column {
                OutlinedTextField(
                    value = title.value,
                    onValueChange = { title.value = it },
                    label = { Text(stringResource(R.string.label_title)) },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = desc.value,
                    onValueChange = { desc.value = it },
                    label = { Text(stringResource(R.string.label_description_optional)) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(title.value, desc.value) },
                enabled = title.value.isNotBlank()
            ) { Text(stringResource(R.string.btn_save)) }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) { Text(stringResource(R.string.btn_cancel)) }
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
            text = stringResource(R.string.btn_expand),
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
        text = stringResource(R.string.btn_collapse),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.clickable { onToggle() }
    )
}