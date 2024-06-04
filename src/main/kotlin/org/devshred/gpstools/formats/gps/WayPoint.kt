package org.devshred.gpstools.formats.gps

import java.time.Instant
import java.util.UUID

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
    val uuid: UUID? = null,
)

fun mergeWaypoints(
    existing: List<WayPoint>,
    newWayPoints: List<WayPoint>,
): List<WayPoint> {
    val mapOfPoints = existing.associateBy { it.uuid }.toMutableMap()
    newWayPoints.forEach { mapOfPoints[it.uuid] = it }
    return mapOfPoints.values.toList()
}
