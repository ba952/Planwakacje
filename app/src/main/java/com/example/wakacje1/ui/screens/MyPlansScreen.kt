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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.wakacje1.presentation.viewmodel.AuthViewModel
import com.example.wakacje1.presentation.viewmodel.MyPlansViewModel
import com.example.wakacje1.presentation.viewmodel.VacationViewModel
import java.util.Calendar

private val MaxContentWidth = 520.dp

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
    val uid = authVm.user?.uid

    LaunchedEffect(uid) {
        plansVm.stop()
        if (uid != null) plansVm.start(uid)
    }


    DisposableEffect(Unit) {
        onDispose { plansVm.stop() }
    }

    val localUi = plansVm.localUi
    val cloudUi = plansVm.cloudUi
    val rows = plansVm.localPlans

    val snack = remember { SnackbarHostState() }

    // lokalne błędy -> snackbar
    LaunchedEffect(localUi.error) {
        val msg = localUi.error ?: return@LaunchedEffect
        snack.showSnackbar(msg)
        plansVm.clearLocalError()
    }

    // chmura/sync błędy -> snackbar
    LaunchedEffect(cloudUi.error) {
        val msg = cloudUi.error ?: return@LaunchedEffect
        snack.showSnackbar("Chmura: $msg")
        plansVm.clearCloudError()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Moje plany") },
                actions = {
                    TextButton(
                        onClick = {
                            plansVm.stop()
                            authVm.signOut()
                            onLoggedOut()
                        }
                    ) { Text("Wyloguj") }
                }
            )
        },
        snackbarHost = { SnackbarHost(hostState = snack) },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = onNewPlan) {
                Text("Nowy plan")
            }
        }
    ) { padding ->

        Centered(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            when {
                uid == null -> {
                    Text("Brak zalogowanego użytkownika.", color = MaterialTheme.colorScheme.error)
                    return@Centered
                }

                localUi.loading -> {
                    Text("Ładowanie planów…")
                    return@Centered
                }
            }

            if (rows.isEmpty()) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Nie masz jeszcze zapisanych planów.", style = MaterialTheme.typography.bodyLarge)
                    Text("Kliknij „Nowy plan”, aby wygenerować pierwszy.")
                }
                return@Centered
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 92.dp) // zapas pod FAB
            ) {
                itemsIndexed(rows, key = { _, row -> row.id }) { _, row ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
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
                                Text(
                                    text = formatRange(row.startDateMillis, row.endDateMillis),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }

                            TextButton(
                                onClick = {
                                    val safeUid = uid ?: return@TextButton
                                    plansVm.deletePlan(safeUid, row.id)
                                }
                            ) { Text("Usuń") }
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
        ) {
            content()
        }
    }
}

private fun formatRange(start: Long?, end: Long?): String {
    val s = start?.let { formatDate(it) } ?: "—"
    val e = end?.let { formatDate(it) } ?: "—"
    return "$s → $e"
}

private fun formatDate(millis: Long): String {
    val cal = Calendar.getInstance()
    cal.timeInMillis = millis
    val y = cal.get(Calendar.YEAR)
    val m = cal.get(Calendar.MONTH) + 1
    val d = cal.get(Calendar.DAY_OF_MONTH)
    return "%04d-%02d-%02d".format(y, m, d)
}
