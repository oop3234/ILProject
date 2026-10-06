package indoor.map

/** Coordinates in metadata.unit, using the axes supplied by this map. */
data class Coordinate(val x: Double, val y: Double) {
    init { require(x.isFinite() && y.isFinite()) { "Coordinates must be finite" } }
}

data class HeadingAxes(
    val positiveXHeading: Double, val negativeXHeading: Double,
    val positiveYHeading: Double, val negativeYHeading: Double,
)

data class CoordinateSystem(
    val originNodeId: String,
    val originPositionSource: String,
    val origin: Coordinate,
    val headingReference: String,
    val headingUnit: String,
    val headingConvention: String,
    val headingRange: String,
    val axes: HeadingAxes,
    val note: String?,
)

data class MapMetadata(val mapId: String, val floor: Int, val unit: String, val coordinateSystem: CoordinateSystem)
data class MeasuredNode(val id: String, val rawPosition: Coordinate)
data class Corridor(val id: String, val nodeIds: List<String>, val width: Double, val direction: String, val heading: Double)
data class Measurement(val positionReference: String, val nodes: List<MeasuredNode>, val corridors: List<Corridor>)

/** Optional fields remain absent; in particular, a missing capture position is not guessed. */
data class ImageReference(
    val id: String,
    val path: String,
    val file: String?,
    val direction: String?,
    val heading: Double?,
    val caption: String?,
    val mimeType: String?,
    val capturedAt: String?,
    val positionSource: String?,
    val extraMetadata: Map<String, Any?>,
    val extensions: Map<String, Any?>,
)

data class Node(
    val id: String,
    val type: String,
    val description: String?,
    val position: Coordinate,
    val images: List<ImageReference>,
    val poiType: String?,
    val roomNumber: String?,
    val hasAdditionalBranch: Boolean?,
    val additionalBranchEnabled: Boolean?,
)

data class EdgeHeading(val forward: Double, val reverse: Double)
data class Edge(
    val id: String,
    val from: String,
    val to: String,
    val corridorId: String,
    val bidirectional: Boolean,
    val walkable: Boolean,
    val distance: Double,
    val heading: EdgeHeading,
)

data class Navigation(
    val positionReference: String,
    val distanceCalculation: String,
    val edgeConvention: String,
    val nodes: List<Node>,
    val edges: List<Edge>,
)

data class FloorMap(
    val schemaVersion: String,
    val metadata: MapMetadata,
    val measurement: Measurement,
    val navigation: Navigation,
    /** Descriptive embedded schema, retained without inventing a replacement schema. */
    val imageMetadataSchema: Map<String, Any?>,
)
