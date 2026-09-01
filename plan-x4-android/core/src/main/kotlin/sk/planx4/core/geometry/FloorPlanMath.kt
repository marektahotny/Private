package sk.planx4.core.geometry

import sk.planx4.core.model.RoomPlan
import kotlin.math.abs

object FloorPlanMath {

    /** Shoelace formula. `points` must form a simple (non-self-intersecting) closed polygon. */
    fun polygonAreaM2(points: List<Point>): Double {
        if (points.size < 3) return 0.0
        var sum = 0.0
        for (i in points.indices) {
            val a = points[i]
            val b = points[(i + 1) % points.size]
            sum += a.x * b.y - b.x * a.y
        }
        return abs(sum) / 2.0
    }

    fun polygonPerimeterM(points: List<Point>): Double {
        if (points.size < 2) return 0.0
        var sum = 0.0
        for (i in points.indices) {
            sum += distanceBetween(points[i], points[(i + 1) % points.size])
        }
        return sum
    }

    /**
     * True if `points`, taken in order, run clockwise in a standard (x right, y up) plane.
     * Uses the standard "shoelace sign" trick: sum of (x2-x1)(y2+y1) is positive for a
     * clockwise polygon and negative for counter-clockwise.
     */
    internal fun isClockwise(points: List<Point>): Boolean {
        var sum = 0.0
        for (i in points.indices) {
            val a = points[i]
            val b = points[(i + 1) % points.size]
            sum += (b.x - a.x) * (b.y + a.y)
        }
        return sum > 0.0
    }

    /**
     * Offsets every edge of a closed polygon outward (away from the polygon's own interior)
     * by `edgeOffsetsM[i]` — one value per edge, where edge `i` runs from `points[i]` to
     * `points[(i+1) % n]` — then rebuilds each corner as the intersection of its two
     * neighbouring offset edge lines (a simple mitre join).
     *
     * A positive offset pushes that edge outward; negative pulls it inward. This is used to
     * turn the drawn wall centerlines/faces into the net (interior-face) and gross
     * (exterior-face) room polygons — see [Wall.netEdgeOffsetM]/[Wall.grossEdgeOffsetM].
     *
     * Known simplification: this is a plain per-edge mitre offset, not a full polygon-offset
     * algorithm — it doesn't clip or handle self-intersection on very acute corners or
     * offsets larger than an adjacent edge. Fine for ordinary room shapes; revisit if rooms
     * turn out to have very sharp angles or tiny alcoves.
     */
    fun offsetPolygon(points: List<Point>, edgeOffsetsM: List<Double>): List<Point> {
        require(points.size == edgeOffsetsM.size) {
            "Potrebujem presne jeden offset na každú hranu (${points.size} bodov, ${edgeOffsetsM.size} offsetov)"
        }
        val n = points.size
        if (n < 3) return points

        val clockwise = isClockwise(points)

        data class Line(val point: Point, val direction: Point)

        val offsetLines = (0 until n).map { i ->
            val a = points[i]
            val b = points[(i + 1) % n]
            val dir = (b - a).normalized()
            // perpendicular() is a +90° (CCW) turn, which points into the interior for a
            // CCW polygon and out of it for a CW one — so we only need to flip for CCW.
            var outward = dir.perpendicular()
            if (!clockwise) outward = outward.scaled(-1.0)
            Line(a + outward.scaled(edgeOffsetsM[i]), dir)
        }

        fun intersect(l1: Line, l2: Line): Point {
            val denom = l1.direction.x * l2.direction.y - l1.direction.y * l2.direction.x
            if (abs(denom) < 1e-9) return l1.point // parallel edges (a straight run split in two) — no real corner
            val diff = l2.point - l1.point
            val t = (diff.x * l2.direction.y - diff.y * l2.direction.x) / denom
            return l1.point + l1.direction.scaled(t)
        }

        return (0 until n).map { i ->
            intersect(offsetLines[(i - 1 + n) % n], offsetLines[i])
        }
    }

    /** The interior-face polygon of a room — what you'd report as its usable floor area. */
    fun netPolygon(room: RoomPlan): List<Point> =
        offsetPolygon(room.walls.map { it.start }, room.walls.map { it.netEdgeOffsetM() })

    /** The exterior-face polygon of a room — footprint including the walls themselves. */
    fun grossPolygon(room: RoomPlan): List<Point> =
        offsetPolygon(room.walls.map { it.start }, room.walls.map { it.grossEdgeOffsetM() })

    fun netAreaM2(room: RoomPlan): Double = polygonAreaM2(netPolygon(room))
    fun grossAreaM2(room: RoomPlan): Double = polygonAreaM2(grossPolygon(room))
    fun netPerimeterM(room: RoomPlan): Double = polygonPerimeterM(netPolygon(room))
}
