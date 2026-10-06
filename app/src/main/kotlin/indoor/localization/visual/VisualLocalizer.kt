package indoor.localization.visual

import indoor.localization.PositionEstimate

interface VisualLocalizer<in CameraFrame> {
    /** Return VISUAL in the map frame, or null for an insufficient/ambiguous match.
     * Implementations resolve nodeId through IndoorMapRepository and honor capture position_source.
     * timestamp is frame capture time in Unix milliseconds, not inference completion time.
     */
    fun localize(frame: CameraFrame, timestamp: Long): PositionEstimate?
}
