package com.example.wakacje1.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold // <--- WAŻNY IMPORT
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.wakacje1.R
import com.example.wakacje1.presentation.viewmodel.AuthViewModel

/**
 * Główny ekran logowania użytkownika.
 * Wykorzystuje [AuthViewModel] do obsługi logiki uwierzytelniania i zarządzania stanem sesji.
 */
@Composable
fun LoginScreen(
    authVm: AuthViewModel,
    onDone: () -> Unit,      // Callback wywoływany po pomyślnym zalogowaniu
    onGoRegister: () -> Unit // Callback do nawigacji do ekranu rejestracji
) {
    // Stan lokalny dla pól tekstowych (Transient State)
    var email by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    val showReset = remember { mutableStateOf(false) }

    // Scaffold zapewnia podstawową strukturę wizualną Material Design,
    // w tym automatyczne zarządzanie tłem i odstępami (padding).
    Scaffold { paddingValues ->

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues), // Respektowanie bezpiecznych odstępów Scaffolda
            contentAlignment = Alignment.Center
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Nagłówek ekranu pobierany z zasobów stringów
                Text(stringResource(R.string.login_title), style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(16.dp))

                // Pole wprowadzania adresu e-mail
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text(stringResource(R.string.label_email)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))

                // Pole wprowadzania hasła z maskowaniem znaków
                OutlinedTextField(
                    value = pass,
                    onValueChange = { pass = it },
                    label = { Text(stringResource(R.string.label_password)) },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(8.dp))

                // --- OBSŁUGA BŁĘDÓW I INFORMACJI (UiText) ---
                // Reaktywne wyświetlanie błędów przekazywanych z ViewModelu jako UiText
                authVm.error?.let { uiText ->
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = uiText.asString(), // Konwersja obiektu UiText na String w kontekście Composable
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                // Wyświetlanie komunikatów informacyjnych (np. o wysłaniu linku resetującego)
                authVm.info?.let { uiText ->
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = uiText.asString(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(Modifier.height(14.dp))

                // Przycisk logowania - blokowany, gdy trwa proces komunikacji z API
                Button(
                    onClick = { authVm.signIn(email, pass, onSuccess = onDone) },
                    enabled = !authVm.loading,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.btn_login))
                }

                Spacer(Modifier.height(10.dp))

                // Interaktywny tekst wyzwalający okno dialogowe resetowania hasła
                Text(
                    text = stringResource(R.string.btn_forgot_password),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null // Wyłączenie efektu ripple dla czystego wyglądu linku
                        ) { showReset.value = true }
                        .padding(6.dp)
                )

                Spacer(Modifier.height(18.dp))

                // Przycisk nawigacji do ekranu tworzenia nowego konta
                OutlinedButton(
                    onClick = onGoRegister,
                    enabled = !authVm.loading,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.btn_go_to_register))
                }
            }

            // Wyświetlanie wskaźnika postępu podczas operacji asynchronicznych
            if (authVm.loading) {
                CircularProgressIndicator()
            }
        }
    }

    // Wyświetlanie modalnego okna resetu hasła (warunkowo)
    if (showReset.value) {
        ResetPasswordDialog(
            initialEmail = email,
            onDismiss = { showReset.value = false },
            onSend = { e ->
                authVm.sendPasswordReset(e)
                showReset.value = false
            }
        )
    }
}

/**
 * Komponent pomocniczy wyświetlający okno dialogowe do resetowania hasła.
 */
@Composable
private fun ResetPasswordDialog(
    initialEmail: String,
    onDismiss: () -> Unit,
    onSend: (String) -> Unit
) {
    var mail by remember { mutableStateOf(initialEmail) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_reset_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.dialog_reset_msg))
                OutlinedTextField(
                    value = mail,
                    onValueChange = { mail = it },
                    label = { Text(stringResource(R.string.label_email)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSend(mail) }) { Text(stringResource(R.string.btn_send)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.btn_cancel)) }
        }
    )
}