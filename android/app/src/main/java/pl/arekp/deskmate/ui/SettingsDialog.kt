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
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import pl.arekp.deskmate.mcp.McpServerConfig
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
    /** Test połączenia z każdym skonfigurowanym modelem – zwraca linie wyniku. */
    onTest: suspend () -> List<String> = { emptyList() },
    onShareLogs: () -> Unit = {},
    /** Zwraca nazwę zapisanego pliku albo null. */
    onSaveLogs: () -> String? = { null },
    onClearLogs: () -> Unit = {},
    onUploadLogs: suspend () -> String = { "" },
    /** Test jednego serwera MCP – zwraca „✅ … narzędzia” albo „❌ błąd”. */
    onMcpTest: suspend (McpServerConfig) -> String = { "" },
    /** Bieżący stan połączeń MCP (po zapisaniu ustawień). */
    mcpStatus: () -> List<String> = { emptyList() },
) {
    val mcpServers = remember { mutableStateListOf(*McpServerConfig.listFromJson(settings.mcpServersJson).toTypedArray()) }
    val mcpResults = remember { mutableStateMapOf<String, String>() }
    var mcpName by remember { mutableStateOf(if (mcpServers.isEmpty()) "ha" else "") }
    var mcpUrl by remember { mutableStateOf(if (mcpServers.isEmpty()) McpServerConfig.HOME_ASSISTANT_URL_HINT else "") }
    var mcpToken by remember { mutableStateOf("") }
    var mcpImport by remember { mutableStateOf("") }
    var mcpInfo by remember { mutableStateOf("") }
    var logRepo by remember { mutableStateOf(settings.logRepo) }
    var logToken by remember { mutableStateOf(settings.logToken) }
    var logAuto by remember { mutableStateOf(settings.logAutoUpload) }
    var logInfo by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    var testResult by remember { mutableStateOf<List<String>?>(null) }
    var testing by remember { mutableStateOf(false) }
    var mode by remember { mutableStateOf(settings.routerMode) }
    var geminiKey by remember { mutableStateOf(settings.geminiKey) }
    var geminiModel by remember { mutableStateOf(settings.geminiModel) }
    var orKey by remember { mutableStateOf(settings.openRouterKey) }
    var orModel by remember { mutableStateOf(settings.openRouterModel) }
    var claudeKey by remember { mutableStateOf(settings.claudeKey) }
    var claudeModel by remember { mutableStateOf(settings.claudeModel) }
    var url by remember { mutableStateOf(settings.screenHelperUrl) }
    var token by remember { mutableStateOf(settings.screenHelperToken) }
    var city by remember { mutableStateOf(settings.homeCity) }
    var stt by remember { mutableStateOf(settings.sttService) }
    var ttsEngine by remember { mutableStateOf(settings.ttsEngine) }
    var debug by remember { mutableStateOf(settings.debugMode) }
    var bargeIn by remember { mutableStateOf(settings.bargeInVoice) }
    var cameraPip by remember { mutableStateOf(settings.showCameraPreview) }

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

                TextButton(
                    enabled = !testing,
                    onClick = {
                        // Zapisz wpisane klucze i sprawdź każdy model krótkim zapytaniem
                        settings.routerMode = mode
                        settings.geminiKey = geminiKey
                        settings.geminiModel = geminiModel
                        settings.openRouterKey = orKey
                        settings.openRouterModel = orModel
                        settings.claudeKey = claudeKey
                        settings.claudeModel = claudeModel
                        testing = true
                        testResult = listOf("Sprawdzam…")
                        scope.launch {
                            testResult = runCatching { onTest() }.getOrElse { listOf("Błąd testu: ${it.message}") }
                            testing = false
                        }
                    },
                ) { Text(if (testing) "Sprawdzam połączenie…" else "Zapisz klucze i testuj połączenie") }
                testResult?.forEach { line -> Text(line, style = MaterialTheme.typography.bodySmall) }

                Section("Serwery MCP (narzędzia dla agenta)")
                Text(
                    "Agent korzysta z narzędzi z tych serwerów (przedrostek nazwy, np. ha__). Home Assistant: dodaj integrację " +
                        "„Model Context Protocol Server”, adres http://<IP>:8123/api/mcp, token: profil → Bezpieczeństwo → " +
                        "token długoterminowy. Robot steruje tylko urządzeniami udostępnionymi dla Assist.",
                    style = MaterialTheme.typography.bodySmall,
                )
                mcpStatus().forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
                mcpServers.forEachIndexed { i, s ->
                    Text("${s.name} · ${s.url}", style = MaterialTheme.typography.bodyMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(
                            selected = s.enabled,
                            onClick = { mcpServers[i] = s.copy(enabled = !s.enabled) },
                            label = { Text(if (s.enabled) "włączony" else "wyłączony") },
                        )
                        TextButton(onClick = {
                            mcpResults[s.name] = "Sprawdzam…"
                            scope.launch { mcpResults[s.name] = runCatching { onMcpTest(s) }.getOrElse { "❌ ${it.message}" } }
                        }) { Text("Testuj") }
                        TextButton(onClick = { mcpServers.removeAt(i); mcpResults.remove(s.name) }) { Text("Usuń") }
                    }
                    mcpResults[s.name]?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                }
                PlainField("Nazwa (przedrostek), np. ha", mcpName) { mcpName = it }
                PlainField("Adres URL serwera MCP", mcpUrl) { mcpUrl = it }
                SecretField("Token (Bearer)", mcpToken) { mcpToken = it }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TextButton(onClick = {
                        if (mcpName.isBlank() || mcpUrl.isBlank()) {
                            mcpInfo = "Podaj nazwę i adres"
                        } else {
                            mcpServers.removeAll { it.name == mcpName.trim() }
                            mcpServers += McpServerConfig(mcpName.trim(), mcpUrl.trim(), mcpToken.trim())
                            mcpInfo = "Dodano ${mcpName.trim()} – naciśnij Testuj, a potem Zapisz"
                            mcpName = ""; mcpUrl = ""; mcpToken = ""
                        }
                    }) { Text("Dodaj serwer") }
                }
                PlainField("Import JSON {\"mcpServers\": …} (opcjonalnie)", mcpImport) { mcpImport = it }
                TextButton(onClick = {
                    mcpInfo = runCatching {
                        val imported = McpServerConfig.importMcpServers(mcpImport)
                        imported.forEach { n -> mcpServers.removeAll { it.name == n.name }; mcpServers += n }
                        mcpImport = ""
                        "Zaimportowano: ${imported.joinToString { it.name }.ifBlank { "nic" }}"
                    }.getOrElse { "Niepoprawny JSON: ${it.message}" }
                }) { Text("Importuj") }
                if (mcpInfo.isNotBlank()) Text(mcpInfo, style = MaterialTheme.typography.bodySmall)

                Section("Pogoda i wyszukiwanie")
                PlainField("Twoje miasto (domyślne dla pogody)", city) { city = it }
                Text(
                    "Pogoda: Open-Meteo (bez klucza). Wyszukiwanie w internecie: Google przez Twój klucz Gemini, bez klucza – Wikipedia.",
                    style = MaterialTheme.typography.bodySmall,
                )

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

                Section("Przerywanie robota")
                FilterChip(
                    selected = bargeIn,
                    onClick = { bargeIn = !bargeIn },
                    label = { Text(if (bargeIn) "Przerywanie głosem: włączone" else "Przerywanie głosem: wyłączone") },
                )
                Text(
                    "Zawsze działa: dotknięcie ekranu albo machanie. Głosem – eksperymentalnie (domyślnie wyłączone): " +
                        "zacznij głośno mówić, gdy robot mówi. Jeśli robot ucina sam sobie odpowiedzi – wyłącz.",
                    style = MaterialTheme.typography.bodySmall,
                )

                Section("Ekran")
                FilterChip(
                    selected = cameraPip,
                    onClick = { cameraPip = !cameraPip },
                    label = { Text(if (cameraPip) "Podgląd kamery: widoczny" else "Podgląd kamery: ukryty") },
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

                Section("Logi diagnostyczne")
                Text(
                    "Dziennik działania (zapytania do modeli z błędami serwera, narzędzia, słuchanie, awarie). " +
                        "Klucze API są wycinane. Wyślij go do aplikacji Claude albo dołącz do rozmowy, żeby znaleźć błąd.",
                    style = MaterialTheme.typography.bodySmall,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TextButton(onClick = { onShareLogs() }) { Text("Udostępnij logi") }
                    TextButton(onClick = {
                        logInfo = onSaveLogs()?.let { "Zapisano w Pobranych: $it" } ?: "Nie udało się zapisać"
                    }) { Text("Zapisz w Pobranych") }
                    TextButton(onClick = { onClearLogs(); logInfo = "Wyczyszczono" }) { Text("Wyczyść") }
                }
                Text(
                    "Automatycznie do Claude: prywatne repozytorium GitHub + token fine-grained tylko do niego " +
                        "(Contents: Read and write). Claude odczyta logi sam.",
                    style = MaterialTheme.typography.bodySmall,
                )
                PlainField("Repozytorium (prywatne), np. arekp/deskmate-logs", logRepo) { logRepo = it }
                SecretField("Token GitHub (github_pat_…)", logToken) { logToken = it }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(
                        selected = logAuto,
                        onClick = { logAuto = !logAuto },
                        label = { Text(if (logAuto) "Wysyłaj automatycznie po błędach" else "Tylko ręcznie") },
                    )
                    TextButton(onClick = {
                        settings.logRepo = logRepo
                        settings.logToken = logToken
                        settings.logAutoUpload = logAuto
                        logInfo = "Wysyłam…"
                        scope.launch { logInfo = runCatching { onUploadLogs() }.getOrElse { "Błąd: ${it.message}" } }
                    }) { Text("Wyślij logi na GitHub") }
                }
                if (logInfo.isNotBlank()) Text(logInfo, style = MaterialTheme.typography.bodySmall)

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
                settings.homeCity = city
                settings.mcpServersJson = McpServerConfig.listToJson(mcpServers)
                settings.logRepo = logRepo
                settings.logToken = logToken
                settings.logAutoUpload = logAuto
                val speechChanged = stt != settings.sttService || ttsEngine != settings.ttsEngine
                settings.sttService = stt
                settings.ttsEngine = ttsEngine
                settings.debugMode = debug
                settings.bargeInVoice = bargeIn
                settings.showCameraPreview = cameraPip
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
