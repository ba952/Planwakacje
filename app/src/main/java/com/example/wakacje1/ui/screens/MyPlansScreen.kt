package com.example.wakacje1.ui.screens

import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.wakacje1.ui.viewmodel.AuthViewModel
import com.example.wakacje1.ui.viewmodel.MyPlansViewModel
import com.example.wakacje1.ui.viewmodel.VacationViewModel

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
        if (uid != null) plansVm.start(uid)
    }

    DisposableEffect(Unit) {
        onDispose { plansVm.stop() }
    }

    val ui = plansVm.ui
    val plans = plansVm.plans

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
        floatingActionButton = {
            FloatingActionButton(onClick = onNewPlan) {
                // Tekst w FAB (bez ikon)
                Text(
                    text = "Nowy",
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                )
            }
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            when {
                uid == null -> {
                    Text("Brak zalogowanego użytkownika.", color = MaterialTheme.colorScheme.error)
                    return@Column
                }

                ui.loading -> {
                    Text("Ładowanie planów…")
                    return@Column
                }

                ui.error != null -> {
                    Text("Błąd: ${ui.error}", color = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.height(10.dp))
                }
            }

            if (plans.isEmpty()) {
                Text("Nie masz jeszcze zapisanych planów.", style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(10.dp))
                Text("Kliknij „Nowy”, aby wygenerować pierwszy.")
                return@Column
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                itemsIndexed(plans, key = { _, row -> row.id }) { _, row ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val safeUid = uid ?: return@clickable
                                plansVm.load(safeUid, row.id) { stored ->
                                    vacationVm.applyStoredPlan(stored)
                                    onOpenPlan()
                                }
                            },
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(row.title, fontWeight = FontWeight.Bold)
                                if (row.subtitle.isNotBlank()) {
                                    Spacer(Modifier.height(2.dp))
                                    Text(row.subtitle, style = MaterialTheme.typography.bodySmall)
                                }
                            }

                            TextButton(
                                onClick = {
                                    val safeUid = uid ?: return@TextButton
                                    plansVm.delete(safeUid, row.id)
                                }
                            ) { Text("Usuń") }
                        }
                    }
                }
            }
        }
    }
}
