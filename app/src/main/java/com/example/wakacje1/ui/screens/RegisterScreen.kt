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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.wakacje1.presentation.viewmodel.AuthViewModel

@Composable
fun RegisterScreen(
    authVm: AuthViewModel,
    onDone: () -> Unit,
    onBackToLogin: () -> Unit
) {
    var email by remember { mutableStateOf("") }
    var pass1 by remember { mutableStateOf("") }
    var pass2 by remember { mutableStateOf("") }
    var localError by remember { mutableStateOf<String?>(null) }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Rejestracja", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(16.dp))

            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Email") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = pass1,
                onValueChange = { pass1 = it },
                label = { Text("Hasło") },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = pass2,
                onValueChange = { pass2 = it },
                label = { Text("Powtórz hasło") },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(8.dp))

            localError?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
            }
            authVm.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
            }

            Spacer(Modifier.height(14.dp))

            Button(
                onClick = {
                    authVm.clearMessages()
                    localError = null

                    val e = email.trim()
                    when {
                        e.isBlank() -> localError = "Podaj email."
                        pass1.isBlank() -> localError = "Podaj hasło."
                        pass1 != pass2 -> localError = "Hasła nie są takie same."
                        else -> authVm.register(e, pass1, onSuccess = onDone)
                    }
                },
                enabled = !authVm.loading,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Utwórz konto")
            }

            Spacer(Modifier.height(10.dp))

            OutlinedButton(
                onClick = onBackToLogin,
                enabled = !authVm.loading,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Mam konto — zaloguj")
            }
        }

        if (authVm.loading) {
            CircularProgressIndicator()
        }
    }
}
