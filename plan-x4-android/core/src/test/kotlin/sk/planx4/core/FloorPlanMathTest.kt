package sk.planx4.core

import sk.planx4.core.geometry.FloorPlanMath
import sk.planx4.core.geometry.Point
import sk.planx4.core.model.MeasurementSource
import sk.planx4.core.model.RoomPlan
import sk.planx4.core.model.Wall
import sk.planx4.core.model.WallSide
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

private const val EPS = 1e-6

private fun assertNear(expected: Double, actual: Double, label: String) {
    assertTrue(abs(expected - actual) < 1e-4, "$label: expected $expected, got $actual")
}

/** A 4m x 3m rectangle, corners walked counter-clockwise, one wall per side. */
private fun rectangleRoom(thicknessM: Double, side: WallSide): RoomPlan {
    val corners = listOf(
        Point(0.0, 0.0),
        Point(4.0, 0.0),
        Point(4.0, 3.0),
        Point(0.0, 3.0)
    )
    val walls = corners.indices.map { i ->
        Wall(
            id = "w$i",
            start = corners[i],
            end = corners[(i + 1) % corners.size],
            thicknessM = thicknessM,
            side = side,
            source = MeasurementSource.DISTO_X4
        )
    }
    return RoomPlan(id = "r1", name = "Test izba", walls = walls)
}

class FloorPlanMathTest {

    @Test
    fun `polygon area of a plain rectangle matches width times height`() {
        val points = listOf(Point(0.0, 0.0), Point(4.0, 0.0), Point(4.0, 3.0), Point(0.0, 3.0))
        assertNear(12.0, FloorPlanMath.polygonAreaM2(points), "rectangle area")
        assertNear(14.0, FloorPlanMath.polygonPerimeterM(points), "rectangle perimeter")
    }

    @Test
    fun `OUTSIDE wall side keeps the drawn line as the net (interior) polygon`() {
        // Drawn line = interior face; thickness is added outward.
        val room = rectangleRoom(thicknessM = 0.30, side = WallSide.OUTSIDE)
        assertNear(12.0, FloorPlanMath.netAreaM2(room), "net area (OUTSIDE)")
        // Gross footprint grows by the thickness on every side: (4+0.6) x (3+0.6)
        assertNear(4.6 * 3.6, FloorPlanMath.grossAreaM2(room), "gross area (OUTSIDE)")
    }

    @Test
    fun `INSIDE wall side keeps the drawn line as the gross (exterior) polygon`() {
        // Drawn line = exterior face; thickness is added inward.
        val room = rectangleRoom(thicknessM = 0.30, side = WallSide.INSIDE)
        assertNear(12.0, FloorPlanMath.grossAreaM2(room), "gross area (INSIDE)")
        // Net usable area shrinks by the thickness on every side: (4-0.6) x (3-0.6)
        assertNear(3.4 * 2.4, FloorPlanMath.netAreaM2(room), "net area (INSIDE)")
    }

    @Test
    fun `CENTER wall side splits the thickness evenly both ways`() {
        val room = rectangleRoom(thicknessM = 0.30, side = WallSide.CENTER)
        // Half the thickness (0.15 m) is removed on every side for the net polygon...
        assertNear(3.7 * 2.7, FloorPlanMath.netAreaM2(room), "net area (CENTER)")
        // ...and added on every side for the gross polygon.
        assertNear(4.3 * 3.3, FloorPlanMath.grossAreaM2(room), "gross area (CENTER)")
    }

    @Test
    fun `mixed wall sides on the same room offset independently`() {
        // Only the left wall (index 3, from (0,3) to (0,0)) gets a thick OUTSIDE wall;
        // the rest stay thin. Net area should shrink only on that one side.
        val corners = listOf(Point(0.0, 0.0), Point(4.0, 0.0), Point(4.0, 3.0), Point(0.0, 3.0))
        val thicknesses = listOf(0.0, 0.0, 0.0, 0.40)
        val walls = corners.indices.map { i ->
            Wall(
                id = "w$i",
                start = corners[i],
                end = corners[(i + 1) % corners.size],
                thicknessM = thicknesses[i],
                side = WallSide.OUTSIDE
            )
        }
        val room = RoomPlan("r2", "Mixed", walls)
        // Only the left edge moves outward (in -x direction) by 0.40 m -> width grows to 4.40.
        assertNear(4.40 * 3.0, FloorPlanMath.grossAreaM2(room), "gross area (mixed)")
        assertNear(12.0, FloorPlanMath.netAreaM2(room), "net area (mixed, unchanged)")
    }

    @Test
    fun `opening must fit within its wall`() {
        val wall = Wall(
            id = "w0",
            start = Point(0.0, 0.0),
            end = Point(4.0, 0.0),
            thicknessM = 0.30,
            side = WallSide.OUTSIDE
        )
        val ok = sk.planx4.core.model.Opening(
            id = "o1", wallId = "w0",
            type = sk.planx4.core.model.OpeningType.WINDOW,
            offsetM = 0.86, widthM = 1.20, heightM = 1.40, sillM = 0.90,
            source = MeasurementSource.DISTO_X4
        )
        assertTrue(ok.fitsWithin(wall.lengthM))
        val tooWide = ok.copy(id = "o2", widthM = 10.0)
        assertTrue(!tooWide.fitsWithin(wall.lengthM))
    }
}
