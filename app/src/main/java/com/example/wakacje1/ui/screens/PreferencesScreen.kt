package com.example.wakacje1.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import com.example.wakacje1.R
import com.example.wakacje1.domain.model.Preferences
import com.example.wakacje1.presentation.viewmodel.VacationViewModel
import java.util.Calendar

private val MaxContentWidth = 520.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PreferencesScreen(
    viewModel: VacationViewModel,
    onNext: () -> Unit,
    onGoMyPlans: () -> Unit
) {
    val context = LocalContext.current

    // --- Opcje (pobierane z zasobów) ---
    val regionOptions = listOf(
        stringResource(R.string.region_europe_city),
        stringResource(R.string.region_mediterranean),
        stringResource(R.string.region_mountains),
        stringResource(R.string.region_asia),
        stringResource(R.string.region_america)
    )
    val climateOptions = listOf(
        stringResource(R.string.climate_warm),
        stringResource(R.string.climate_moderate),
        stringResource(R.string.climate_cool)
    )
    val styleOptions = listOf(
        stringResource(R.string.style_relax),
        stringResource(R.string.style_sightseeing),
        stringResource(R.string.style_active),
        stringResource(R.string.style_mix)
    )

    // --- stan ---
    val budgetText = rememberSaveable { mutableStateOf("") }

    val regionIndex = rememberSaveable { mutableIntStateOf(0) }
    val climateIndex = rememberSaveable { mutableIntStateOf(1) }
    val styleIndex = rememberSaveable { mutableIntStateOf(0) }

    val startDateMillis = rememberSaveable { mutableStateOf<Long?>(null) }
    val endDateMillis = rememberSaveable { mutableStateOf<Long?>(null) }

    val showStartPicker = remember { mutableStateOf(false) }
    val showEndPicker = remember { mutableStateOf(false) }

    // Snackbar errors
    val snack = remember { SnackbarHostState() }
    val localError = remember { mutableStateOf<String?>(null) }

    LaunchedEffect(localError.value) {
        val msg = localError.value ?: return@LaunchedEffect
        snack.showSnackbar(msg)
        localError.value = null
    }

    val computedDays = computeDays(startDateMillis.value, endDateMillis.value)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.prefs_screen_title)) },
                actions = {
                    TextButton(onClick = onGoMyPlans) { Text(stringResource(R.string.btn_my_plans)) }
                }
            )
        },
        snackbarHost = { SnackbarHost(hostState = snack) }
    ) { padding ->

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(
                horizontal = 16.dp,
                vertical = 12.dp
            )
        ) {

            item {
                Centered {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Text(
                                text = stringResource(R.string.prefs_intro_text),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }

            item {
                Centered {
                    SectionCard(title = stringResource(R.string.section_budget)) {
                        OutlinedTextField(
                            value = budgetText.value,
                            onValueChange = { budgetText.value = it.filter(Char::isDigit) },
                            label = { Text(stringResource(R.string.label_total_budget)) },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = stringResource(R.string.hint_budget_digits),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            item {
                Centered {
                    SectionCard(title = stringResource(R.string.section_preferences)) {
                        SimpleDropdownField(
                            label = stringResource(R.string.label_region),
                            value = regionOptions[regionIndex.intValue],
                            options = regionOptions,
                            onSelect = { regionIndex.intValue = it }
                        )
                        Spacer(Modifier.height(10.dp))
                        SimpleDropdownField(
                            label = stringResource(R.string.label_climate),
                            value = climateOptions[climateIndex.intValue],
                            options = climateOptions,
                            onSelect = { climateIndex.intValue = it }
                        )
                        Spacer(Modifier.height(10.dp))
                        SimpleDropdownField(
                            label = stringResource(R.string.label_travel_style),
                            value = styleOptions[styleIndex.intValue],
                            options = styleOptions,
                            onSelect = { styleIndex.intValue = it }
                        )
                    }
                }
            }

            item {
                Centered {
                    SectionCard(title = stringResource(R.string.section_dates)) {
                        OutlinedButton(
                            onClick = { showStartPicker.value = true },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(stringResource(R.string.btn_pick_start_date)) }

                        Spacer(Modifier.height(8.dp))

                        OutlinedButton(
                            onClick = { showEndPicker.value = true },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(stringResource(R.string.btn_pick_end_date)) }

                        Spacer(Modifier.height(10.dp))
                        Divider()
                        Spacer(Modifier.height(10.dp))

                        // Używamy placeholderów w stringach: "Start: %s"
                        val startStr = startDateMillis.value?.let { formatDate(it) } ?: "—"
                        Text(stringResource(R.string.label_start_date_colon, startStr))

                        val endStr = endDateMillis.value?.let { formatDate(it) } ?: "—"
                        Text(stringResource(R.string.label_end_date_colon, endStr))

                        Spacer(Modifier.height(6.dp))

                        val daysStr = computedDays?.toString() ?: "—"
                        Text(
                            text = stringResource(R.string.label_days_count_colon, daysStr),
                            style = MaterialTheme.typography.titleSmall
                        )
                    }
                }
            }

            // --- CTA na końcu ---
            item {
                Centered {
                    Column {
                        // Pobieramy stringi błędów do zmiennych, żeby użyć ich w bloku onClick
                        val errBudget = stringResource(R.string.error_invalid_budget)
                        val errDates = stringResource(R.string.error_dates_missing)
                        val errRange = stringResource(R.string.error_invalid_date_range)
                        val errTooLong = stringResource(R.string.error_too_long_trip)

                        Button(
                            onClick = {
                                val budget = budgetText.value.toIntOrNull()
                                val start = startDateMillis.value
                                val end = endDateMillis.value
                                val days = computeDays(start, end)

                                when {
                                    budget == null || budget <= 0 -> {
                                        localError.value = errBudget
                                        return@Button
                                    }
                                    start == null || end == null -> {
                                        localError.value = errDates
                                        return@Button
                                    }
                                    days == null || days < 1 -> {
                                        localError.value = errRange
                                        return@Button
                                    }
                                    days > 21 -> {
                                        localError.value = errTooLong
                                        return@Button
                                    }
                                }

                                viewModel.updatePreferences(
                                    Preferences(
                                        budget = budget,
                                        days = days,
                                        climate = climateOptions[climateIndex.intValue],
                                        region = regionOptions[regionIndex.intValue],
                                        style = styleOptions[styleIndex.intValue],
                                        startDateMillis = normalizeToLocalMidnight(start!!),
                                        endDateMillis = normalizeToLocalMidnight(end!!)
                                    )
                                )

                                onNext()
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                        ) { Text(stringResource(R.string.btn_next)) }

                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
        }
    }

    if (showStartPicker.value) {
        SingleDateDialog(
            title = stringResource(R.string.btn_pick_start_date),
            onDismiss = { showStartPicker.value = false },
            onPick = { picked ->
                startDateMillis.value = picked
                val e = endDateMillis.value
                if (e != null && picked > e) endDateMillis.value = null
                showStartPicker.value = false
            }
        )
    }

    if (showEndPicker.value) {
        // Pobieramy error do zmiennej, bo jesteśmy w Composable
        val errEndBeforeStart = stringResource(R.string.error_end_date_before_start)

        SingleDateDialog(
            title = stringResource(R.string.btn_pick_end_date),
            onDismiss = { showEndPicker.value = false },
            onPick = { picked ->
                val s = startDateMillis.value
                if (s != null && picked < s) {
                    localError.value = errEndBeforeStart
                } else {
                    endDateMillis.value = picked
                }
                showEndPicker.value = false
            }
        )
    }
}

@Composable
private fun Centered(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.TopCenter
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = MaxContentWidth)
        ) {
            content()
        }
    }
}

@Composable
private fun SectionCard(
    title: String,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}

@Composable
private fun SimpleDropdownField(
    label: String,
    value: String,
    options: List<String>,
    onSelect: (Int) -> Unit
) {
    val expanded = remember { mutableStateOf(false) }

    Column {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded.value = true }
        )

        DropdownMenu(
            expanded = expanded.value,
            onDismissRequest = { expanded.value = false },
            properties = PopupProperties(focusable = true)
        ) {
            options.forEachIndexed { idx, opt ->
                DropdownMenuItem(
                    text = { Text(opt) },
                    onClick = {
                        onSelect(idx)
                        expanded.value = false
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SingleDateDialog(
    title: String,
    onDismiss: () -> Unit,
    onPick: (Long) -> Unit
) {
    val state = rememberDatePickerState()

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(
                onClick = {
                    val v = state.selectedDateMillis
                    if (v != null) onPick(v) else onDismiss()
                }
            ) { Text(stringResource(R.string.btn_ok)) }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) { Text(stringResource(R.string.btn_cancel)) }
        }
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            DatePicker(state = state)
        }
    }
}

// Funkcje pomocnicze bez zmian
private fun computeDays(start: Long?, end: Long?): Int? {
    if (start == null || end == null) return null
    val s = normalizeToLocalMidnight(start)
    val e = normalizeToLocalMidnight(end)
    val diff = e - s
    if (diff < 0) return null
    val oneDay = 24L * 60 * 60 * 1000L
    return (diff / oneDay).toInt() + 1
}

private fun normalizeToLocalMidnight(millis: Long): Long {
    val cal = Calendar.getInstance()
    cal.timeInMillis = millis
    cal.set(Calendar.HOUR_OF_DAY, 0)
    cal.set(Calendar.MINUTE, 0)
    cal.set(Calendar.SECOND, 0)
    cal.set(Calendar.MILLISECOND, 0)
    return cal.timeInMillis
}

private fun formatDate(millis: Long): String {
    val cal = Calendar.getInstance()
    cal.timeInMillis = millis
    val y = cal.get(Calendar.YEAR)
    val m = cal.get(Calendar.MONTH) + 1
    val d = cal.get(Calendar.DAY_OF_MONTH)
    return "%04d-%02d-%02d".format(y, m, d)
}