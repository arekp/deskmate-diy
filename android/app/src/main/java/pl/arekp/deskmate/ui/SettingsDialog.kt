package pl.arekp.deskmate.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import pl.arekp.deskmate.Settings
import pl.arekp.deskmate.ai.llm.LlmRouter

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsDialog(settings: Settings, onDismiss: () -> Unit) {
    var mode by remember { mutableStateOf(settings.routerMode) }
    var geminiKey by remember { mutableStateOf(settings.geminiKey) }
    var geminiModel by remember { mutableStateOf(settings.geminiModel) }
    var orKey by remember { mutableStateOf(settings.openRouterKey) }
    var orModel by remember { mutableStateOf(settings.openRouterModel) }
    var claudeKey by remember { mutableStateOf(settings.claudeKey) }
    var claudeModel by remember { mutableStateOf(settings.claudeModel) }
    var url by remember { mutableStateOf(settings.screenHelperUrl) }
    var token by remember { mutableStateOf(settings.screenHelperToken) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ustawienia DeskMate") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Section("Wybór modelu AI")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    LlmRouter.Mode.entries.forEach { m ->
                        FilterChip(selected = mode == m, onClick = { mode = m }, label = { Text(m.label) })
                    }
                }

                Section("Gemini – darmowy pakiet (aistudio.google.com)")
                SecretField("Klucz Gemini", geminiKey) { geminiKey = it }
                PlainField("Model Gemini", geminiModel) { geminiModel = it }

                Section("OpenRouter – darmowe modele (openrouter.ai)")
                SecretField("Klucz OpenRouter", orKey) { orKey = it }
                PlainField("Model OpenRouter", orModel) { orModel = it }

                Section("Claude – płatne API (console.anthropic.com)")
                SecretField("Klucz Claude API", claudeKey) { claudeKey = it }
                PlainField("Model Claude", claudeModel) { claudeModel = it }

                Section("Pomocnik ekranu PC (opcjonalnie)")
                PlainField("Adres, np. http://192.168.1.50:8765", url) { url = it }
                SecretField("Token", token) { token = it }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                settings.routerMode = mode
                settings.geminiKey = geminiKey
                settings.geminiModel = geminiModel
                settings.openRouterKey = orKey
                settings.openRouterModel = orModel
                settings.claudeKey = claudeKey
                settings.claudeModel = claudeModel
                settings.screenHelperUrl = url
                settings.screenHelperToken = token
                onDismiss()
            }) { Text("Zapisz") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Anuluj") } },
    )
}

@Composable
private fun Section(title: String) {
    Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
}

@Composable
private fun SecretField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value, onChange,
        label = { Text(label) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun PlainField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(value, onChange, label = { Text(label) }, singleLine = true, modifier = Modifier.fillMaxWidth())
}
