package indoor.localization.pdr

import indoor.localization.PositionEstimate

/** SensorSample belongs to the platform adapter; no fabricated IMU format or data here. */
interface PdrLocalizer<in SensorSample> {
    /** Initialize or correct relative tracking from an established absolute estimate. */
    fun reset(anchor: PositionEstimate)
    /** Return PDR, or null before initialization / when the sample cannot yield an estimate. */
    fun update(sample: SensorSample, timestamp: Long): PositionEstimate?
}
