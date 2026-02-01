package com.example.wakacje1.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
 * Ekran rejestracji nowego użytkownika.
 * Wykorzystuje [AuthViewModel] do procesowania danych i zarządzania stanem ładowania/błędów.
 */
@Composable
fun RegisterScreen(
    authVm: AuthViewModel,
    onDone: () -> Unit,    // Nawigacja po sukcesie
    onGoLogin: () -> Unit  // Nawigacja powrotna
) {
    // Lokalny stan pól tekstowych. Wykorzystanie 'remember' zapewnia zachowanie
    // tekstu podczas rekompozycji (np. przy wyświetlaniu błędu).
    var email by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Tytuł ekranu zintegrowany z systemem zasobów i typografią Material3
            Text(
                text = stringResource(R.string.register_title),
                style = MaterialTheme.typography.headlineMedium
            )

            Spacer(Modifier.height(16.dp))

            // Pole Email z wymuszeniem odpowiedniego układu klawiatury
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text(stringResource(R.string.label_email)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(10.dp))

            // Pole Hasło z PasswordVisualTransformation (maskowanie kropek)
            OutlinedTextField(
                value = pass,
                onValueChange = { pass = it },
                label = { Text(stringResource(R.string.label_password)) },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            // Podpowiedź dot. wymagań hasła (np. min. 6 znaków, duża litera)
            Text(
                text = stringResource(R.string.password_requirements_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(top = 4.dp)
            )

            Spacer(Modifier.height(8.dp))

            // --- OBSŁUGA BŁĘDÓW (UiText) ---
            // Reaktywne wyświetlanie komunikatów o błędach pochodzących z ViewModelu.
            // Wykorzystanie .asString() pozwala na automatyczne tłumaczenie błędów z Firebase lub walidatora.
            authVm.error?.let { uiText ->
                Spacer(Modifier.height(6.dp))
                Text(
                    text = uiText.asString(),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Spacer(Modifier.height(14.dp))

            // Przycisk wyzwalający rejestrację. enabled = !authVm.loading zapobiega
            // wielokrotnym kliknięciom podczas trwania zapytania do serwera.
            Button(
                onClick = { authVm.register(email, pass, onSuccess = onDone) },
                enabled = !authVm.loading,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.btn_register_action))
            }

            Spacer(Modifier.height(18.dp))

            // Powrót do ekranu logowania dla użytkowników posiadających już konto
            TextButton(
                onClick = onGoLogin,
                enabled = !authVm.loading,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.btn_have_account_login))
            }
        }

        // Nakładka ładowania (Blocking UI)
        if (authVm.loading) {
            CircularProgressIndicator()
        }
    }
}