package sk.planx4.app.ui.device

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import sk.planx4.app.ble.BleDeviceInfo
import sk.planx4.app.ble.DistoConnectionState

private val bluetoothPermissions: Array<String> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
    } else {
        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceConnectScreen(viewModel: DeviceConnectViewModel, onBack: () -> Unit) {
    val connection by viewModel.connectionState.collectAsState()
    val results by viewModel.scanResults.collectAsState()
    val lastError by viewModel.lastError.collectAsState()
    var hasPermission by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        hasPermission = grants.values.all { it }
        if (hasPermission) viewModel.startScan()
    }

    LaunchedEffect(Unit) { permissionLauncher.launch(bluetoothPermissions) }

    Scaffold(topBar = { TopAppBar(title = { Text("Bluetooth zariadenia") }) }) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp)) {
            if (!hasPermission) {
                Text("Appka potrebuje povolenie na Bluetooth, aby videla DISTO X4 v okolí.")
                return@Column
            }

            when (connection) {
                DistoConnectionState.SCANNING -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.padding(end = 12.dp))
                    Text("Hľadám zariadenia…")
                }
                DistoConnectionState.CONNECTING, DistoConnectionState.DISCOVERING_SERVICES ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.padding(end = 12.dp))
                        Text(
                            if (connection == DistoConnectionState.CONNECTING) "Pripájam sa na X4…"
                            else "Hľadám služby na X4…"
                        )
                    }
                DistoConnectionState.READY -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("X4 pripojené ✓", color = MaterialTheme.colorScheme.secondary)
                }
                else -> Button(onClick = { viewModel.startScan() }) { Text("Hľadať znova") }
            }

            lastError?.let { message ->
                Text(
                    message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(top = 12.dp))

            if (results.isEmpty()) {
                Text(
                    "Zatiaľ nič nenájdené. Over, že je na DISTO X4 zapnutý Bluetooth a šifrovanie je vypnuté.",
                    style = MaterialTheme.typography.bodyLarge
                )
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 8.dp)) {
                    items(results, key = { it.address }) { device ->
                        DeviceRow(device = device, connected = connection == DistoConnectionState.READY) {
                            viewModel.connect(device.address)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DeviceRow(device: BleDeviceInfo, connected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(12.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text(device.name ?: "Neznáme zariadenie", style = MaterialTheme.typography.titleMedium)
            Text(device.address, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text("${device.rssi} dBm", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
