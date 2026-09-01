package sk.planx4.core.geometry

import kotlinx.serialization.Serializable
import kotlin.math.sqrt

/**
 * A 2D point/vector in meters, in plan (top-down) coordinates.
 * The math in [sk.planx4.core.geometry.FloorPlanMath] assumes a standard
 * right-handed Cartesian plane (x right, y "up" in the mathematical sense) —
 * it does not matter which physical direction that corresponds to on screen,
 * only that it's used consistently.
 */
@Serializable
data class Point(val x: Double, val y: Double) {
    operator fun plus(other: Point) = Point(x + other.x, y + other.y)
    operator fun minus(other: Point) = Point(x - other.x, y - other.y)
    fun scaled(factor: Double) = Point(x * factor, y * factor)
    fun length(): Double = sqrt(x * x + y * y)

    fun normalized(): Point {
        val len = length()
        return if (len < 1e-9) Point(0.0, 0.0) else Point(x / len, y / len)
    }

    /** Rotates this vector +90° (counter-clockwise in a standard y-up plane). */
    fun perpendicular(): Point = Point(-y, x)
}

fun distanceBetween(a: Point, b: Point): Double = (b - a).length()
