package pl.arekp.deskmate

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pl.arekp.deskmate.ui.CameraPip
import pl.arekp.deskmate.ui.EyesScreen
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import pl.arekp.deskmate.ui.SettingsActions
import pl.arekp.deskmate.ui.SettingsScreen
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf

class MainActivity : ComponentActivity() {

    private lateinit var brain: DeskMateBrain

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            if (result.values.all { it }) {
                brain.start()
            } else {
                Toast.makeText(this, "Bez uprawnień do kamery, mikrofonu i Bluetooth robot nie zadziała.", Toast.LENGTH_LONG).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        hideSystemBars()

        Settings(this).let { s -> DiagLog.init(filesDir) { LogShare.secretsOf(s) } }
        brain = DeskMateBrain(this)
        setContent {
            val state by brain.ui.collectAsStateWithLifecycle()
            var showSettings by remember { mutableStateOf(false) }
            var settingsReload by remember { mutableIntStateOf(0) }

            // Ustawienia = robot wstrzymany (oczy zamknięte, bez słuchania i mówienia, kamera wyłączona)
            LaunchedEffect(showSettings) { if (showSettings) brain.pause() else brain.resume() }

            // Kopia konfiguracji: systemowy wybór pliku (zapis / odczyt)
            val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
                if (uri != null) {
                    val ok = runCatching {
                        val json = ConfigBackup.toJson(brain.settings.exportAll(), BuildConfig.VERSION_NAME, java.time.LocalDateTime.now().toString())
                        contentResolver.openOutputStream(uri)!!.use { it.write(json.toByteArray()) }
                    }
                    DiagLog.i("Ustawienia", "Eksport konfiguracji: ${ok.exceptionOrNull()?.message ?: "OK"}")
                    toast(if (ok.isSuccess) "Zapisano kopię konfiguracji" else "Nie udało się zapisać: ${ok.exceptionOrNull()?.message}")
                }
            }
            val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                if (uri != null) {
                    val r = runCatching {
                        val json = contentResolver.openInputStream(uri)!!.use { it.readBytes().decodeToString() }
                        brain.settings.importAll(ConfigBackup.parse(json))
                    }
                    DiagLog.i("Ustawienia", "Import konfiguracji: ${r.exceptionOrNull()?.message ?: "OK"}")
                    if (r.isSuccess) {
                        settingsReload++
                        brain.onSettingsSaved(speechChanged = true)
                        toast("Wczytano konfigurację")
                    } else {
                        toast("Nie wczytano: ${r.exceptionOrNull()?.message}")
                    }
                }
            }

            if (showSettings) {
                SettingsScreen(
                    settings = brain.settings,
                    actions = remember {
                        SettingsActions(
                            sttOptions = brain.sttOptions(),
                            ttsOptions = brain.ttsOptions(),
                            testModels = { brain.testProviders() },
                            testMcp = { brain.mcp.test(it) },
                            mcpStatus = { brain.mcp.statuses.map { (if (it.ok) "✅ " else "❌ ") + it.text } },
                            shareLogs = { LogShare.share(this@MainActivity, brain.diagnosticReport()) },
                            saveLogs = { LogShare.saveToDownloads(this@MainActivity, brain.diagnosticReport()) },
                            clearLogs = { DiagLog.clear() },
                            uploadLogs = { brain.uploadLogs() },
                            exportConfig = { exportLauncher.launch("deskmate-konfiguracja.json") },
                            importConfig = { importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) },
                            appInfo = "Wersja ${BuildConfig.VERSION_NAME}, build ${BuildConfig.BUILD_NUMBER} (${BuildConfig.GIT_SHA}), " +
                                "konfiguracja v${Settings.CONFIG_VERSION}",
                        )
                    },
                    reloadKey = settingsReload,
                    onSaved = { brain.onSettingsSaved(it) },
                    onClose = { showSettings = false },
                )
            } else {
                // Dotknięcie: przerwij robota / zacznij słuchać. Przytrzymanie palca: ustawienia
                Box(Modifier.fillMaxSize()) {
                    EyesScreen(
                        state,
                        brain.gaze,
                        Modifier.pointerInput(Unit) { detectTapGestures(onTap = { brain.onTap() }, onLongPress = { showSettings = true }) },
                    )
                    if (state.showCamera) {
                        val face by brain.face.collectAsStateWithLifecycle()
                        val scene by brain.scene.collectAsStateWithLifecycle()
                        CameraPip(face, scene, brain::attachPreview, Modifier.align(Alignment.TopEnd).padding(12.dp))
                    }
                }
            }
        }

        val missing = requiredPermissions().filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) brain.start() else permissionLauncher.launch(missing.toTypedArray())
    }

    private fun toast(text: String) = Toast.makeText(this, text, Toast.LENGTH_LONG).show()

    override fun onDestroy() {
        brain.stop()
        super.onDestroy()
    }

    private fun hideSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    private fun requiredPermissions(): List<String> = buildList {
        add(Manifest.permission.CAMERA)
        add(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            add(Manifest.permission.BLUETOOTH_SCAN)
            add(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }
}
