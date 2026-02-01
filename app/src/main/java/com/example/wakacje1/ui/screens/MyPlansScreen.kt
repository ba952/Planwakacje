package com.example.wakacje1.ui.screens

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.wakacje1.R
import com.example.wakacje1.presentation.viewmodel.AuthViewModel
import com.example.wakacje1.presentation.viewmodel.MyPlansViewModel
import com.example.wakacje1.presentation.viewmodel.VacationViewModel
import java.util.Calendar

// Ograniczenie szerokości dla czytelności na dużych ekranach
private val MaxContentWidth = 520.dp

/**
 * Ekran wyświetlający listę zapisanych planów wakacyjnych użytkownika.
 * Obsługuje synchronizację z chmurą, wczytywanie lokalne oraz usuwanie planów.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyPlansScreen(
    authVm: AuthViewModel,
    plansVm: MyPlansViewModel,
    vacationVm: VacationViewModel,
    onNewPlan: () -> Unit,
    onOpenPlan: () -> Unit,
    onLoggedOut: () -> Unit
) {
    // Pobranie ID użytkownika z ViewModelu autoryzacji
    val uid = authVm.user?.uid

    // Uruchomienie nasłuchiwania repozytoriów przy zmianie użytkownika
    LaunchedEffect(uid) {
        plansVm.stop()
        if (uid != null) plansVm.start(uid)
    }

    // Zatrzymanie listenerów w momencie niszczenia ekranu (cleanup)
    DisposableEffect(Unit) {
        onDispose { plansVm.stop() }
    }

    // Destrukturyzacja stanów z ViewModelu planów
    val localUi = plansVm.localUi
    val cloudUi = plansVm.cloudUi
    val rows = plansVm.localPlans

    // Stan Snackbara do wyświetlania błędów synchronizacji i bazy danych
    val snack = remember { SnackbarHostState() }
    val cloudErrorPrefix = stringResource(R.string.error_cloud_prefix)

    // Obsługa błędów bazy lokalnej - wyświetlenie Snackbar i wyczyszczenie błędu w VM
    LaunchedEffect(localUi.error) {
        val msg = localUi.error ?: return@LaunchedEffect
        snack.showSnackbar(msg)
        plansVm.clearLocalError()
    }

    // Obsługa błędów synchronizacji z chmurą (np. brak sieci)
    LaunchedEffect(cloudUi.error) {
        val msg = cloudUi.error ?: return@LaunchedEffect
        snack.showSnackbar("$cloudErrorPrefix $msg")
        plansVm.clearCloudError()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.my_plans_title)) },
                actions = {
                    // Wylogowanie: zatrzymanie procesów i powrót do ekranu logowania
                    TextButton(
                        onClick = {
                            plansVm.stop()
                            authVm.signOut()
                            onLoggedOut()
                        }
                    ) { Text(stringResource(R.string.btn_logout)) }
                }
            )
        },
        snackbarHost = { SnackbarHost(hostState = snack) },
        floatingActionButton = {
            // Przycisk FAB uruchamiający kreator nowego planu
            ExtendedFloatingActionButton(onClick = onNewPlan) {
                Text(stringResource(R.string.btn_new_plan))
            }
        }
    ) { padding ->

        Centered(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            // Zarządzanie stanami pustymi i ładowania
            when {
                uid == null -> {
                    Text(
                        text = stringResource(R.string.error_no_user),
                        color = MaterialTheme.colorScheme.error
                    )
                    return@Centered
                }

                localUi.loading -> {
                    Text(stringResource(R.string.state_loading_plans))
                    return@Centered
                }
            }

            // Wyświetlenie zachęty, gdy użytkownik nie ma jeszcze żadnych planów
            if (rows.isEmpty()) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = stringResource(R.string.empty_plans_title),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(stringResource(R.string.empty_plans_body))
                }
                return@Centered
            }

            // Reaktywna lista zapisanych planów
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 92.dp) // Odstęp, aby FAB nie zasłaniał ostatniego elementu
            ) {
                itemsIndexed(rows, key = { _, row -> row.id }) { _, row ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                // Wczytanie pełnych danych planu i aplikacja do ViewModelu Vacation
                                val safeUid = uid ?: return@clickable
                                plansVm.loadLocal(safeUid, row.id) { stored ->
                                    vacationVm.applyStoredPlan(stored)
                                    onOpenPlan()
                                }
                            },
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(row.title, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.height(2.dp))

                                // Formatowanie zakresu dat wyjazdu
                                val s = row.startDateMillis?.let { formatDate(it) } ?: "—"
                                val e = row.endDateMillis?.let { formatDate(it) } ?: "—"
                                Text(
                                    text = stringResource(R.string.plan_date_range, s, e),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }

                            // Przycisk usuwania planu z bazy lokalnej i chmury
                            TextButton(
                                onClick = {
                                    val safeUid = uid ?: return@TextButton
                                    plansVm.deletePlan(safeUid, row.id)
                                }
                            ) { Text(stringResource(R.string.btn_delete)) }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Komponent pomocniczy centrujący treść w poziomie (do MaxContentWidth).
 */
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
        ) {
            content()
        }
    }
}

/**
 * Formatuje timestamp w formacie YYYY-MM-DD.
 */
private fun formatDate(millis: Long): String {
    val cal = Calendar.getInstance()
    cal.timeInMillis = millis
    val y = cal.get(Calendar.YEAR)
    val m = cal.get(Calendar.MONTH) + 1
    val d = cal.get(Calendar.DAY_OF_MONTH)
    return "%04d-%02d-%02d".format(y, m, d)
}