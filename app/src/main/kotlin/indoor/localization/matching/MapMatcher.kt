package indoor.localization.matching

import indoor.localization.PositionEstimate
import indoor.map.FloorMap

interface MapMatcher {
    /** Return a NAVIGATION estimate, or null if unmatched/floor incompatible.
     * Preserve source and observation timestamp; do not force every position to a node.
     */
    fun match(estimate: PositionEstimate, map: FloorMap): PositionEstimate?
}
