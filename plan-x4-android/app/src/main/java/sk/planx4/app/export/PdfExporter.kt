package sk.planx4.app.export

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import java.io.File
import java.io.FileOutputStream
import sk.planx4.core.geometry.FloorPlanMath
import sk.planx4.core.geometry.Point
import sk.planx4.core.model.Project
import sk.planx4.core.model.RoomPlan

private const val PAGE_WIDTH = 595 // A4 @ 72dpi
private const val PAGE_HEIGHT = 842
private const val MARGIN = 40f

/**
 * MVP export: one PDF page per room, drawn net (interior-face) polygon plus its area/perimeter,
 * and a summary page. DXF/CAD export is intentionally out of scope for the MVP (concept doc,
 * section 04) — this is the plain "print or share a PDF" path only.
 */
object PdfExporter {

    fun export(project: Project, outputFile: File) {
        val document = PdfDocument()

        drawSummaryPage(document, project)
        project.rooms.filter { it.isClosed }.forEach { room -> drawRoomPage(document, room) }

        FileOutputStream(outputFile).use { document.writeTo(it) }
        document.close()
    }

    private fun drawSummaryPage(document: PdfDocument, project: Project) {
        val page = document.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, document.pages.size + 1).create())
        val canvas = page.canvas
        val titlePaint = Paint().apply { color = Color.BLACK; textSize = 20f; isFakeBoldText = true }
        val bodyPaint = Paint().apply { color = Color.DKGRAY; textSize = 12f }

        canvas.drawText(project.name, MARGIN, MARGIN + 20f, titlePaint)

        var y = MARGIN + 60f
        val totalArea = project.rooms.filter { it.isClosed }.sumOf { FloorPlanMath.netAreaM2(it) }
        canvas.drawText("Miestností: ${project.rooms.size}", MARGIN, y, bodyPaint); y += 18f
        canvas.drawText("Plocha spolu: %.1f m²".format(totalArea), MARGIN, y, bodyPaint); y += 24f

        project.rooms.forEach { room ->
            val line = if (room.isClosed) {
                "${room.name} — %.1f m², obvod %.1f m".format(FloorPlanMath.netAreaM2(room), FloorPlanMath.netPerimeterM(room))
            } else {
                "${room.name} — nedokončené"
            }
            canvas.drawText(line, MARGIN, y, bodyPaint)
            y += 16f
        }

        document.finishPage(page)
    }

    private fun drawRoomPage(document: PdfDocument, room: RoomPlan) {
        val page = document.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, document.pages.size + 1).create())
        val canvas = page.canvas
        val titlePaint = Paint().apply { color = Color.BLACK; textSize = 16f; isFakeBoldText = true }
        val bodyPaint = Paint().apply { color = Color.DKGRAY; textSize = 11f }
        val wallPaint = Paint().apply { color = Color.BLACK; style = Paint.Style.STROKE; strokeWidth = 3f }

        canvas.drawText(room.name, MARGIN, MARGIN + 16f, titlePaint)
        canvas.drawText(
            "%.1f m²  ·  obvod %.1f m".format(FloorPlanMath.netAreaM2(room), FloorPlanMath.netPerimeterM(room)),
            MARGIN, MARGIN + 36f, bodyPaint
        )

        val net = FloorPlanMath.netPolygon(room)
        val available = PAGE_WIDTH - 2 * MARGIN
        val top = MARGIN + 60f
        val availableH = PAGE_HEIGHT - top - MARGIN

        drawPolygon(canvas, net, available, availableH, top, wallPaint)
        document.finishPage(page)
    }

    private fun drawPolygon(canvas: Canvas, points: List<Point>, availableW: Float, availableH: Float, top: Float, paint: Paint) {
        if (points.size < 3) return
        val minX = points.minOf { it.x }
        val maxX = points.maxOf { it.x }
        val minY = points.minOf { it.y }
        val maxY = points.maxOf { it.y }
        val spanX = (maxX - minX).coerceAtLeast(0.1)
        val spanY = (maxY - minY).coerceAtLeast(0.1)
        val scale = minOf(availableW / spanX.toFloat(), availableH / spanY.toFloat())

        fun toX(x: Double) = MARGIN + (x - minX).toFloat() * scale
        fun toY(y: Double) = top + (maxY - y).toFloat() * scale // flip Y for screen-down PDF coords

        val path = android.graphics.Path()
        path.moveTo(toX(points[0].x), toY(points[0].y))
        for (i in 1 until points.size) path.lineTo(toX(points[i].x), toY(points[i].y))
        path.close()
        canvas.drawPath(path, paint)
    }
}
