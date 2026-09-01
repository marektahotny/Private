package sk.planx4.app.ui.editor

import androidx.compose.ui.geometry.Offset
import sk.planx4.core.geometry.Point

/**
 * Maps model-space meters (Y "up", see [sk.planx4.core.geometry.Point]) to Compose's
 * screen-space pixels (Y down), with a pan offset and uniform scale.
 */
data class ViewTransform(val pixelsPerMeter: Float, val panPx: Offset) {
    fun toScreen(p: Point): Offset =
        Offset(p.x.toFloat() * pixelsPerMeter, -p.y.toFloat() * pixelsPerMeter) + panPx

    fun toModel(offset: Offset): Point {
        val unpanned = offset - panPx
        return Point((unpanned.x / pixelsPerMeter).toDouble(), (-unpanned.y / pixelsPerMeter).toDouble())
    }

    companion object {
        /** Fits every wall endpoint inside [canvasSize] with [paddingPx] of breathing room. */
        fun fitting(points: List<Point>, canvasWidth: Float, canvasHeight: Float, paddingPx: Float = 48f): ViewTransform {
            if (points.isEmpty()) return ViewTransform(60f, Offset(canvasWidth / 2f, canvasHeight / 2f))
            val minX = points.minOf { it.x }
            val maxX = points.maxOf { it.x }
            val minY = points.minOf { it.y }
            val maxY = points.maxOf { it.y }
            val spanX = (maxX - minX).coerceAtLeast(0.5)
            val spanY = (maxY - minY).coerceAtLeast(0.5)
            val availableW = (canvasWidth - 2 * paddingPx).coerceAtLeast(1f)
            val availableH = (canvasHeight - 2 * paddingPx).coerceAtLeast(1f)
            val scale = minOf(availableW / spanX.toFloat(), availableH / spanY.toFloat()).coerceIn(8f, 300f)
            val centerModel = Point((minX + maxX) / 2.0, (minY + maxY) / 2.0)
            val centerScreen = Offset(canvasWidth / 2f, canvasHeight / 2f)
            // pan so that centerModel maps to centerScreen: centerScreen = toScreen(centerModel) with panPx=0, then solve.
            val panPx = centerScreen - Offset(centerModel.x.toFloat() * scale, -centerModel.y.toFloat() * scale)
            return ViewTransform(scale, panPx)
        }
    }
}
