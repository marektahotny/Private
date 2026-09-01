package sk.planx4.app.ui.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.atan2
import kotlin.math.max
import sk.planx4.core.geometry.Point
import sk.planx4.core.model.OpeningType
import sk.planx4.core.model.Wall

private const val VERTEX_HIT_RADIUS_PX = 40f
private const val WALL_HIT_DISTANCE_PX = 24f
private const val MIN_WALL_STROKE_PX = 6f

/**
 * The floor plan drawing/editing surface (concept doc, section 07, "Plátno na kreslenie").
 *
 * DRAW mode: drag from empty canvas anywhere and release to append the next wall — if the
 * DISTO has reported a live distance, only the drag *direction* is used (the length comes
 * from the X4); otherwise the drag's on-screen length is used directly (manual entry).
 *
 * EDIT mode: drag an existing corner to reshape the room; tap a wall to select it (for the
 * properties panel) — see [FloorPlanEditorScreen].
 */
@Composable
fun FloorPlanCanvas(
    state: EditorUiState,
    onAddManualPoint: (Point) -> Unit,
    onAddFromDirection: (angleDeg: Double) -> Unit,
    onMoveVertex: (index: Int, Point) -> Unit,
    onSelectWall: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    val wallColor = MaterialTheme.colorScheme.onBackground
    val accent = MaterialTheme.colorScheme.primary
    val accent2 = MaterialTheme.colorScheme.secondary
    val backgroundColor = MaterialTheme.colorScheme.surfaceVariant
    val faintColor = MaterialTheme.colorScheme.onSurfaceVariant

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val widthPx = constraints.maxWidth.toFloat()
        val heightPx = constraints.maxHeight.toFloat()

        val allPoints = remember(state.room.walls) {
            state.room.walls.map { it.start } + listOfNotNull(state.room.walls.lastOrNull()?.end)
        }
        val transform = remember(allPoints, widthPx, heightPx) {
            ViewTransform.fitting(allPoints, widthPx, heightPx)
        }

        var draggingVertexIndex by remember { mutableStateOf<Int?>(null) }
        var dragPreviewEnd by remember { mutableStateOf<Offset?>(null) }

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(state.mode, state.room.walls.size) {
                    if (state.mode == EditorMode.EDIT) {
                        detectDragGestures(
                            onDragStart = { start ->
                                draggingVertexIndex = nearestVertexIndex(state.room.walls, transform, start)
                            },
                            onDragEnd = { draggingVertexIndex = null },
                            onDragCancel = { draggingVertexIndex = null }
                        ) { change, _ ->
                            val index = draggingVertexIndex ?: return@detectDragGestures
                            onMoveVertex(index, transform.toModel(change.position))
                        }
                    } else {
                        detectDragGestures(
                            onDragStart = { start -> dragPreviewEnd = start },
                            onDragEnd = {
                                val end = dragPreviewEnd
                                dragPreviewEnd = null
                                val lastPoint = state.room.walls.lastOrNull()?.end ?: Point(0.0, 0.0)
                                if (end != null && !state.closed) {
                                    if (state.liveDistanceM != null) {
                                        val target = transform.toModel(end)
                                        val dx = target.x - lastPoint.x
                                        val dy = target.y - lastPoint.y
                                        val angleDeg = Math.toDegrees(atan2(dy, dx))
                                        onAddFromDirection(angleDeg)
                                    } else {
                                        onAddManualPoint(transform.toModel(end))
                                    }
                                }
                            },
                            onDragCancel = { dragPreviewEnd = null }
                        ) { change, _ -> dragPreviewEnd = change.position }
                    }
                }
                .pointerInput(state.mode, state.room.walls) {
                    if (state.mode == EditorMode.EDIT) {
                        detectTapSelectWall(state.room.walls, transform) { onSelectWall(it) }
                    }
                }
        ) {
            drawGrid(faintColor.copy(alpha = 0.15f))

            state.room.walls.forEach { wall ->
                drawWall(
                    wall = wall,
                    transform = transform,
                    color = if (wall.id == state.selectedWallId) accent else wallColor,
                    openingGapColor = backgroundColor,
                    windowColor = accent2
                )
            }

            // Corner handles.
            state.room.walls.forEachIndexed { index, wall ->
                val p = transform.toScreen(wall.start)
                drawCircle(
                    color = if (state.mode == EditorMode.EDIT) accent else wallColor,
                    radius = if (draggingVertexIndex == index) 9f else 5f,
                    center = p
                )
            }
            if (!state.closed) {
                state.room.walls.lastOrNull()?.let {
                    drawCircle(color = wallColor, radius = 5f, center = transform.toScreen(it.end))
                }
            }

            // Live preview line while dragging in DRAW mode.
            if (state.mode == EditorMode.DRAW) {
                dragPreviewEnd?.let { end ->
                    val lastPoint = state.room.walls.lastOrNull()?.end ?: Point(0.0, 0.0)
                    drawLine(
                        color = accent2,
                        start = transform.toScreen(lastPoint),
                        end = end,
                        strokeWidth = 3f,
                        cap = StrokeCap.Round
                    )
                }
            }
        }
    }
}

