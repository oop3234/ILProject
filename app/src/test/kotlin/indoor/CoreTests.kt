package indoor

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import indoor.map.*
import indoor.localization.*
import indoor.localization.pdr.PdrLocalizer
import indoor.localization.visual.VisualLocalizer
import indoor.localization.fusion.PositionFusionEngine
import indoor.localization.matching.MapMatcher
import java.nio.file.Files
import java.nio.file.Path
import kotlin.math.abs
import kotlin.math.hypot

/** Plain JVM runner: failed checks throw and produce a nonzero exit code, without a test framework dependency. */
fun main(args: Array<String>) {
    val path = Path.of(args.single())
    val original = Files.readAllBytes(path)
    val json = Files.readString(path)
    val repo = JsonIndoorMapRepository.load(path)
    var passed = 0
    fun test(name: String, body: () -> Unit) {
        body()
        passed++
        println("PASS $name")
    }
    fun fixture(change: (JsonObject) -> Unit): String = JsonParser.parseString(json).asJsonObject
        .also(change).toString()
    fun rejected(text: String, message: String? = null) {
        val error = runCatching { JsonIndoorMapRepository.load(text.reader()) }.exceptionOrNull()
        check(error is MapFormatException) { "Expected MapFormatException, got $error" }
        if (message != null) check(error.message.orEmpty().contains(message)) { error.message.orEmpty() }
    }
    fun nav(root: JsonObject) = root.getAsJsonObject("navigation")
    fun edge(root: JsonObject) = nav(root).getAsJsonArray("edges")[0].asJsonObject
    fun node(root: JsonObject) = nav(root).getAsJsonArray("nodes")[0].asJsonObject

    test("real map metadata and headings") {
        check(repo.floorMap.schemaVersion == "1.0.0")
        check(repo.floorMap.metadata.floor == 2 && repo.floorMap.metadata.unit == "meter")
        val cs = repo.floorMap.metadata.coordinateSystem
        check(cs.originNodeId == "B0" && cs.origin == Coordinate(0.0, 0.0))
        check(cs.headingReference == "magnetic_north")
        check(cs.axes == HeadingAxes(300.0, 120.0, 210.0, 30.0))
    }
    test("exact node IDs and navigation coordinates") {
        val expected = mapOf("B0" to Coordinate(0.0, -0.975), "B1" to Coordinate(3.775, -0.975),
            "B2" to Coordinate(23.7, -0.975), "B3" to Coordinate(42.6, -0.975),
            "B4" to Coordinate(42.6, -2.25), "B5" to Coordinate(3.775, 4.2))
        check(repo.getNodes().associate { it.id to it.position } == expected)
    }
    test("raw coordinates remain distinct") {
        val expected = mapOf("B0" to Coordinate(0.0, 0.0), "B1" to Coordinate(2.5, 0.0),
            "B2" to Coordinate(23.7, 0.0), "B3" to Coordinate(44.0, 0.0),
            "B4" to Coordinate(44.0, -2.25), "B5" to Coordinate(2.5, 4.2))
        expected.forEach { (id, position) -> check(repo.getMeasuredPosition(id) == position) }
    }
    test("optional node metadata and empty references") {
        check(repo.getNode("B5")!!.roomNumber == "206")
        check(repo.getNode("B0")!!.type == "anchor")
        check(repo.getNode("B2")!!.hasAdditionalBranch == true)
        check(repo.getNode("B2")!!.additionalBranchEnabled == false)
        check(repo.getNodes().all { it.images.isEmpty() })
        check(repo.floorMap.imageMetadataSchema["required"] == listOf("id", "path"))
    }
    test("all edges and incident edge index") {
        check(repo.getEdges().size == 5)
        check(repo.getConnectedEdges("B1").map { it.id }.toSet() == setOf("B0-B1", "B1-B2", "B1-B5"))
    }
    test("actual distances match navigation geometry") {
        repo.getEdges().forEach {
            val a = repo.getPosition(it.from)!!
            val b = repo.getPosition(it.to)!!
            check(abs(hypot(a.x - b.x, a.y - b.y) - it.distance) < 1e-9)
            check(abs((it.heading.forward + 180) % 360 - it.heading.reverse) < 1e-9)
        }
    }
    test("adjacency and both traversal directions") {
        check(repo.areConnected("B0", "B1") && repo.areConnected("B1", "B0"))
        check(repo.canTraverse("B0", "B1") && repo.canTraverse("B1", "B0"))
        check(!repo.areConnected("B0", "B5") && !repo.canTraverse("B0", "B5"))
        check(!repo.areConnected("B0", "B0"))
    }
    test("unknown IDs are absent") {
        check(repo.getNode("unknown") == null && repo.getPosition("unknown") == null)
        check(repo.getMeasuredPosition("unknown") == null && repo.getConnectedEdges("unknown").isEmpty())
        check(!repo.areConnected("unknown", "B0") && !repo.canTraverse("B0", "unknown"))
    }
    test("one-way fixture affects traversal only") {
        val changed = JsonIndoorMapRepository.load(fixture { edge(it).addProperty("bidirectional", false) }.reader())
        check(changed.areConnected("B1", "B0"))
        check(changed.canTraverse("B0", "B1") && !changed.canTraverse("B1", "B0"))
    }
    test("blocked fixture preserves incident edge") {
        val changed = JsonIndoorMapRepository.load(fixture { edge(it).addProperty("walkable", false) }.reader())
        check(changed.areConnected("B0", "B1") && changed.getConnectedEdges("B0").size == 1)
        check(!changed.canTraverse("B0", "B1") && !changed.canTraverse("B1", "B0"))
    }
    test("missing required coordinate has field path") {
        rejected(fixture { node(it).getAsJsonObject("position").remove("x") }, "navigation.nodes[0].position.x")
    }
    test("wrong numeric and boolean types rejected") {
        rejected(fixture { edge(it).addProperty("distance", "3.775") })
        rejected(fixture { edge(it).addProperty("walkable", "true") })
        rejected(fixture { it.getAsJsonObject("metadata").addProperty("floor", 2.5) })
    }
    test("duplicate identifiers rejected") {
        rejected(fixture { nav(it).getAsJsonArray("nodes").add(node(it).deepCopy()) }, "duplicate IDs")
        rejected(fixture { nav(it).getAsJsonArray("edges").add(edge(it).deepCopy()) }, "duplicate IDs")
    }
    test("dangling endpoints and corridors rejected") {
        rejected(fixture { edge(it).addProperty("to", "missing") }, "endpoint")
        rejected(fixture { edge(it).addProperty("corridor_id", "missing") }, "corridor")
        rejected(fixture { it.getAsJsonObject("metadata").getAsJsonObject("coordinate_system").addProperty("origin_node_id", "missing") }, "origin_node_id")
    }
    test("invalid distance and heading rejected") {
        rejected(fixture { edge(it).addProperty("distance", -1) })
        rejected(fixture { edge(it).getAsJsonObject("heading").addProperty("forward", 360) })
        rejected(fixture { node(it).getAsJsonObject("position").addProperty("x", 1e300) }.replace("1.0E300", "1e999"))
    }
    test("version and malformed input rejected") {
        rejected(fixture { it.addProperty("schema_version", "2.0.0") }, "unsupported version")
        rejected("{broken")
        rejected(json + " {}")
        rejected(json.replaceFirst("\"schema_version\"", "schema_version"))
    }
    test("image fixture preserves capture source and extension metadata") {
        val changed = JsonIndoorMapRepository.load(fixture {
            node(it).getAsJsonArray("images").add(JsonParser.parseString("""{
                "id":"test-only", "path":"fixtures/B0.jpg", "heading":300,
                "position_source":"measurement.raw_position", "extra_metadata":{"test":true},
                "future_field":{"value":7}
            }"""))
        }.reader())
        val image = changed.getNode("B0")!!.images.single()
        check(image.heading == 300.0 && image.positionSource == "measurement.raw_position")
        check(image.extraMetadata["test"] == true && image.extensions.containsKey("future_field"))
        check(image.file == null && image.capturedAt == null)
    }
    test("missing image heading/source remain unknown; required path enforced") {
        val minimal = fixture { node(it).getAsJsonArray("images").add(JsonParser.parseString("""{"id":"test", "path":"fixture.jpg"}""")) }
        val image = JsonIndoorMapRepository.load(minimal.reader()).getNode("B0")!!.images.single()
        check(image.heading == null && image.positionSource == null)
        rejected(fixture { node(it).getAsJsonArray("images").add(JsonParser.parseString("""{"id":"test"}""")) }, "path")
    }
    val estimate = PositionEstimate(1.0, 2.0, 2, 300.0, 0.8, 1234L, PositionSource.PDR, PositionReference.MEASUREMENT_RAW)
    test("position estimates preserve conventions and allow unknown heading") {
        check(estimate.copy(heading = null).heading == null)
        check(PositionSource.entries.toSet() == setOf(PositionSource.PDR, PositionSource.VISUAL, PositionSource.FUSED))
        check(estimate.nodeId == null)
    }
    test("invalid position estimates rejected") {
        listOf<() -> PositionEstimate>(
            { estimate.copy(x = Double.NaN) }, { estimate.copy(y = Double.POSITIVE_INFINITY) },
            { estimate.copy(heading = 360.0) }, { estimate.copy(heading = -1.0) },
            { estimate.copy(confidence = 1.1) }, { estimate.copy(confidence = Double.NaN) },
            { estimate.copy(timestamp = -1) }, { estimate.copy(nodeId = " ") },
        ).forEach { check(runCatching(it).exceptionOrNull() is IllegalArgumentException) }
    }
    test("interfaces accept independent test doubles without sensor dependencies") {
        // Wiring-only mocks; these return scripted results, never simulate physical measurements.
        val pdr: PdrLocalizer<Unit> = object : PdrLocalizer<Unit> {
            override fun reset(anchor: PositionEstimate) {}
            override fun update(sample: Unit, timestamp: Long) = estimate.copy(timestamp = timestamp)
        }
        val visual: VisualLocalizer<Unit> = object : VisualLocalizer<Unit> {
            override fun localize(frame: Unit, timestamp: Long): PositionEstimate? = null
        }
        val fusion: PositionFusionEngine = object : PositionFusionEngine {
            override fun update(estimate: PositionEstimate) = estimate.copy(source = PositionSource.FUSED)
            override fun reset() {}
        }
        val matcher: MapMatcher = object : MapMatcher {
            override fun match(estimate: PositionEstimate, map: FloorMap): PositionEstimate? = null
        }
        pdr.reset(estimate)
        val fused = fusion.update(pdr.update(Unit, 1234L)!!)!!
        check(fused.source == PositionSource.FUSED)
        check(visual.localize(Unit, 1234L) == null && matcher.match(fused, repo.floorMap) == null)
        fusion.reset()
    }
    runHeadingToMapConverterTests(repo.floorMap.metadata, ::test)
    test("source JSON unchanged") { check(original.contentEquals(Files.readAllBytes(path))) }
    println("$passed tests passed")
}
