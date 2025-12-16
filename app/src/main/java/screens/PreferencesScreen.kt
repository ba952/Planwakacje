package screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.wakacje1.data.model.Preferences
import com.example.wakacje1.ui.theme.VacationViewModel
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PreferencesScreen(
    viewModel: VacationViewModel,
    onNext: () -> Unit
) {
    val budgetText = rememberSaveable { mutableStateOf("") }

    val regionOptions = listOf("Europa - miasto", "Morze Śródziemne", "Góry", "Azja", "Ameryka")
    val climateOptions = listOf("Ciepły", "Umiarkowany", "Chłodny")
    val styleOptions = listOf("Relaks", "Zwiedzanie", "Aktywny", "Mix")

    val region = rememberSaveable { mutableStateOf(regionOptions.first()) }
    val climate = rememberSaveable { mutableStateOf(climateOptions[1]) }
    val style = rememberSaveable { mutableStateOf(styleOptions.first()) }

    val startDateMillis = rememberSaveable { mutableStateOf<Long?>(null) }
    val endDateMillis = rememberSaveable { mutableStateOf<Long?>(null) }

    val showStartPicker = remember { mutableStateOf(false) }
    val showEndPicker = remember { mutableStateOf(false) }

    val error = remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Preferencje podróży") }) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
            ) {
                Column(Modifier.padding(14.dp)) {
                    Text(
                        text = "Uzupełnij preferencje, a aplikacja zaproponuje 3 miejsca i wygeneruje plan dni.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            OutlinedTextField(
                value = budgetText.value,
                onValueChange = { budgetText.value = it.filter { ch -> ch.isDigit() } },
                label = { Text("Budżet całkowity (PLN)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )

            SimplePickRow(
                label = "Region",
                options = regionOptions,
                selected = region.value,
                onSelected = { region.value = it }
            )

            SimplePickRow(
                label = "Klimat",
                options = climateOptions,
                selected = climate.value,
                onSelected = { climate.value = it }
            )

            SimplePickRow(
                label = "Typ podróży",
                options = styleOptions,
                selected = style.value,
                onSelected = { style.value = it }
            )

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Zakres dat", style = MaterialTheme.typography.titleMedium)

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(
                            onClick = { showStartPicker.value = true },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Start")
                        }

                        OutlinedButton(
                            onClick = { showEndPicker.value = true },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Koniec")
                        }
                    }

                    Text("Start: ${startDateMillis.value?.let { formatDate(it) } ?: "—"}")
                    Text("Koniec: ${endDateMillis.value?.let { formatDate(it) } ?: "—"}")

                    val days = computeDays(startDateMillis.value, endDateMillis.value)
                    Text("Liczba dni: ${days ?: "—"}")
                }
            }

            error.value?.let { Text(it, color = MaterialTheme.colorScheme.error) }

            Spacer(Modifier.height(6.dp))

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
                        else -> error.value = null
                    }

                    viewModel.updatePreferences(
                        Preferences(
                            budget = budget,
                            days = days!!,
                            climate = climate.value,
                            region = region.value,
                            style = style.value,
                            startDateMillis = normalizeToLocalMidnight(start!!),
                            endDateMillis = normalizeToLocalMidnight(end!!)
                        )
                    )

                    // u Ciebie wcześniej było prepareDestinationSuggestions() w PreferencesScreen,
                    // ale teraz robimy to w NavGraph (po kliknięciu Dalej).
                    onNext()
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Dalej") }
        }
    }

    if (showStartPicker.value) {
        SingleDateDialog(
            title = "Wybierz datę startu",
            onDismiss = { showStartPicker.value = false },
            onPick = {
                startDateMillis.value = it
                val e = endDateMillis.value
                if (e != null && it > e) endDateMillis.value = null
                showStartPicker.value = false
            }
        )
    }

    if (showEndPicker.value) {
        SingleDateDialog(
            title = "Wybierz datę końca",
            onDismiss = { showEndPicker.value = false },
            onPick = {
                val s = startDateMillis.value
                if (s != null && it < s) {
                    error.value = "Data końca nie może być wcześniejsza niż start."
                } else {
                    endDateMillis.value = it
                    error.value = null
                }
                showEndPicker.value = false
            }
        )
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
            Button(onClick = {
                val v = state.selectedDateMillis
                if (v != null) onPick(v) else onDismiss()
            }) { Text("OK") }
        },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text("Anuluj") } }
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            DatePicker(state = state)
        }
    }
}

@Composable
private fun SimplePickRow(
    label: String,
    options: List<String>,
    selected: String,
    onSelected: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(label, style = MaterialTheme.typography.titleSmall)

            options.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { opt ->
                        val isSel = opt == selected
                        OutlinedButton(
                            onClick = { onSelected(opt) },
                            enabled = !isSel,
                            modifier = Modifier.weight(1f)
                        ) { Text(opt) }
                    }
                }
            }

            Text("Wybrano: $selected", style = MaterialTheme.typography.bodySmall)
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