private fun nearestVertexIndex(walls: List<Wall>, transform: ViewTransform, screenPoint: Offset): Int? {
    var bestIndex: Int? = null
    var bestDistance = VERTEX_HIT_RADIUS_PX
    walls.forEachIndexed { index, wall ->
        val d = (transform.toScreen(wall.start) - screenPoint).getDistance()
        if (d < bestDistance) {
            bestDistance = d
            bestIndex = index
        }
    }
    return bestIndex
}

private suspend fun PointerInputScope.detectTapSelectWall(
    walls: List<Wall>,
    transform: ViewTransform,
    onSelect: (String?) -> Unit
) {
    detectTapGestures { tapOffset ->
        val hit = walls.minByOrNull { wall ->
            distancePointToSegment(tapOffset, transform.toScreen(wall.start), transform.toScreen(wall.end))
        }
        if (hit != null && distancePointToSegment(tapOffset, transform.toScreen(hit.start), transform.toScreen(hit.end)) <= WALL_HIT_DISTANCE_PX) {
            onSelect(hit.id)
        } else {
            onSelect(null)
        }
    }
}

private fun distancePointToSegment(p: Offset, a: Offset, b: Offset): Float {
    val ab = b - a
    val abLenSq = ab.x * ab.x + ab.y * ab.y
    if (abLenSq < 1e-6f) return (p - a).getDistance()
    val t = (((p.x - a.x) * ab.x + (p.y - a.y) * ab.y) / abLenSq).coerceIn(0f, 1f)
    val closest = Offset(a.x + ab.x * t, a.y + ab.y * t)
    return (p - closest).getDistance()
}

private fun DrawScope.drawGrid(color: Color) {
    val stepPx = 40f
    var x = 0f
    while (x < size.width) {
        drawLine(color, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f)
        x += stepPx
    }
    var y = 0f
    while (y < size.height) {
        drawLine(color, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
        y += stepPx
    }
}

private fun DrawScope.drawWall(
    wall: Wall,
    transform: ViewTransform,
    color: Color,
    openingGapColor: Color,
    windowColor: Color
) {
    val start = transform.toScreen(wall.start)
    val end = transform.toScreen(wall.end)
    val strokeWidth = max(wall.thicknessM.toFloat() * transform.pixelsPerMeter, MIN_WALL_STROKE_PX)

    drawLine(color, start, end, strokeWidth = strokeWidth, cap = StrokeCap.Butt)

    if (wall.openings.isEmpty() || wall.lengthM < 1e-6) return

    val dir = Offset((end.x - start.x), (end.y - start.y))
    val lengthPx = dir.getDistance()
    if (lengthPx < 1f) return
    val unit = Offset(dir.x / lengthPx, dir.y / lengthPx)
    val pxPerMeter = lengthPx / wall.lengthM.toFloat()

    wall.openings.forEach { opening ->
        val gapStartPx = opening.offsetM.toFloat() * pxPerMeter
        val gapEndPx = (opening.offsetM + opening.widthM).toFloat() * pxPerMeter
        val gapStart = Offset(start.x + unit.x * gapStartPx, start.y + unit.y * gapStartPx)
        val gapEnd = Offset(start.x + unit.x * gapEndPx, start.y + unit.y * gapEndPx)

        // Erase the wall along the opening span, then draw a type-specific symbol on top.
        drawLine(openingGapColor, gapStart, gapEnd, strokeWidth = strokeWidth + 2f, cap = StrokeCap.Butt)

        when (opening.type) {
            OpeningType.WINDOW -> {
                val normal = Offset(-unit.y, unit.x)
                val half = strokeWidth / 2.4f
                drawLine(windowColor, gapStart + normal * half, gapEnd + normal * half, strokeWidth = 2f)
                drawLine(windowColor, gapStart - normal * half, gapEnd - normal * half, strokeWidth = 2f)
            }
            OpeningType.DOOR -> {
                // Hinge at the near jamb (gapStart); leaf swings a quarter turn from lying
                // along the wall (toward gapEnd) to standing perpendicular into the room.
                val radiusPx = gapEndPx - gapStartPx
                val normal = Offset(-unit.y, unit.x)
                val startAngleDeg = Math.toDegrees(atan2(unit.y.toDouble(), unit.x.toDouble())).toFloat()
                drawArc(
                    color = color.copy(alpha = 0.55f),
                    startAngle = startAngleDeg,
                    sweepAngle = 90f,
                    useCenter = false,
                    style = Stroke(width = 1.5f),
                    topLeft = Offset(gapStart.x - radiusPx, gapStart.y - radiusPx),
                    size = Size(radiusPx * 2f, radiusPx * 2f)
                )
                drawLine(color, gapStart, gapStart + normal * radiusPx, strokeWidth = 2f)
            }
        }
    }
}
