package org.devshred.gpstools.domain.gps

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
    companion object {
        fun fromGpxPoint(gpxPoint: GpxWayPoint): WayPoint =
            WayPoint(
                latitude = gpxPoint.latitude.toDouble(),
                longitude = gpxPoint.longitude.toDouble(),
                elevation = gpxPoint.elevation.orElse(null)?.toDouble(),
                time = gpxPoint.time.orElse(null),
            )
    }

    fun toGpxPoint(): GpxWayPoint = GpxWayPoint.builder().lat(latitude).lon(longitude).build()
}
