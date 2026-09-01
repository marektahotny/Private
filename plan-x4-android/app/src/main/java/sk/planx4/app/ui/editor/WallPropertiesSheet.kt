package sk.planx4.app.ui.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import sk.planx4.core.model.MeasurementSource
import sk.planx4.core.model.Wall
import sk.planx4.core.model.WallSide
import sk.planx4.core.model.WallType

/**
 * Wall's properties panel — includes the "sketch first, correct later" flow: length and
 * thickness can both be re-measured with the X4 after the wall is already drawn, not only
 * while first placing it (concept doc, section 06/07).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WallPropertiesSheet(
    wall: Wall,
    sheetState: SheetState,
    liveDistanceM: Double?,
    onDismiss: () -> Unit,
    onLengthChange: (m: Double, source: MeasurementSource) -> Unit,
    onThicknessChange: (cm: Double) -> Unit,
    onSideChange: (WallSide) -> Unit,
    onTypeChange: (WallType) -> Unit,
    onAddOpeningClick: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Vlastnosti steny", style = MaterialTheme.typography.titleMedium)

            LabeledRow(label = "Dĺžka") {
                val sourceLabel = if (wall.source == MeasurementSource.DISTO_X4) " · X4" else ""
                Text("%.2f m%s".format(wall.lengthM, sourceLabel))
            }
            WallLengthEditor(
                currentLengthM = wall.lengthM,
                liveDistanceM = liveDistanceM,
                onConfirm = onLengthChange
            )

            LabeledRow(label = "Hrúbka") {
                val cm = (wall.thicknessM * 100).roundToInt()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { onThicknessChange((cm - 1).coerceAtLeast(0).toDouble()) }) {
                        Icon(Icons.Outlined.Remove, contentDescription = "Znížiť hrúbku")
                    }
                    Text("$cm cm")
                    IconButton(onClick = { onThicknessChange((cm + 1).toDouble()) }) {
                        Icon(Icons.Outlined.Add, contentDescription = "Zvýšiť hrúbku")
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    OutlinedButton(
                        onClick = { liveDistanceM?.let { m -> onThicknessChange((m * 100).roundToInt().toDouble()) } },
                        enabled = liveDistanceM != null
                    ) { Text("X4") }
                }
            }

            LabeledRow(label = "Strana hrúbky") {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(selected = wall.side == WallSide.INSIDE, onClick = { onSideChange(WallSide.INSIDE) }, label = { Text("Dnu") })
                    FilterChip(selected = wall.side == WallSide.OUTSIDE, onClick = { onSideChange(WallSide.OUTSIDE) }, label = { Text("Von") })
                    FilterChip(selected = wall.side == WallSide.CENTER, onClick = { onSideChange(WallSide.CENTER) }, label = { Text("Stred") })
                }
            }

            LabeledRow(label = "Typ") {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(selected = wall.type == WallType.LOAD_BEARING, onClick = { onTypeChange(WallType.LOAD_BEARING) }, label = { Text("Nosná") })
                    FilterChip(selected = wall.type == WallType.PARTITION, onClick = { onTypeChange(WallType.PARTITION) }, label = { Text("Priečka") })
                }
            }

            if (wall.openings.isNotEmpty()) {
                Text("Otvory: ${wall.openings.size}", style = MaterialTheme.typography.bodyLarge)
            }

            Button(onClick = onAddOpeningClick, modifier = Modifier.fillMaxWidth()) {
                Text("Pridať okno / dvere")
            }
        }
    }
}

/** Text field + "X4" quick-fill + confirm — lets you re-measure an already-drawn wall's length
 *  by standing at its far corner and firing the laser, instead of dragging a corner by hand. */
@Composable
private fun WallLengthEditor(
    currentLengthM: Double,
    liveDistanceM: Double?,
    onConfirm: (m: Double, source: MeasurementSource) -> Unit
) {
    var lengthText by remember(currentLengthM) { mutableStateOf("%.2f".format(currentLengthM)) }
    var usedLive by remember(currentLengthM) { mutableStateOf(false) }

    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = lengthText,
            onValueChange = { lengthText = it; usedLive = false },
            label = { Text("Nová dĺžka (m)") },
            modifier = Modifier.weight(1f)
        )
        OutlinedButton(
            onClick = { liveDistanceM?.let { m -> lengthText = "%.2f".format(m); usedLive = true } },
            enabled = liveDistanceM != null
        ) { Text("X4") }
        IconButton(
            onClick = {
                val m = lengthText.toDoubleOrNull()
                if (m != null && m > 0.0) {
                    onConfirm(m, if (usedLive) MeasurementSource.DISTO_X4 else MeasurementSource.MANUAL)
                }
            }
        ) {
            Icon(Icons.Outlined.Check, contentDescription = "Potvrdiť novú dĺžku")
        }
    }
}

@Composable
internal fun LabeledRow(label: String, content: @Composable () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        content()
    }
}
