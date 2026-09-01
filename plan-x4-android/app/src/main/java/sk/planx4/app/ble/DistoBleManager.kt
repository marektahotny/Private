package sk.planx4.app.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

data class BleDeviceInfo(
    val address: String,
    val name: String?,
    val rssi: Int
)

/**
 * Talks to a Leica DISTO X4 over Bluetooth LE using the community-reverse-engineered "basic
 * measurement" service (see [DistoUuids] for sources and caveats — this is NOT an official
 * Leica SDK integration).
 *
 * Usage: caller must already hold BLUETOOTH_SCAN + BLUETOOTH_CONNECT (Android 12+) or
 * ACCESS_FINE_LOCATION (older) before calling [startScan]/[connect] — this class does not
 * request permissions itself, only checks preconditions defensively where cheap to do so.
 *
 * NOTE ON WHAT'S UNVERIFIED: nothing in this file has been run against a physical DISTO X4
 * yet (see the "Plán X4" concept doc, section 03). [CHAR_DISTANCE]'s float32-little-endian
 * decoding is what the community reports for related DISTO models; treat the first real
 * connection as a validation step, not an assumption.
 */
@SuppressLint("MissingPermission")
class DistoBleManager(private val context: Context) {

    private val bluetoothManager: BluetoothManager? =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val adapter: BluetoothAdapter? get() = bluetoothManager?.adapter

    private var gatt: BluetoothGatt? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    // Android's BLE connect is notoriously flaky on the very first attempt to a not-yet-bonded
    // device (status codes like 133/245 from the radio/link layer, unrelated to our GATT code) —
    // a short automatic retry clears most of these without bothering the user.
    private var pendingAddress: String? = null
    private var connectRetries = 0
    private val maxConnectRetries = 2

    private val _connectionState = MutableStateFlow(DistoConnectionState.DISCONNECTED)
    val connectionState: StateFlow<DistoConnectionState> = _connectionState.asStateFlow()

    private val _scanResults = MutableStateFlow<List<BleDeviceInfo>>(emptyList())
    val scanResults: StateFlow<List<BleDeviceInfo>> = _scanResults.asStateFlow()

    private val _measurements = MutableSharedFlow<DistoMeasurement>(replay = 0, extraBufferCapacity = 16)
    val measurements = _measurements.asSharedFlow()

    private val _batteryPercent = MutableStateFlow<Int?>(null)
    val batteryPercent: StateFlow<Int?> = _batteryPercent.asStateFlow()

    /** Human-readable reason for the last connection failure, if any — cleared on the next
     *  [startScan]/[connect] attempt. The UI shows this so a failed connect isn't silently
     *  invisible to the user. */
    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val name = result.scanRecord?.deviceName ?: result.device.name
            // DISTO devices haven't been confirmed to always advertise the service UUID in
            // the scan payload (it may only show up after GATT service discovery), so we
            // also accept anything whose advertised name looks like a DISTO.
            val looksLikeDisto = name?.contains("DISTO", ignoreCase = true) == true
            val advertisesService = result.scanRecord?.serviceUuids
                ?.any { it.uuid == DistoUuids.SERVICE_BASIC_MEASUREMENT } == true
            if (!looksLikeDisto && !advertisesService) return

