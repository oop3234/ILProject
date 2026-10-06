package indoor

import indoor.localization.coordinates.HeadingToMapConverter
import indoor.localization.coordinates.MapDisplacement
import indoor.map.HeadingAxes
import indoor.map.MapMetadata
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.sqrt

/** Metadata is loaded from the real JSON by CoreTests; all other maps here are explicit fixtures. */
fun runHeadingToMapConverterTests(metadata: MapMetadata, test: (String, () -> Unit) -> Unit) {
    val converter = HeadingToMapConverter(metadata)
    fun near(actual: Double, expected: Double) {
        check(abs(actual - expected) <= 1e-12) { "Expected $expected, got $actual" }
    }
    fun displacement(heading: Double, distance: Double, dx: Double, dy: Double) {
        val result = converter.toDisplacement(heading, distance)
        near(result.dx, dx)
        near(result.dy, dy)
    }
    fun rejects(action: () -> Unit) {
        check(runCatching(action).exceptionOrNull() is IllegalArgumentException)
    }
    test("heading 300 projects to positive X") { displacement(300.0, 2.0, 2.0, 0.0) }
    test("heading 120 projects to negative X") { displacement(120.0, 2.0, -2.0, 0.0) }
    test("heading 210 projects to positive Y") { displacement(210.0, 2.0, 0.0, 2.0) }
    test("heading 30 projects to negative Y") { displacement(30.0, 2.0, 0.0, -2.0) }
    test("intermediate headings cover all four quadrants") {
        displacement(255.0, sqrt(2.0), 1.0, 1.0)
        displacement(165.0, sqrt(2.0), -1.0, 1.0)
        displacement(75.0, sqrt(2.0), -1.0, -1.0)
        displacement(345.0, sqrt(2.0), 1.0, -1.0)
        displacement(270.0, 2.0, sqrt(3.0), 1.0)
    }
    test("heading wraps including negative and multiple turns") {
        listOf(-60.0, 660.0, 1380.0, -780.0).forEach { displacement(it, 2.0, 2.0, 0.0) }
        listOf(0.0, 360.0, 720.0, -360.0).forEach { displacement(it, 2.0, 1.0, -sqrt(3.0)) }
        near(HeadingToMapConverter.normalizeHeading(-105.0), 255.0)
        listOf(-Double.MIN_VALUE, -0.0, Double.MAX_VALUE, -Double.MAX_VALUE).forEach {
            val normalized = HeadingToMapConverter.normalizeHeading(it)
            check(normalized >= 0.0 && normalized < 360.0)
        }
    }
    test("zero distance is exactly zero for every direction") {
        listOf(300.0, 120.0, 210.0, 30.0, 255.0, -105.0).forEach {
            check(converter.toDisplacement(it, 0.0) == MapDisplacement(0.0, 0.0))
            check(converter.toDisplacement(it, -0.0) == MapDisplacement(0.0, 0.0))
        }
    }
    test("projection preserves distance throughout full turn") {
        for (heading in 0 until 360) {
            val result = converter.toDisplacement(heading.toDouble(), 3.7)
            near(hypot(result.dx, result.dy), 3.7)
        }
    }
    test("nonfinite heading and invalid distance rejected") {
        listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY).forEach {
            rejects { converter.toDisplacement(it, 1.0) }
            rejects { converter.toDisplacement(it, 0.0) }
            rejects { converter.toDisplacement(300.0, it) }
        }
        rejects { converter.toDisplacement(300.0, -1.0) }
    }
    test("alternate map axes are used without floor-specific constants") {
        val changed = metadata.copy(coordinateSystem = metadata.coordinateSystem.copy(
            axes = HeadingAxes(90.0, 270.0, 0.0, 180.0)))
        val result = HeadingToMapConverter(changed).toDisplacement(45.0, sqrt(2.0))
        near(result.dx, 1.0)
        near(result.dy, 1.0)
    }
    test("inconsistent map axes rejected") {
        val axes = metadata.coordinateSystem.axes
        listOf(axes.copy(negativeXHeading = 121.0), axes.copy(negativeYHeading = 31.0),
            HeadingAxes(300.0, 120.0, 200.0, 20.0), axes.copy(positiveXHeading = Double.NaN),
            axes.copy(positiveXHeading = 360.0)).forEach {
            rejects { HeadingToMapConverter(metadata.copy(coordinateSystem = metadata.coordinateSystem.copy(axes = it))) }
        }
    }
    test("incompatible units and heading convention rejected") {
        rejects { HeadingToMapConverter(metadata.copy(unit = "centimeter")) }
        val system = metadata.coordinateSystem
        listOf(system.copy(headingReference = "true_north"), system.copy(headingUnit = "radian"),
            system.copy(headingConvention = "counterclockwise"), system.copy(headingRange = "[-180, 180)")).forEach {
            rejects { HeadingToMapConverter(metadata.copy(coordinateSystem = it)) }
        }
    }
}
