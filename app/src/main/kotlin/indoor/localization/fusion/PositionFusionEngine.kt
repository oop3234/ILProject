package indoor.localization.fusion

import indoor.localization.PositionEstimate

interface PositionFusionEngine {
    /**
     * Consume a PDR or VISUAL estimate; return FUSED or null if no valid fusion is possible.
     * Implementations must handle stale/out-of-order timestamps and incompatible floors explicitly.
     * Any retained state and confidence policy belong to the future algorithm, not this contract.
     */
    fun update(estimate: PositionEstimate): PositionEstimate?
    fun reset()
}
