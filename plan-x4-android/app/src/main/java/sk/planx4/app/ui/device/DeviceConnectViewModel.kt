package sk.planx4.app.ui.device

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.StateFlow
import sk.planx4.app.ble.BleDeviceInfo
import sk.planx4.app.ble.DistoBleManager
import sk.planx4.app.ble.DistoConnectionState

class DeviceConnectViewModel(private val bleManager: DistoBleManager) : ViewModel() {

    val connectionState: StateFlow<DistoConnectionState> = bleManager.connectionState
    val scanResults: StateFlow<List<BleDeviceInfo>> = bleManager.scanResults

    fun startScan() = bleManager.startScan()
    fun stopScan() = bleManager.stopScan()
    fun connect(address: String) = bleManager.connect(address)
    fun disconnect() = bleManager.disconnect()

    override fun onCleared() {
        bleManager.stopScan()
    }
}
