package indoor.localization.coordinates

import indoor.map.MapMetadata
import kotlin.math.abs
import kotlin.math.cos

/** A relative movement in map axes, in meters; not an absolute position or anchor correction. */
data class MapDisplacement(val dx: Double, val dy: Double)

/**
 * Stateless projection of magnetic-north clockwise heading onto orthogonal map axes.
 * Axis angles come from map metadata; no floor-specific angles or Android dependencies.
 * The input heading must describe movement direction, not an uncorrected phone orientation.
 */
class HeadingToMapConverter(metadata: MapMetadata) {
    private val axes = metadata.coordinateSystem.axes

    init {
        val system = metadata.coordinateSystem
        require(metadata.unit == "meter") { "Map unit must be meter" }
        require(system.headingReference == "magnetic_north") { "Expected magnetic_north heading reference" }
        require(system.headingUnit == "degree") { "Expected degree heading unit" }
        require(system.headingConvention == "clockwise_from_north") { "Expected clockwise_from_north convention" }
        require(system.headingRange == "[0, 360)") { "Expected [0, 360) map heading range" }
        listOf(axes.positiveXHeading, axes.negativeXHeading, axes.positiveYHeading, axes.negativeYHeading)
            .forEach { require(it.isFinite() && it >= 0.0 && it < 360.0) { "Map axis headings must be in [0, 360)" } }
        require(abs(separation(axes.positiveXHeading, axes.negativeXHeading) - 180.0) <= AXIS_TOLERANCE_DEGREES) {
            "Negative X must oppose positive X"
        }
        require(abs(separation(axes.positiveYHeading, axes.negativeYHeading) - 180.0) <= AXIS_TOLERANCE_DEGREES) {
            "Negative Y must oppose positive Y"
        }
        require(abs(separation(axes.positiveXHeading, axes.positiveYHeading) - 90.0) <= AXIS_TOLERANCE_DEGREES) {
            "Map X and Y axes must be orthogonal"
        }
    }

    /**
     * headingDegrees is normalized to [0, 360); distanceMeters must be finite and nonnegative.
     * Zero distance returns exactly (0, 0). Other results retain normal floating-point roundoff.
     * Invalid inputs throw IllegalArgumentException, including invalid heading at zero distance.
     */
    fun toDisplacement(headingDegrees: Double, distanceMeters: Double): MapDisplacement {
        val heading = normalizeHeading(headingDegrees)
        require(distanceMeters.isFinite() && distanceMeters >= 0.0) { "Distance must be finite and nonnegative" }
        if (distanceMeters == 0.0) return MapDisplacement(0.0, 0.0)
        return MapDisplacement(
            distanceMeters * cos(Math.toRadians(heading - axes.positiveXHeading)),
            distanceMeters * cos(Math.toRadians(heading - axes.positiveYHeading)),
        )
    }

    companion object {
        // Numerical tolerance for axis consistency, not a sensor calibration threshold.
        private const val AXIS_TOLERANCE_DEGREES = 1e-9

        fun normalizeHeading(headingDegrees: Double): Double {
            require(headingDegrees.isFinite()) { "Heading must be finite" }
            val remainder = headingDegrees % 360.0
            val normalized = if (remainder < 0.0) remainder + 360.0 else remainder
            // Addition may round a tiny negative angle to 360; also canonicalize negative zero.
            return if (normalized == 0.0 || normalized >= 360.0) 0.0 else normalized
        }

        private fun separation(a: Double, b: Double): Double {
            val difference = normalizeHeading(a - b)
            return minOf(difference, 360.0 - difference)
        }
    }
}
