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
import pl.arekp.deskmate.voice.SpeechOption

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsDialog(
    settings: Settings,
    sttOptions: List<SpeechOption>,
    ttsOptions: List<SpeechOption>,
    onSaved: (speechChanged: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var mode by remember { mutableStateOf(settings.routerMode) }
    var geminiKey by remember { mutableStateOf(settings.geminiKey) }
    var geminiModel by remember { mutableStateOf(settings.geminiModel) }
    var orKey by remember { mutableStateOf(settings.openRouterKey) }
    var orModel by remember { mutableStateOf(settings.openRouterModel) }
    var claudeKey by remember { mutableStateOf(settings.claudeKey) }
    var claudeModel by remember { mutableStateOf(settings.claudeModel) }
    var url by remember { mutableStateOf(settings.screenHelperUrl) }
    var token by remember { mutableStateOf(settings.screenHelperToken) }
    var stt by remember { mutableStateOf(settings.sttService) }
    var ttsEngine by remember { mutableStateOf(settings.ttsEngine) }
    var debug by remember { mutableStateOf(settings.debugMode) }

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

                Section("Rozpoznawanie mowy (STT)")
                Choices(sttOptions, stt) { stt = it }

                Section("Synteza mowy (TTS)")
                Choices(ttsOptions, ttsEngine) { ttsEngine = it }
                Text(
                    "Głos wybierzesz w telefonie: Ustawienia → Zarządzanie ogólne → Język → Zamiana tekstu na mowę.",
                    style = MaterialTheme.typography.bodySmall,
                )

                Section("Diagnostyka")
                FilterChip(
                    selected = debug,
                    onClick = { debug = !debug },
                    label = { Text(if (debug) "Tryb debug: włączony" else "Tryb debug: wyłączony") },
                )
                Text(
                    "Informacje techniczne (STT, TTS, czasy, model) w pasku na dole. Głosowo: „tryb debug” / „wyłącz debug”.",
                    style = MaterialTheme.typography.bodySmall,
                )

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
                val speechChanged = stt != settings.sttService || ttsEngine != settings.ttsEngine
                settings.sttService = stt
                settings.ttsEngine = ttsEngine
                settings.debugMode = debug
                onSaved(speechChanged)
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Choices(options: List<SpeechOption>, selected: String, onSelect: (String) -> Unit) {
    // Zapisana usługa mogła zostać odinstalowana – pokaż ją mimo to, żeby można było wrócić do domyślnej
    val all = if (options.any { it.id == selected }) options else options + SpeechOption(selected, "$selected (brak)")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        all.forEach { o ->
            FilterChip(selected = o.id == selected, onClick = { onSelect(o.id) }, label = { Text(o.label) })
        }
    }
}
