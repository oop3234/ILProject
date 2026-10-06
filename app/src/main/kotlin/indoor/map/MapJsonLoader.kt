package indoor.map

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.Gson
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import java.io.Reader

class MapFormatException(message: String, cause: Throwable? = null) : IllegalArgumentException(message, cause)

/** Explicit decoding avoids Gson reflection silently assigning defaults to missing Kotlin fields. */
object MapJsonLoader {
    fun load(reader: Reader): FloorMap {
        try {
            val input = JsonReader(reader).apply { isLenient = false }
            val root = Fields(Gson().getAdapter(JsonElement::class.java).read(input).asJsonObject, "$")
            if (input.peek() != JsonToken.END_DOCUMENT) fail("$", "unexpected trailing content")
            val version = root.string("schema_version")
            if (version != "1.0.0") fail("$.schema_version", "unsupported version: $version")
            val metadata = root.obj("metadata")
            val cs = metadata.obj("coordinate_system")
            val axes = cs.obj("axes")
            val measurement = root.obj("measurement")
            val navigation = root.obj("navigation")
            val map = FloorMap(
                version,
                MapMetadata(metadata.string("map_id"), metadata.integer("floor"), metadata.string("unit"),
                    CoordinateSystem(cs.string("origin_node_id"), cs.string("origin_position_source"),
                        cs.coordinate("origin"), cs.string("heading_reference"), cs.string("heading_unit"),
                        cs.string("heading_convention"), cs.string("heading_range"),
                        HeadingAxes(axes.heading("positive_x_heading"), axes.heading("negative_x_heading"),
                            axes.heading("positive_y_heading"), axes.heading("negative_y_heading")), cs.optionalString("note"))),
                Measurement(measurement.string("position_reference"),
                    measurement.objects("nodes").map { MeasuredNode(it.string("id"), it.coordinate("raw_position")) },
                    measurement.objects("corridors").map {
                        Corridor(it.string("id"), it.strings("node_ids"), it.positive("width"),
                            it.string("direction"), it.heading("heading"))
                    }),
                Navigation(navigation.string("position_reference"), navigation.string("distance_calculation"),
                    navigation.string("edge_convention"), navigation.objects("nodes").map { node ->
                        Node(node.string("id"), node.string("type"), node.optionalString("description"),
                            node.coordinate("position"), node.objects("images").map(::image),
                            node.optionalString("poi_type"), node.optionalString("room_number"),
                            node.optionalBoolean("has_additional_branch"), node.optionalBoolean("additional_branch_enabled"))
                    }, navigation.objects("edges").map {
                        val heading = it.obj("heading")
                        Edge(it.string("id"), it.string("from"), it.string("to"), it.string("corridor_id"),
                            it.boolean("bidirectional"), it.boolean("walkable"), it.positive("distance"),
                            EdgeHeading(heading.heading("forward"), heading.heading("reverse")))
                    }),
                root.obj("image_metadata_schema").values(),
            )
            validateReferences(map)
            return map
        } catch (e: MapFormatException) {
            throw e
        } catch (e: com.google.gson.stream.MalformedJsonException) {
            throw MapFormatException("Invalid map JSON: ${e.message}", e)
        } catch (e: java.io.EOFException) {
            throw MapFormatException("Incomplete map JSON: ${e.message}", e)
        } catch (e: java.io.IOException) {
            throw e
        } catch (e: RuntimeException) {
            throw MapFormatException("Invalid map JSON: ${e.message}", e)
        }
    }

    private fun image(f: Fields): ImageReference {
        val positionSource = f.optionalString("position_source")
        if (positionSource != null && positionSource !in setOf("measurement.raw_position", "navigation.position")) {
            fail("${f.path}.position_source", "unknown capture position source")
        }
        val known = setOf("id", "path", "file", "direction", "heading", "caption", "mime_type", "captured_at", "position_source", "extra_metadata")
        return ImageReference(f.string("id"), f.string("path"), f.optionalString("file"),
            f.optionalString("direction"), if (f.has("heading")) f.heading("heading") else null,
            f.optionalString("caption"), f.optionalString("mime_type"), f.optionalString("captured_at"),
            positionSource, if (f.has("extra_metadata")) f.obj("extra_metadata").values() else emptyMap(),
            f.values().filterKeys { it !in known })
    }

