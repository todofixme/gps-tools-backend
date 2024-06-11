package org.devshred.gpstools.formats.gps

import java.time.Instant
import java.util.UUID

data class PointOfInterest(
    val uuid: UUID,
    val latitude: Double,
    val longitude: Double,
    val elevation: Double? = null,
    val time: Instant? = null,
    val name: String? = null,
    val type: PoiType? = null,
)

fun mergePoints(
    existing: List<PointOfInterest>,
    newPOIs: List<PointOfInterest>,
): List<PointOfInterest> {
    val mapOfPoints = existing.associateBy { it.uuid }.toMutableMap()
    newPOIs.forEach { mapOfPoints[it.uuid] = it }
    return mapOfPoints.values.toList()
}
