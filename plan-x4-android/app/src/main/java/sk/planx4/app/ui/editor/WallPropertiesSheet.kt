package sk.planx4.app.ui.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import sk.planx4.core.model.MeasurementSource
import sk.planx4.core.model.Wall
import sk.planx4.core.model.WallSide
import sk.planx4.core.model.WallType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WallPropertiesSheet(
    wall: Wall,
    sheetState: SheetState,
    onDismiss: () -> Unit,
    onThicknessChange: (cm: Double) -> Unit,
    onSideChange: (WallSide) -> Unit,
    onTypeChange: (WallType) -> Unit,
    onAddOpeningClick: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Vlastnosti steny", style = androidx.compose.material3.MaterialTheme.typography.titleMedium)

            LabeledRow(label = "Dĺžka") {
                val sourceLabel = if (wall.source == MeasurementSource.DISTO_X4) " · X4 živé" else ""
                Text("%.2f m%s".format(wall.lengthM, sourceLabel))
            }

            LabeledRow(label = "Hrúbka") {
                val cm = (wall.thicknessM * 100).toInt()
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    IconButton(onClick = { onThicknessChange((cm - 1).coerceAtLeast(0).toDouble()) }) {
                        Icon(Icons.Outlined.Remove, contentDescription = "Znížiť hrúbku")
                    }
                    Text("$cm cm")
                    IconButton(onClick = { onThicknessChange((cm + 1).toDouble()) }) {
                        Icon(Icons.Outlined.Add, contentDescription = "Zvýšiť hrúbku")
                    }
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
                Text("Otvory: ${wall.openings.size}", style = androidx.compose.material3.MaterialTheme.typography.bodyLarge)
            }

            Button(onClick = onAddOpeningClick, modifier = Modifier.fillMaxWidth()) {
                Text("Pridať okno / dvere")
            }
        }
    }
}

@Composable
internal fun LabeledRow(label: String, content: @Composable () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
    ) {
        Text(label, style = androidx.compose.material3.MaterialTheme.typography.bodyLarge)
        content()
    }
}
