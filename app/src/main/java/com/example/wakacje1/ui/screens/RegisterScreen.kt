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
import androidx.compose.material3.Scaffold // 1. Dodany import
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import com.example.wakacje1.presentation.viewmodel.AuthEvent
import com.example.wakacje1.presentation.viewmodel.AuthViewModel

@Composable
fun RegisterScreen(
    authVm: AuthViewModel,
    onDone: () -> Unit,
    onGoLogin: () -> Unit
) {
    val state by authVm.state.collectAsState()

    var email by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        authVm.events.collect { e ->
            if (e is AuthEvent.NavigateAfterAuth) onDone()
        }
    }

    // 2. Używamy Scaffold jako głównego kontenera.
    // Scaffold automatycznie ustawia kolor tła na MaterialTheme.colorScheme.background
    Scaffold(
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->

        // Box jest teraz wewnątrz Scaffolda, żeby wycentrować treść
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding), // Ważne: uwzględniamy padding ze Scaffolda
            contentAlignment = Alignment.Center
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(R.string.register_title),
                    style = MaterialTheme.typography.headlineMedium
                )

                Spacer(Modifier.height(16.dp))

                OutlinedTextField(
                    value = email,
                    onValueChange = {
                        email = it
                        if (state.error != null || state.info != null) authVm.clearMessages()
                    },
                    label = { Text(stringResource(R.string.label_email)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(10.dp))

                OutlinedTextField(
                    value = pass,
                    onValueChange = {
                        pass = it
                        if (state.error != null || state.info != null) authVm.clearMessages()
                    },
                    label = { Text(stringResource(R.string.label_password)) },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    text = stringResource(R.string.password_requirements_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(top = 4.dp)
                )

                state.error?.let { uiText ->
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = uiText.asString(),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                state.info?.let { uiText ->
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = uiText.asString(),
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                Spacer(Modifier.height(14.dp))

                Button(
                    onClick = { authVm.register(email, pass) },
                    enabled = !state.loading,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.btn_register_action))
                }

                Spacer(Modifier.height(18.dp))

                TextButton(
                    onClick = onGoLogin,
                    enabled = !state.loading,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.btn_have_account_login))
                }
            }

            if (state.loading) {
                CircularProgressIndicator()
            }
        }
    }
}