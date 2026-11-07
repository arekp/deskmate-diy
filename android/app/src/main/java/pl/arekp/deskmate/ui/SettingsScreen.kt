package pl.arekp.deskmate.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import pl.arekp.deskmate.Settings
import pl.arekp.deskmate.ai.llm.LlmRouter
import pl.arekp.deskmate.mcp.McpServerConfig
import pl.arekp.deskmate.voice.SpeechOption

/** Wszystkie edytowalne ustawienia jako jedna wartość – łatwo porównać „czy są niezapisane zmiany”. */
data class ConfigDraft(
    val mode: LlmRouter.Mode,
    val geminiKey: String, val geminiModel: String,
    val orKey: String, val orModel: String,
    val claudeKey: String, val claudeModel: String,
    val mcpServers: List<McpServerConfig>,
    val city: String,
    val stt: String, val tts: String, val bargeIn: Boolean,
    val cameraPip: Boolean, val debug: Boolean,
    val logRepo: String, val logToken: String, val logAuto: Boolean,
    val screenUrl: String, val screenToken: String,
) {
    /** Zapisuje do ustawień; zwraca true, gdy zmieniła się usługa mowy (trzeba ją przeładować). */
    fun save(s: Settings): Boolean {
        val speechChanged = stt != s.sttService || tts != s.ttsEngine
        s.routerMode = mode
        s.geminiKey = geminiKey; s.geminiModel = geminiModel
        s.openRouterKey = orKey; s.openRouterModel = orModel
        s.claudeKey = claudeKey; s.claudeModel = claudeModel
        s.mcpServersJson = McpServerConfig.listToJson(mcpServers)
        s.homeCity = city
        s.sttService = stt; s.ttsEngine = tts; s.bargeInVoice = bargeIn
        s.showCameraPreview = cameraPip; s.debugMode = debug
        s.logRepo = logRepo; s.logToken = logToken; s.logAutoUpload = logAuto
        s.screenHelperUrl = screenUrl; s.screenHelperToken = screenToken
        return speechChanged
    }

    companion object {
        fun from(s: Settings) = ConfigDraft(
            s.routerMode, s.geminiKey, s.geminiModel, s.openRouterKey, s.openRouterModel, s.claudeKey, s.claudeModel,
            McpServerConfig.listFromJson(s.mcpServersJson), s.homeCity,
            s.sttService, s.ttsEngine, s.bargeInVoice, s.showCameraPreview, s.debugMode,
            s.logRepo, s.logToken, s.logAutoUpload, s.screenHelperUrl, s.screenHelperToken,
        )
    }
}

/** Akcje, które ekran ustawień zleca reszcie aplikacji. */
class SettingsActions(
    val sttOptions: List<SpeechOption>,
    val ttsOptions: List<SpeechOption>,
    val testModels: suspend () -> List<String>,
    val testMcp: suspend (McpServerConfig) -> String,
    val mcpStatus: () -> List<String>,
    val shareLogs: () -> Unit,
    val saveLogs: () -> String?,
    val clearLogs: () -> Unit,
    val uploadLogs: suspend () -> String,
    /** Eksport / import pliku z kopią konfiguracji (systemowy wybór pliku). */
    val exportConfig: () -> Unit,
    val importConfig: () -> Unit,
    val appInfo: String,
)

private enum class Sec(val title: String) {
    MODELS("Modele AI"), MCP("Serwery MCP"), WEB("Pogoda i wyszukiwanie"), SPEECH("Mowa"),
    SCREEN("Ekran i diagnostyka"), LOGS("Logi"), PC("Pomocnik PC"), BACKUP("Kopia konfiguracji"),
}