    private fun validateReferences(map: FloorMap) {
        fun unique(ids: List<String>, path: String): Set<String> {
            if (ids.size != ids.toSet().size) fail(path, "duplicate IDs")
            return ids.toSet()
        }
        val nodes = unique(map.navigation.nodes.map { it.id }, "$.navigation.nodes")
        val measured = unique(map.measurement.nodes.map { it.id }, "$.measurement.nodes")
        val corridors = unique(map.measurement.corridors.map { it.id }, "$.measurement.corridors")
        unique(map.navigation.edges.map { it.id }, "$.navigation.edges")
        if (map.metadata.coordinateSystem.originNodeId !in measured) fail("$.metadata.coordinate_system.origin_node_id", "unknown measured node")
        map.measurement.corridors.forEach { corridor ->
            if (corridor.nodeIds.any { it !in measured }) fail("corridor ${corridor.id}", "unknown measured node")
        }
        map.navigation.edges.forEach { edge ->
            if (edge.from !in nodes || edge.to !in nodes) fail("edge ${edge.id}", "unknown navigation endpoint")
            if (edge.corridorId !in corridors) fail("edge ${edge.id}", "unknown corridor")
        }
        map.navigation.nodes.forEach { node ->
            unique(node.images.map { it.id }, "node ${node.id}.images")
            if (node.images.any { it.positionSource == "measurement.raw_position" } && node.id !in measured) {
                fail("node ${node.id}.images", "capture references missing measured node")
            }
        }
    }

    private class Fields(private val json: JsonObject, val path: String) {
        fun has(key: String) = json.has(key)
        private fun value(key: String): JsonElement = json.get(key)?.takeUnless { it.isJsonNull }
            ?: fail("$path.$key", "required field missing or null")
        fun string(key: String): String {
            val v = value(key)
            if (!v.isJsonPrimitive || !v.asJsonPrimitive.isString || v.asString.isBlank()) fail("$path.$key", "expected nonblank string")
            return v.asString
        }
        fun optionalString(key: String) = if (has(key)) string(key) else null
        fun boolean(key: String): Boolean {
            val v = value(key)
            if (!v.isJsonPrimitive || !v.asJsonPrimitive.isBoolean) fail("$path.$key", "expected boolean")
            return v.asBoolean
        }
        fun optionalBoolean(key: String) = if (has(key)) boolean(key) else null
        fun number(key: String): Double {
            val v = value(key)
            if (!v.isJsonPrimitive || !v.asJsonPrimitive.isNumber) fail("$path.$key", "expected number")
            return v.asDouble.also { if (!it.isFinite()) fail("$path.$key", "expected finite number") }
        }
        fun integer(key: String): Int {
            val n = number(key)
            if (n < Int.MIN_VALUE || n > Int.MAX_VALUE || n % 1.0 != 0.0) fail("$path.$key", "expected integer")
            return n.toInt()
        }
        fun positive(key: String) = number(key).also { if (it <= 0) fail("$path.$key", "expected positive number") }
        fun heading(key: String) = number(key).also { if (it < 0 || it >= 360) fail("$path.$key", "expected heading in [0, 360)") }
        fun obj(key: String): Fields {
            val v = value(key)
            if (!v.isJsonObject) fail("$path.$key", "expected object")
            return Fields(v.asJsonObject, "$path.$key")
        }
        private fun array(key: String): List<JsonElement> {
            val v = value(key)
            if (!v.isJsonArray) fail("$path.$key", "expected array")
            return v.asJsonArray.toList()
        }
        fun objects(key: String) = array(key).mapIndexed { i, v ->
            if (!v.isJsonObject) fail("$path.$key[$i]", "expected object")
            Fields(v.asJsonObject, "$path.$key[$i]")
        }
        fun strings(key: String) = array(key).mapIndexed { i, v ->
            if (!v.isJsonPrimitive || !v.asJsonPrimitive.isString || v.asString.isBlank()) fail("$path.$key[$i]", "expected nonblank string")
            v.asString
        }
        fun coordinate(key: String) = obj(key).let { Coordinate(it.number("x"), it.number("y")) }
        fun values(): Map<String, Any?> = json.entrySet().associate { it.key to plain(it.value) }
    }

    private fun plain(v: JsonElement): Any? = when {
        v.isJsonNull -> null
        v.isJsonObject -> v.asJsonObject.entrySet().associate { it.key to plain(it.value) }
        v.isJsonArray -> v.asJsonArray.map(::plain)
        v.asJsonPrimitive.isBoolean -> v.asBoolean
        v.asJsonPrimitive.isNumber -> v.asBigDecimal
        else -> v.asString
    }

    private fun fail(path: String, message: String): Nothing = throw MapFormatException("$path: $message")
}