            val info = BleDeviceInfo(result.device.address, name, result.rssi)
            _scanResults.update { current ->
                if (current.any { it.address == info.address }) {
                    current.map { if (it.address == info.address) info else it }
                } else {
                    current + info
                }
            }
        }

        override fun onScanFailed(errorCode: Int) {
            Log.w(TAG, "BLE scan failed, code=$errorCode")
            _lastError.value = "Vyhľadávanie zariadení zlyhalo (kód $errorCode). Skús vypnúť/zapnúť Bluetooth."
            _connectionState.value = DistoConnectionState.DISCONNECTED
        }
    }

    fun startScan() {
        val scanner = adapter?.bluetoothLeScanner ?: run {
            Log.w(TAG, "No BLE scanner available (Bluetooth off or unsupported)")
            _lastError.value = "Bluetooth na telefóne je vypnutý alebo nie je podporovaný."
            return
        }
        _lastError.value = null
        _scanResults.value = emptyList()
        _connectionState.value = DistoConnectionState.SCANNING
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        scanner.startScan(null, settings, scanCallback)
    }

    fun stopScan() {
        adapter?.bluetoothLeScanner?.stopScan(scanCallback)
        if (_connectionState.value == DistoConnectionState.SCANNING) {
            _connectionState.value = DistoConnectionState.DISCONNECTED
        }
    }

    fun connect(address: String) {
        stopScan()
        pendingAddress = address
        connectRetries = 0
        _lastError.value = null
        doConnect(address)
    }

    private fun doConnect(address: String) {
        val device: BluetoothDevice = adapter?.getRemoteDevice(address) ?: return
        _connectionState.value = DistoConnectionState.CONNECTING
        gatt?.close()
        // Explicit TRANSPORT_LE avoids Android guessing between classic Bluetooth and BLE on
        // dual-mode devices, which is a common cause of connect failures on the very first try.
        gatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
    }

    fun disconnect() {
        pendingAddress = null
        gatt?.disconnect()
    }

    fun close() {
        pendingAddress = null
        gatt?.close()
        gatt = null
        _connectionState.value = DistoConnectionState.DISCONNECTED
    }

    private val gattCallback = object : BluetoothGattCallback() {

        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                // A non-zero status here means the connection attempt itself failed (device out
                // of range, rejected the link, radio hiccup, ...) — without this branch the UI
                // saw nothing at all: connectionState would just silently fall back to
                // DISCONNECTED via the newState switch below with no explanation.
                Log.w(TAG, "GATT error status=$status newState=$newState")
                g.close()
                gatt = null
                val address = pendingAddress
                if (address != null && connectRetries < maxConnectRetries) {
                    connectRetries++
                    Log.w(TAG, "Retrying connect ($connectRetries/$maxConnectRetries) after status=$status")
                    mainHandler.postDelayed({ doConnect(address) }, 500)
                } else {
                    _lastError.value = "Spojenie s X4 zlyhalo (status=$status) aj po opakovaných pokusoch. " +
                        "Skús X4 vypnúť/zapnúť a priblíž telefón bližšie."
                    _connectionState.value = DistoConnectionState.DISCONNECTED
                }
                return
            }
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    _connectionState.value = DistoConnectionState.DISCOVERING_SERVICES
                    g.discoverServices()
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    _connectionState.value = DistoConnectionState.DISCONNECTED
                    g.close()
                    gatt = null
                }
            }
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.w(TAG, "Service discovery failed, status=$status")
                _lastError.value = "Vyhľadanie služieb na X4 zlyhalo (status=$status)."
                g.disconnect()
                return
            }
            val service = g.getService(DistoUuids.SERVICE_BASIC_MEASUREMENT)
            if (service == null) {
                Log.w(
                    TAG,
                    "Service ${DistoUuids.SERVICE_BASIC_MEASUREMENT} not found — this device may " +
                        "expose measurements under a different service than the ones we know about. " +
                        "Worth inspecting with nRF Connect (see concept doc, section 03)."
                )
                _lastError.value = "X4 nemá očakávanú Bluetooth službu na meranie — nájdené GATT služby " +
                    "sú iné, než sme čakali. Over cez nRF Connect (koncept, časť 03) a napíš mi UUID."
                g.disconnect()
                return
            }
            // Subscribe to every characteristic on the service, not just the ones we can
            // already decode — this is what makes it possible to spot the inclination
            // characteristic later just by watching which one changes while tilting the device.
            service.characteristics.forEach { enableNotify(g, it) }

            if (service.characteristics.any { it.uuid == DistoUuids.CHAR_DISTANCE }) {
                // Connected, service found, subscribed — ready to receive measurements. We mark
                // this now rather than waiting for the first actual laser reading, otherwise the
                // UI shows no feedback at all until you fire the laser once.
                _connectionState.value = DistoConnectionState.READY
            } else {
                _lastError.value = "Služba na X4 je nájdená, ale chýba charakteristika na vzdialenosť."
            }
        }

        @Suppress("DEPRECATION") // Old-style callback; still invoked on API 33+ for compatibility, see class doc.
        override fun onCharacteristicChanged(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            val bytes = characteristic.value ?: return
            when (characteristic.uuid) {
                DistoUuids.CHAR_DISTANCE -> {
                    val meters = decodeFloat32LittleEndian(bytes)
                    if (meters != null) {
                        _measurements.tryEmit(DistoMeasurement.Distance(meters.toDouble()))
                    }
                }
                else -> _measurements.tryEmit(DistoMeasurement.RawNotification(characteristic.uuid, bytes))
            }
        }
    }

    private fun enableNotify(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
        val supportsNotify = characteristic.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0
        if (!supportsNotify) return
        g.setCharacteristicNotification(characteristic, true)
        val cccd = characteristic.getDescriptor(DistoUuids.CLIENT_CHARACTERISTIC_CONFIG) ?: return
        cccd.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
        g.writeDescriptor(cccd)
    }

    private fun decodeFloat32LittleEndian(bytes: ByteArray): Float? {
        if (bytes.size < 4) return null
        return ByteBuffer.wrap(bytes, 0, 4).order(ByteOrder.LITTLE_ENDIAN).float
    }

    private companion object {
        const val TAG = "DistoBleManager"
    }
}

private inline fun <T> MutableStateFlow<T>.update(transform: (T) -> T) {
    value = transform(value)
}
