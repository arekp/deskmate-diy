package pl.arekp.deskmate.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import android.os.ParcelUuid
import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import pl.arekp.deskmate.Config

/**
 * Połączenie z "ciałem" robota (ESP32) przez Bluetooth LE.
 * Komendy tekstowe: docs/protokol-ble.md. Zapisy GATT idą po kolei (Android pozwala na jedną operację naraz).
 */
@SuppressLint("MissingPermission") // uprawnienia sprawdza MainActivity przed start()
class RobotLink(private val context: Context, private val scope: CoroutineScope) {

    enum class State(val label: String) {
        DISCONNECTED("robot: rozłączony"),
        SCANNING("robot: szukam…"),
        CONNECTING("robot: łączę…"),
        CONNECTED("robot: połączony"),
    }

    private val _state = MutableStateFlow(State.DISCONNECTED)
    val state: StateFlow<State> = _state.asStateFlow()

    /** Komunikaty z robota, np. "TOUCH", "OK", "S 0.0 0.0 idle=1 sleep=0". */
    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 32)
    val events: SharedFlow<String> = _events.asSharedFlow()

    private val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
    private var gatt: BluetoothGatt? = null
    private var cmdChr: BluetoothGattCharacteristic? = null
    private var pendingWrite: CompletableDeferred<Boolean>? = null
    private val outbox = Channel<String>(capacity = 32)
    private var writerJob: Job? = null
    private var stopped = false

    fun start() {
        stopped = false
        if (writerJob == null) writerJob = scope.launch(Dispatchers.IO) { writerLoop() }
        startScan()
    }

    fun stop() {
        stopped = true
        stopScan()
        gatt?.close()
        gatt = null
        cmdChr = null
        _state.value = State.DISCONNECTED
    }

    /** Wyślij komendę. Gdy kolejka pełna (np. zalew komend L), komenda jest pomijana. */
    fun send(command: String) {
        if (_state.value != State.CONNECTED) return
        outbox.trySend(command)
    }

    // --- Skanowanie --------------------------------------------------------

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            if (_state.value != State.SCANNING) return
            stopScan()
            connect(result.device)
        }

        override fun onScanFailed(errorCode: Int) {
            Log.w(TAG, "Skanowanie nieudane: $errorCode")
            retryLater()
        }
    }

    private fun startScan() {
        val scanner = adapter?.bluetoothLeScanner ?: run { retryLater(); return }
        _state.value = State.SCANNING
        val filter = ScanFilter.Builder().setServiceUuid(ParcelUuid(Config.SERVICE_UUID)).build()
        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
        scanner.startScan(listOf(filter), settings, scanCallback)
    }

    private fun stopScan() {
        runCatching { adapter?.bluetoothLeScanner?.stopScan(scanCallback) }
    }

    private fun retryLater() {
        if (stopped) return
        _state.value = State.DISCONNECTED
        scope.launch {
            delay(3000)
            if (!stopped && _state.value == State.DISCONNECTED) startScan()
        }
    }

    // --- GATT --------------------------------------------------------------

    private fun connect(device: BluetoothDevice) {
        _state.value = State.CONNECTING
        gatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> g.discoverServices()
                BluetoothProfile.STATE_DISCONNECTED -> {
                    g.close()
                    gatt = null
                    cmdChr = null
                    pendingWrite?.complete(false)
                    retryLater()
                }
            }
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            val service = g.getService(Config.SERVICE_UUID)
            val cmd = service?.getCharacteristic(Config.CMD_UUID)
            val statusChr = service?.getCharacteristic(Config.STATUS_UUID)
            if (cmd == null || statusChr == null) {
                Log.w(TAG, "Brak usługi DeskMate na urządzeniu")
                g.disconnect()
                return
            }
            cmdChr = cmd
            g.setCharacteristicNotification(statusChr, true)
            statusChr.getDescriptor(Config.CCCD_UUID)?.let { writeDescriptor(g, it) }
            _state.value = State.CONNECTED
            scope.launch {
                delay(300) // poczekaj na zapis deskryptora powiadomień
                send("G wake")
            }
        }

        override fun onCharacteristicWrite(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int,
        ) {
            pendingWrite?.complete(status == BluetoothGatt.GATT_SUCCESS)
        }

        // Android 13+
        override fun onCharacteristicChanged(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
        ) {
            _events.tryEmit(value.decodeToString())
        }

        // Android 12 i starsze
        @Deprecated("Deprecated in Java")
        override fun onCharacteristicChanged(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                @Suppress("DEPRECATION")
                _events.tryEmit(characteristic.value?.decodeToString() ?: return)
            }
        }
    }

    private suspend fun writerLoop() {
        for (command in outbox) {
            val g = gatt ?: continue
            val chr = cmdChr ?: continue
            val done = CompletableDeferred<Boolean>()
            pendingWrite = done
            val bytes = (command + "\n").encodeToByteArray()
            val started = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                g.writeCharacteristic(chr, bytes, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT) ==
                    BluetoothGatt.GATT_SUCCESS
            } else {
                @Suppress("DEPRECATION")
                chr.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                @Suppress("DEPRECATION")
                chr.value = bytes
                @Suppress("DEPRECATION")
                g.writeCharacteristic(chr)
            }
            if (started) withTimeoutOrNull(500) { done.await() }
            pendingWrite = null
        }
    }

    private fun writeDescriptor(g: BluetoothGatt, d: BluetoothGattDescriptor) {
        val value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            g.writeDescriptor(d, value)
        } else {
            @Suppress("DEPRECATION")
            d.value = value
            @Suppress("DEPRECATION")
            g.writeDescriptor(d)
        }
    }

    private companion object {
        const val TAG = "RobotLink"
    }
}
