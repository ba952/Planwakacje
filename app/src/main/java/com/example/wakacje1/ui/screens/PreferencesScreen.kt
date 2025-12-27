package com.example.wakacje1.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.unit.dp
import com.example.wakacje1.data.model.Preferences
import com.example.wakacje1.ui.viewmodel.VacationViewModel
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PreferencesScreen(
    viewModel: VacationViewModel,
    onNext: () -> Unit
) {
    // --- opcje ---
    val regionOptions = listOf(
        "Europa - miasto",
        "Morze Śródziemne",
        "Góry",
        "Azja",
        "Ameryka"
    )
    val climateOptions = listOf("Ciepły", "Umiarkowany", "Chłodny")
    val styleOptions = listOf("Relaks", "Zwiedzanie", "Aktywny", "Mix")

    // --- stan ---
    val budgetText = rememberSaveable { mutableStateOf("") }

    val regionIndex = rememberSaveable { mutableIntStateOf(0) }
    val climateIndex = rememberSaveable { mutableIntStateOf(1) }
    val styleIndex = rememberSaveable { mutableIntStateOf(0) }

    val startDateMillis = rememberSaveable { mutableStateOf<Long?>(null) }
    val endDateMillis = rememberSaveable { mutableStateOf<Long?>(null) }

    val showStartPicker = remember { mutableStateOf(false) }
    val showEndPicker = remember { mutableStateOf(false) }

    val error = remember { mutableStateOf<String?>(null) }

    val computedDays = computeDays(startDateMillis.value, endDateMillis.value)

    Scaffold(
        topBar = { TopAppBar(title = { Text("Preferencje podróży") }) },
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                error.value?.let {
                    Text(it, color = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.height(8.dp))
                }

                Button(
                    onClick = {
                        val budget = budgetText.value.toIntOrNull()
                        val start = startDateMillis.value
                        val end = endDateMillis.value
                        val days = computeDays(start, end)

                        when {
                            budget == null || budget <= 0 -> {
                                error.value = "Podaj poprawny budżet (liczba > 0)."
                                return@Button
                            }
                            start == null || end == null -> {
                                error.value = "Wybierz datę startu i końca."
                                return@Button
                            }
                            days == null || days < 1 -> {
                                error.value = "Niepoprawny zakres dat."
                                return@Button
                            }
                            days > 21 -> {
                                error.value = "Za długi wyjazd jak na demo (max 21 dni)."
                                return@Button
                            }
                            else -> error.value = null
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
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Dalej")
                }
            }
        }
    ) { padding ->

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Text(
                            text = "Ustaw parametry, a aplikacja zaproponuje 3 miejsca i wygeneruje plan dzień po dniu.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            item {
                SectionCard(title = "Budżet") {
                    OutlinedTextField(
                        value = budgetText.value,
                        onValueChange = { budgetText.value = it.filter(Char::isDigit) },
                        label = { Text("Budżet całkowity (PLN)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "Wpisuj tylko cyfry. Aplikacja i tak przeliczy budżet na dzień.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            item {
                SectionCard(title = "Preferencje") {
                    SimpleDropdownField(
                        label = "Region",
                        value = regionOptions[regionIndex.intValue],
                        options = regionOptions,
                        onSelect = { regionIndex.intValue = it }
                    )
                    Spacer(Modifier.height(10.dp))
                    SimpleDropdownField(
                        label = "Klimat",
                        value = climateOptions[climateIndex.intValue],
                        options = climateOptions,
                        onSelect = { climateIndex.intValue = it }
                    )
                    Spacer(Modifier.height(10.dp))
                    SimpleDropdownField(
                        label = "Typ podróży",
                        value = styleOptions[styleIndex.intValue],
                        options = styleOptions,
                        onSelect = { styleIndex.intValue = it }
                    )
                }
            }

            item {
                SectionCard(title = "Daty wyjazdu") {
                    OutlinedButton(
                        onClick = { showStartPicker.value = true },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Wybierz datę startu") }

                    Spacer(Modifier.height(8.dp))

                    OutlinedButton(
                        onClick = { showEndPicker.value = true },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Wybierz datę końca") }

                    Spacer(Modifier.height(10.dp))
                    Divider()
                    Spacer(Modifier.height(10.dp))

                    Text("Start: ${startDateMillis.value?.let { formatDate(it) } ?: "—"}")
                    Text("Koniec: ${endDateMillis.value?.let { formatDate(it) } ?: "—"}")
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "Liczba dni: ${computedDays ?: "—"}",
                        style = MaterialTheme.typography.titleSmall
                    )
                }
            }

            // zapas pod bottomBar, żeby dało się przewinąć do końca
            item { Spacer(Modifier.height(110.dp)) }
        }
    }

    if (showStartPicker.value) {
        SingleDateDialog(
            title = "Wybierz datę startu",
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
        SingleDateDialog(
            title = "Wybierz datę końca",
            onDismiss = { showEndPicker.value = false },
            onPick = { picked ->
                val s = startDateMillis.value
                if (s != null && picked < s) {
                    error.value = "Data końca nie może być wcześniejsza niż start."
                } else {
                    endDateMillis.value = picked
                    error.value = null
                }
                showEndPicker.value = false
            }
        )
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

/**
 * Dropdown bez ExposedDropdownMenu i bez ikon:
 * - OutlinedTextField readOnly
 * - klik w pole otwiera DropdownMenu
 */
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
            ) { Text("OK") }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) { Text("Anuluj") }
        }
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            DatePicker(state = state)
        }
    }
}

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