/**
 * Pełnoekranowe ustawienia. Na czas ich otwarcia robot jest wstrzymany (oczy zamknięte, bez słuchania, kamera wyłączona).
 * Zmiany są w szkicu – trafiają do ustawień dopiero po „Zapisz”; wyjście z niezapisanymi zmianami pyta, co zrobić.
 *
 * @param reloadKey zmiana wartości wczytuje szkic od nowa z ustawień (np. po imporcie kopii)
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    settings: Settings,
    actions: SettingsActions,
    reloadKey: Int,
    onSaved: (speechChanged: Boolean) -> Unit,
    onClose: () -> Unit,
) {
    var saved by remember(reloadKey) { mutableStateOf(ConfigDraft.from(settings)) }
    var d by remember(reloadKey) { mutableStateOf(saved) }
    val dirty = d != saved
    var askLeave by remember { mutableStateOf(false) }
    var savedInfo by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val list = rememberLazyListState()

    fun save() {
        val speechChanged = d.save(settings)
        saved = d
        savedInfo = "Zapisano ✓"
        onSaved(speechChanged)
    }

    BackHandler { if (dirty) askLeave = true else onClose() }

    MaterialTheme(colorScheme = darkColorScheme()) {
        Surface(Modifier.fillMaxSize(), color = Color(0xFF0E1418)) {
            Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).imePadding()) {
                // Pasek górny
                Row(
                    Modifier.fillMaxWidth().background(Color(0xFF162027)).padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = { if (dirty) askLeave = true else onClose() }) { Text("← Wróć") }
                    Text("Ustawienia", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    if (savedInfo.isNotBlank() && !dirty) Text(savedInfo, color = Color(0xFF9CF7D8), modifier = Modifier.padding(end = 8.dp))
                    Button(enabled = dirty, onClick = { save() }) { Text("Zapisz") }
                }
                // Skróty do sekcji
                LazyRow(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    item { Spacer(Modifier.padding(start = 4.dp)) }
                    items(Sec.entries) { s ->
                        AssistChip(onClick = { scope.launch { list.animateScrollToItem(s.ordinal) } }, label = { Text(s.title) })
                    }
                }
                HorizontalDivider()

                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                    LazyColumn(
                        state = list,
                        modifier = Modifier.widthIn(max = 760.dp).fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
                    ) {
                        item { ModelsSection(d, { d = it }, actions, settings) }
                        item { McpSection(d, { d = it }, actions) }
                        item {
                            Group(Sec.WEB.title, "Pogoda: Open-Meteo (bez klucza). Wyszukiwanie: Google przez klucz Gemini, bez niego Google News i Wikipedia.") {
                                Field("Twoje miasto (domyślne dla pogody)", d.city) { d = d.copy(city = it) }
                            }
                        }
                        item {
                            Group(Sec.SPEECH.title, "Głos wybierzesz w telefonie: Ustawienia → Zarządzanie ogólne → Język → Zamiana tekstu na mowę.") {
                                Label("Rozpoznawanie mowy (STT)")
                                Choices(actions.sttOptions, d.stt) { d = d.copy(stt = it) }
                                Label("Synteza mowy (TTS)")
                                Choices(actions.ttsOptions, d.tts) { d = d.copy(tts = it) }
                                Toggle("Przerywanie robota głosem (eksperymentalne)", d.bargeIn) { d = d.copy(bargeIn = it) }
                                Hint("Dotknięcie ekranu i machanie przerywają zawsze. Jeśli robot ucina sam sobie odpowiedzi – wyłącz.")
                            }
                        }
                        item {
                            Group(Sec.SCREEN.title) {
                                Toggle("Podgląd kamery w prawym górnym rogu", d.cameraPip) { d = d.copy(cameraPip = it) }
                                Toggle("Tryb debug (informacje techniczne w pasku)", d.debug) { d = d.copy(debug = it) }
                                Hint("Debug głosem: „tryb debug” / „wyłącz debug”.")
                            }
                        }
                        item { LogsSection(d, { d = it }, actions) }
                        item {
                            Group(Sec.PC.title, "Opcjonalnie: zrzuty ekranu komputera dla robota (desktop/screen_helper.py).") {
                                Field("Adres, np. http://192.168.1.50:8765", d.screenUrl) { d = d.copy(screenUrl = it) }
                                Secret("Token", d.screenToken) { d = d.copy(screenToken = it) }
                            }
                        }
                        item {
                            Group(
                                Sec.BACKUP.title,
                                "Aktualizacje aplikacji zachowują ustawienia automatycznie. Kopia w pliku przyda się po " +
                                    "odinstalowaniu albo na nowym telefonie. Plik zawiera klucze – trzymaj go w bezpiecznym miejscu.",
                            ) {
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(onClick = {
                                        if (dirty) save()
                                        actions.exportConfig()
                                    }) { Text("Zapisz kopię do pliku…") }
                                    OutlinedButton(onClick = actions.importConfig) { Text("Wczytaj kopię z pliku…") }
                                }
                                Hint(actions.appInfo)
                            }
                        }
                        item { Spacer(Modifier.height(80.dp)) }
                    }
                }
            }
        }

        if (askLeave) {
            AlertDialog(
                onDismissRequest = { askLeave = false },
                title = { Text("Niezapisane zmiany") },
                text = { Text("Zapisać zmiany przed wyjściem?") },
                confirmButton = { TextButton(onClick = { save(); askLeave = false; onClose() }) { Text("Zapisz") } },
                dismissButton = {
                    Row {
                        TextButton(onClick = { askLeave = false; onClose() }) { Text("Odrzuć") }
                        TextButton(onClick = { askLeave = false }) { Text("Anuluj") }
                    }
                },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ModelsSection(d: ConfigDraft, set: (ConfigDraft) -> Unit, actions: SettingsActions, settings: Settings) {
    val scope = rememberCoroutineScope()
    var result by remember { mutableStateOf<List<String>?>(null) }
    var testing by remember { mutableStateOf(false) }
    Group(Sec.MODELS.title, "Kolejność w trybie Automatycznie: Gemini → OpenRouter → Claude (płatny, tylko gdy ma klucz).") {
        Label("Tryb wyboru modelu")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            LlmRouter.Mode.entries.forEach { m ->
                FilterChip(selected = d.mode == m, onClick = { set(d.copy(mode = m)) }, label = { Text(m.label) })
            }
        }
        Label("Gemini – darmowy pakiet (aistudio.google.com)")
        Secret("Klucz Gemini", d.geminiKey) { set(d.copy(geminiKey = it)) }
        Field("Model Gemini", d.geminiModel) { set(d.copy(geminiModel = it)) }
        Label("OpenRouter – darmowe modele (openrouter.ai)")
        Secret("Klucz OpenRouter", d.orKey) { set(d.copy(orKey = it)) }
        Field("Model OpenRouter", d.orModel) { set(d.copy(orModel = it)) }
        Label("Claude – płatne API (console.anthropic.com)")
        Secret("Klucz Claude API", d.claudeKey) { set(d.copy(claudeKey = it)) }
        Field("Model Claude", d.claudeModel) { set(d.copy(claudeModel = it)) }
        OutlinedButton(enabled = !testing, onClick = {
            // test działa na wpisanych wartościach – zapisujemy same klucze modeli
            settings.routerMode = d.mode
            settings.geminiKey = d.geminiKey; settings.geminiModel = d.geminiModel
            settings.openRouterKey = d.orKey; settings.openRouterModel = d.orModel
            settings.claudeKey = d.claudeKey; settings.claudeModel = d.claudeModel
            testing = true
            result = listOf("Sprawdzam…")
            scope.launch {
                result = runCatching { actions.testModels() }.getOrElse { listOf("Błąd testu: ${it.message}") }
                testing = false
            }
        }) { Text(if (testing) "Sprawdzam…" else "Testuj połączenie z modelami") }
        result?.forEach { Hint(it) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun McpSection(d: ConfigDraft, set: (ConfigDraft) -> Unit, actions: SettingsActions) {
    val scope = rememberCoroutineScope()
    val results = remember { mutableStateMapOf<String, String>() }
    var name by remember { mutableStateOf(if (d.mcpServers.isEmpty()) "ha" else "") }
    var url by remember { mutableStateOf(if (d.mcpServers.isEmpty()) McpServerConfig.HOME_ASSISTANT_URL_HINT else "") }
    var token by remember { mutableStateOf("") }
    var import by remember { mutableStateOf("") }
    var info by remember { mutableStateOf("") }

    Group(
        Sec.MCP.title,
        "Narzędzia z tych serwerów dostaje agent (przedrostek nazwy, np. ha__). Home Assistant: integracja „Model Context " +
            "Protocol Server”, adres http://<IP>:8123/api/mcp, token długoterminowy z profilu → Bezpieczeństwo. " +
            "Robot steruje tylko urządzeniami udostępnionymi dla Assist.",
    ) {
        actions.mcpStatus().forEach { Hint("Teraz: $it") }
        d.mcpServers.forEachIndexed { i, s ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(10.dp)) {
                    Text(s.name, fontWeight = FontWeight.Bold)
                    Hint(s.url)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(
                            selected = s.enabled,
                            onClick = { set(d.copy(mcpServers = d.mcpServers.toMutableList().also { it[i] = s.copy(enabled = !s.enabled) })) },
                            label = { Text(if (s.enabled) "włączony" else "wyłączony") },
                        )
                        TextButton(onClick = {
                            results[s.name] = "Sprawdzam…"
                            scope.launch { results[s.name] = runCatching { actions.testMcp(s) }.getOrElse { "❌ ${it.message}" } }
                        }) { Text("Testuj") }
                        TextButton(onClick = {
                            name = s.name; url = s.url; token = s.token
                            info = "Edytujesz ${s.name} – popraw pola niżej i naciśnij „Dodaj / zmień”"
                        }) { Text("Edytuj") }
                        TextButton(onClick = {
                            set(d.copy(mcpServers = d.mcpServers.filterIndexed { j, _ -> j != i }))
                            results.remove(s.name)
                        }) { Text("Usuń") }
                    }
                    results[s.name]?.let { Hint(it) }
                }
            }
        }
        Label("Dodaj albo zmień serwer")
        Field("Nazwa (przedrostek), np. ha", name) { name = it }
        Field("Adres URL serwera MCP", url) { url = it }
        Secret("Token (Bearer)", token) { token = it }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = {
                if (name.isBlank() || url.isBlank()) {
                    info = "Podaj nazwę i adres"
                } else {
                    val n = McpServerConfig(name.trim(), url.trim(), token.trim())
                    set(d.copy(mcpServers = d.mcpServers.filter { it.name != n.name } + n))
                    info = "Dodano ${n.name} – naciśnij Testuj, potem Zapisz (na górze)"
                    name = ""; url = ""; token = ""
                }
            }) { Text("Dodaj / zmień") }
        }
        Field("Import JSON {\"mcpServers\": …} (opcjonalnie)", import, singleLine = false) { import = it }
        OutlinedButton(onClick = {
            info = runCatching {
                val imported = McpServerConfig.importMcpServers(import)
                val names = imported.map { it.name }.toSet()
                set(d.copy(mcpServers = d.mcpServers.filter { it.name !in names } + imported))
                import = ""
                "Zaimportowano: ${imported.joinToString { it.name }.ifBlank { "nic" }}"
            }.getOrElse { "Niepoprawny JSON: ${it.message}" }
        }) { Text("Importuj JSON") }
        if (info.isNotBlank()) Hint(info)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LogsSection(d: ConfigDraft, set: (ConfigDraft) -> Unit, actions: SettingsActions) {
    val scope = rememberCoroutineScope()
    var info by remember { mutableStateOf("") }
    Group(Sec.LOGS.title, "Dziennik działania (modele z błędami serwera, narzędzia, słuchanie, awarie). Klucze są wycinane.") {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = actions.shareLogs) { Text("Udostępnij logi") }
            OutlinedButton(onClick = { info = actions.saveLogs()?.let { "Zapisano w Pobranych: $it" } ?: "Nie udało się zapisać" }) {
                Text("Zapisz w Pobranych")
            }
            OutlinedButton(onClick = { actions.clearLogs(); info = "Wyczyszczono" }) { Text("Wyczyść") }
        }
        Label("Automatyczna wysyłka na GitHub (prywatne repozytorium)")
        Field("Repozytorium, np. arekp/deskmate-logs", d.logRepo) { set(d.copy(logRepo = it)) }
        Secret("Token GitHub (github_pat_…)", d.logToken) { set(d.copy(logToken = it)) }
        Toggle("Wysyłaj automatycznie po błędach", d.logAuto) { set(d.copy(logAuto = it)) }
        OutlinedButton(onClick = {
            info = "Wysyłam…"
            scope.launch { info = runCatching { actions.uploadLogs() }.getOrElse { "Błąd: ${it.message}" } }
        }) { Text("Wyślij logi teraz") }
        Hint("Wysyłka używa zapisanych ustawień – najpierw Zapisz.")
        if (info.isNotBlank()) Hint(info)
    }
}

// --- Klocki ---------------------------------------------------------------------

@Composable
private fun Group(title: String, description: String = "", content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            if (description.isNotBlank()) Hint(description)
            content()
        }
    }
}

@Composable
private fun Label(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 6.dp))
}

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = Color(0xFFB0BEC5))
}

@Composable
private fun Field(label: String, value: String, singleLine: Boolean = true, onChange: (String) -> Unit) {
    OutlinedTextField(value, onChange, label = { Text(label) }, singleLine = singleLine, modifier = Modifier.fillMaxWidth())
}

@Composable
private fun Secret(label: String, value: String, onChange: (String) -> Unit) {
    var show by remember { mutableStateOf(false) }
    OutlinedTextField(
        value, onChange,
        label = { Text(label) },
        singleLine = true,
        visualTransformation = if (show) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = { TextButton(onClick = { show = !show }) { Text(if (show) "Ukryj" else "Pokaż") } },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun Toggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    FilterChip(selected = checked, onClick = { onChange(!checked) }, label = { Text((if (checked) "✓ " else "") + label) })
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Choices(options: List<SpeechOption>, selected: String, onSelect: (String) -> Unit) {
    // Zapisana usługa mogła zostać odinstalowana – pokaż ją mimo to, żeby można było wrócić do domyślnej
    val all = if (options.any { it.id == selected }) options else options + SpeechOption(selected, "$selected (brak)")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        all.forEach { o -> FilterChip(selected = o.id == selected, onClick = { onSelect(o.id) }, label = { Text(o.label) }) }
    }
}
