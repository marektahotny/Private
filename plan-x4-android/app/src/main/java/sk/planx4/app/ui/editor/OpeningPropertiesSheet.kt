package sk.planx4.app.ui.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import sk.planx4.core.model.MeasurementSource
import sk.planx4.core.model.OpeningType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OpeningPropertiesSheet(
    wallLengthM: Double,
    liveDistanceM: Double?,
    sheetState: SheetState,
    onDismiss: () -> Unit,
    onConfirm: (type: OpeningType, offsetM: Double, widthM: Double, heightM: Double, sillM: Double?, source: MeasurementSource) -> Unit
) {
    var type by remember { mutableStateOf(OpeningType.WINDOW) }
    var offsetText by remember { mutableStateOf("") }
    var widthText by remember { mutableStateOf("") }
    var heightText by remember { mutableStateOf(if (type == OpeningType.DOOR) "197" else "140") }
    var sillText by remember { mutableStateOf("90") }
    var usedLiveOffset by remember { mutableStateOf(false) }
    var usedLiveWidth by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Pridanie otvoru", style = MaterialTheme.typography.titleMedium)
            Text(
                "Stojíš v rohu: odmeraj X4 k bližšiemu okraju otvoru (odsadenie), potom k druhému okraju (šírka).",
                style = MaterialTheme.typography.bodyLarge
            )

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(selected = type == OpeningType.DOOR, onClick = { type = OpeningType.DOOR }, label = { Text("Dvere") })
                FilterChip(selected = type == OpeningType.WINDOW, onClick = { type = OpeningType.WINDOW }, label = { Text("Okno") })
            }

            MeasureField(
                label = "Odsadenie od rohu (cm)",
                valueText = offsetText,
                onValueChange = { offsetText = it; usedLiveOffset = false },
                liveDistanceM = liveDistanceM,
                onUseLive = { m -> offsetText = (m * 100).toInt().toString(); usedLiveOffset = true }
            )
            MeasureField(
                label = "Šírka otvoru (cm)",
                valueText = widthText,
                onValueChange = { widthText = it; usedLiveWidth = false },
                liveDistanceM = liveDistanceM,
                onUseLive = { m ->
                    // The second X4 reading is the distance to the far edge from the same
                    // corner — width is that minus the offset we already captured.
                    val offsetM = offsetText.toDoubleOrNull()?.div(100.0) ?: 0.0
                    widthText = ((m - offsetM) * 100).toInt().coerceAtLeast(1).toString()
                    usedLiveWidth = true
                }
            )

            OutlinedTextField(
                value = heightText,
                onValueChange = { heightText = it },
                label = { Text("Výška (cm)") },
                modifier = Modifier.fillMaxWidth()
            )
            if (type == OpeningType.WINDOW) {
                OutlinedTextField(
                    value = sillText,
                    onValueChange = { sillText = it },
                    label = { Text("Parapet od podlahy (cm)") },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            val offsetM = offsetText.toDoubleOrNull()?.div(100.0)
            val widthM = widthText.toDoubleOrNull()?.div(100.0)
            val heightM = heightText.toDoubleOrNull()?.div(100.0)
            val valid = offsetM != null && widthM != null && widthM > 0 && heightM != null && heightM > 0 &&
                offsetM >= 0 && offsetM + widthM <= wallLengthM + 1e-6

            if (offsetM != null && widthM != null && offsetM + widthM > wallLengthM + 1e-6) {
                Text(
                    "Otvor sa na túto stenu nezmestí (stena má ${"%.2f".format(wallLengthM)} m).",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyLarge
                )
            }

            Button(
                onClick = {
                    val source = if (usedLiveOffset || usedLiveWidth) MeasurementSource.DISTO_X4 else MeasurementSource.MANUAL
                    onConfirm(type, offsetM!!, widthM!!, heightM!!, sillText.toDoubleOrNull()?.div(100.0), source)
                    onDismiss()
                },
                enabled = valid,
                modifier = Modifier.fillMaxWidth()
            ) { Text("Pridať") }
        }
    }
}

@Composable
private fun MeasureField(
    label: String,
    valueText: String,
    onValueChange: (String) -> Unit,
    liveDistanceM: Double?,
    onUseLive: (Double) -> Unit
) {
    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = valueText,
            onValueChange = onValueChange,
            label = { Text(label) },
            modifier = Modifier.weight(1f)
        )
        OutlinedButton(onClick = { liveDistanceM?.let(onUseLive) }, enabled = liveDistanceM != null) {
            Text("X4")
        }
    }
}
