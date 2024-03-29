package org.devshred.gpstools.formats.gps

import java.time.Instant

data class WayPoint(
    val latitude: Double,
    val longitude: Double,
    val elevation: Double? = null,
    val time: Instant? = null,
    val name: String? = null,
    val type: PoiType? = null,
    val speed: Double? = null,
    val power: Int? = null,
    val temperature: Int? = null,
    val heartRate: Int? = null,
    val cadence: Int? = null,
)
