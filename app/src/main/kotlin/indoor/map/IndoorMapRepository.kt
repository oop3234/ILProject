package indoor.map

import java.io.Reader
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

/** Read-only map access. A future spatial index can implement this contract independently. */
interface IndoorMapRepository {
    val floorMap: FloorMap
    fun getNodes(): List<Node>
    fun getNode(nodeId: String): Node?
    fun getEdges(): List<Edge>
    /** Incident edges, including incoming and non-walkable edges. Unknown ID returns empty. */
    fun getConnectedEdges(nodeId: String): List<Edge>
    /** Navigation centerline position, never the raw measurement position. */
    fun getPosition(nodeId: String): Coordinate?
    fun getMeasuredPosition(nodeId: String): Coordinate?
    /** Structural adjacency, regardless of walkability or direction. */
    fun areConnected(firstNodeId: String, secondNodeId: String): Boolean
    /** Direct traversal only; respects walkable and bidirectional. This is not path search. */
    fun canTraverse(fromNodeId: String, toNodeId: String): Boolean
}

/** Optional future capability; no nearest-node algorithm is implemented in this phase. */
interface NearestNodeSearch {
    fun findNearestNode(position: Coordinate, floor: Int): Node?
}

class JsonIndoorMapRepository private constructor(override val floorMap: FloorMap) : IndoorMapRepository {
    private val nodesById = floorMap.navigation.nodes.associateBy { it.id }
    private val measuredById = floorMap.measurement.nodes.associateBy { it.id }
    private val incident = floorMap.navigation.edges
        .flatMap { edge -> setOf(edge.from, edge.to).map { it to edge } }
        .groupBy({ it.first }, { it.second })

    override fun getNodes() = floorMap.navigation.nodes
    override fun getNode(nodeId: String) = nodesById[nodeId]
    override fun getEdges() = floorMap.navigation.edges
    override fun getConnectedEdges(nodeId: String) = incident[nodeId].orEmpty()
    override fun getPosition(nodeId: String) = getNode(nodeId)?.position
    override fun getMeasuredPosition(nodeId: String) = measuredById[nodeId]?.rawPosition
    override fun areConnected(firstNodeId: String, secondNodeId: String) =
        getConnectedEdges(firstNodeId).any {
            (it.from == firstNodeId && it.to == secondNodeId) ||
                (it.to == firstNodeId && it.from == secondNodeId)
        }

    override fun canTraverse(fromNodeId: String, toNodeId: String) =
        getConnectedEdges(fromNodeId).any {
            it.walkable && ((it.from == fromNodeId && it.to == toNodeId) ||
                (it.bidirectional && it.to == fromNodeId && it.from == toNodeId))
        }

    companion object {
        fun load(path: Path): JsonIndoorMapRepository =
            Files.newBufferedReader(path, StandardCharsets.UTF_8).use { load(it) }

        /** Caller owns the Reader. Android can supply an asset reader without Android core dependencies. */
        fun load(reader: Reader) = JsonIndoorMapRepository(MapJsonLoader.load(reader))
    }
}
