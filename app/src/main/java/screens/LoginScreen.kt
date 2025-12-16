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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

@Composable
fun LoginScreen(onLoggedIn: () -> Unit) {
    var email by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Zaloguj się", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(16.dp))

            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Email") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = pass,
                onValueChange = { pass = it },
                label = { Text("Hasło") },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password
                ),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))

            Button(
                onClick = {
                    loading = true
                    onLoggedIn()   // na razie mock
                    loading = false
                },
                enabled = !loading,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (loading) "Przetwarzanie..." else "Zaloguj / Zarejestruj")
            }
        }
    }
}
