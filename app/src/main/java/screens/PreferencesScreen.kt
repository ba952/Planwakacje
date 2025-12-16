package com.example.wakacje1.ui.screens

import android.app.DatePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.wakacje1.ui.Preferences
import com.example.wakacje1.ui.VacationViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PreferencesScreen(
    viewModel: VacationViewModel,
    onNext: () -> Unit
) {
    val context = LocalContext.current

    var budgetText by remember { mutableStateOf("2000") }

    var climate by remember { mutableStateOf("Ciepły") }
    var region by remember { mutableStateOf("Europa - miasto") }
    var style by remember { mutableStateOf("Zwiedzanie") }

    val climateOptions = listOf("Ciepły", "Umiarkowany", "Chłodny")
    val regionOptions = listOf("Europa - miasto", "Morze Śródziemne", "Góry")
    val styleOptions = listOf("Relaks", "Zwiedzanie", "Aktywny", "Mix")

    var errorText by remember { mutableStateOf<String?>(null) }

    var startDateMillis by remember { mutableStateOf<Long?>(null) }
    var endDateMillis by remember { mutableStateOf<Long?>(null) }

    val scrollState = rememberScrollState()

    fun formatDate(millis: Long?): String {
        if (millis == null) return "-"
        val sdf = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
        return sdf.format(Date(millis))
    }

    val daysCount: Int? = remember(startDateMillis, endDateMillis) {
        if (startDateMillis == null || endDateMillis == null) null
        else {
            val oneDay = 24L * 60 * 60 * 1000
            val diff = endDateMillis!! - startDateMillis!!
            if (diff < 0L) null else ((diff / oneDay) + 1L).toInt()
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Preferencje podróży") }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(innerPadding)
                .verticalScroll(scrollState)
                .padding(16.dp)
        ) {
            Text(
                "Ustal parametry wyjazdu",
                style = MaterialTheme.typography.titleMedium
            )

            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = budgetText,
                onValueChange = { budgetText = it },
                label = { Text("Budżet całkowity (PLN)") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(12.dp))

            // Wybór terminu wyjazdu
            Text(
                text = "Termin wyjazdu",
                style = MaterialTheme.typography.titleSmall
            )
            Spacer(Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        val cal = Calendar.getInstance()
                        val dialog = DatePickerDialog(
                            context,
                            { _, year, month, dayOfMonth ->
                                val c = Calendar.getInstance()
                                c.set(year, month, dayOfMonth, 0, 0, 0)
                                c.set(Calendar.MILLISECOND, 0)
                                startDateMillis = c.timeInMillis
                                // jeśli koniec jest przed początkiem – wyzeruj
                                if (endDateMillis != null && endDateMillis!! < startDateMillis!!) {
                                    endDateMillis = null
                                }
                            },
                            cal.get(Calendar.YEAR),
                            cal.get(Calendar.MONTH),
                            cal.get(Calendar.DAY_OF_MONTH)
                        )
                        dialog.show()
                    }
                ) {
                    Text("Data rozpoczęcia")
                }

                Button(
                    onClick = {
                        val cal = Calendar.getInstance()
                        if (startDateMillis != null) {
                            cal.timeInMillis = startDateMillis!!
                        }
                        val dialog = DatePickerDialog(
                            context,
                            { _, year, month, dayOfMonth ->
                                val c = Calendar.getInstance()
                                c.set(year, month, dayOfMonth, 0, 0, 0)
                                c.set(Calendar.MILLISECOND, 0)
                                endDateMillis = c.timeInMillis
                            },
                            cal.get(Calendar.YEAR),
                            cal.get(Calendar.MONTH),
                            cal.get(Calendar.DAY_OF_MONTH)
                        )
                        // opcjonalnie: wymuś minDate = start
                        if (startDateMillis != null) {
                            dialog.datePicker.minDate = startDateMillis!!
                        }
                        dialog.show()
                    }
                ) {
                    Text("Data zakończenia")
                }
            }

            Spacer(Modifier.height(6.dp))

            Text(
                text = "Wybrany zakres: ${formatDate(startDateMillis)} – ${formatDate(endDateMillis)}" +
                        (if (daysCount != null) "  (${daysCount} dni)" else ""),
                style = MaterialTheme.typography.bodySmall
            )

            Spacer(Modifier.height(12.dp))

            if (errorText != null) {
                Text(
                    text = errorText!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(8.dp))
            }

            SimpleDropdownField(
                label = "Preferowany klimat",
                value = climate,
                options = climateOptions,
                onValueChange = { climate = it },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))

            SimpleDropdownField(
                label = "Region",
                value = region,
                options = regionOptions,
                onValueChange = { region = it },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))

            SimpleDropdownField(
                label = "Styl podróży",
                value = style,
                options = styleOptions,
                onValueChange = { style = it },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(20.dp))

            Button(
                onClick = {
                    val budget = budgetText.toIntOrNull() ?: 0

                    if (startDateMillis == null || endDateMillis == null) {
                        errorText = "Wybierz datę rozpoczęcia i zakończenia wyjazdu."
                        return@Button
                    }

                    if (endDateMillis!! < startDateMillis!!) {
                        errorText = "Data zakończenia nie może być wcześniejsza niż data rozpoczęcia."
                        return@Button
                    }

                    val days = daysCount ?: 0
                    if (days <= 0) {
                        errorText = "Zakres dni jest nieprawidłowy."
                        return@Button
                    }

                    val budgetPerDay = budget.toDouble() / days.toDouble()
                    val MIN_BUDGET_PER_DAY_GLOBAL = 150.0

                    if (budgetPerDay < MIN_BUDGET_PER_DAY_GLOBAL) {
                        errorText =
                            "Podany budżet jest zbyt niski (${budgetPerDay.toInt()} zł/dzień) dla wyjazdu. " +
                                    "Zwiększ budżet lub skróć czas pobytu."
                        return@Button
                    }

                    errorText = null

                    val prefs = Preferences(
                        budget = budget,
                        days = days,
                        climate = climate,
                        region = region,
                        style = style,
                        startDateMillis = startDateMillis,
                        endDateMillis = endDateMillis
                    )
                    viewModel.updatePreferences(prefs)
                    viewModel.prepareDestinationSuggestions()
                    onNext()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Pokaż propozycje miejsc")
            }
        }
    }
}

@Composable
private fun SimpleDropdownField(
    label: String,
    value: String,
    options: List<String>,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = true }
        )

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        onValueChange(option)
                        expanded = false
                    }
                )
            }
        }
    }
}
