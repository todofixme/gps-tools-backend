package org.devshred.gpstools.domain.gps

import io.jenetics.jpx.Point
import java.time.Instant
import io.jenetics.jpx.WayPoint as GpxWayPoint

data class WayPoint(
    val latitude: Double,
    val longitude: Double,
    val elevation: Double? = null,
    val time: Instant? = null,
    val name: String? = null,
    val type: PoiType? = null,
) {
    fun toGpxPoint(): Point = GpxWayPoint.builder().lat(latitude).lon(longitude).build()
}
