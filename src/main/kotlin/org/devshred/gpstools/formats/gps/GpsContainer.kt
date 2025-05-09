package org.devshred.gpstools.formats.gps

import io.jenetics.jpx.geom.Geoid

data class GpsContainer(
    val name: String?,
    val pointsOfInterest: List<PointOfInterest>,
    val track: Track?,
) {
    fun withOptimizedPointsOfInterest(tolerance: Int = MAX_DISTANCE_BETWEEN_TRACK_AND_WAYPOINT): GpsContainer {
        val optimizedWayPoints =
            pointsOfInterest
                .mapNotNull { findPointOnTrackNearestTo(it, tolerance) }
                .sortedBy { it.time }

        return GpsContainer(name, optimizedWayPoints, track)
    }

    private fun findPointOnTrackNearestTo(point: PointOfInterest): PointOfInterest {
        val gpxPoint = point.toGpx()
        val nearestGpxPoint =
            track
                ?.trackPoints
                ?.stream()
                ?.map { it.toGpx() }
                ?.reduce { result: io.jenetics.jpx.WayPoint, current: io.jenetics.jpx.WayPoint ->
                    if (Geoid.WGS84.distance(current, gpxPoint).toInt()
                        < Geoid.WGS84.distance(result, gpxPoint).toInt()
                    ) {
                        current
                    } else {
                        result
                    }
                }?.get()

        return point.copy(
            latitude = nearestGpxPoint!!.latitude.toDouble(),
            longitude = nearestGpxPoint.longitude.toDouble(),
            time = nearestGpxPoint.time.orElse(null),
        )
    }

    private fun findPointOnTrackNearestTo(
        point: PointOfInterest,
        tolerance: Int,
    ): PointOfInterest? {
        val nearestWayPoint = findPointOnTrackNearestTo(point)
        return if (Geoid.WGS84.distance(nearestWayPoint.toGpx(), point.toGpx()).toInt() > tolerance) {
            null
        } else {
            point.copy(
                latitude = nearestWayPoint.latitude,
                longitude = nearestWayPoint.longitude,
                time = nearestWayPoint.time,
            )
        }
    }

    fun removeDuplicatePointsOfInterest(): GpsContainer {
        val cleanedPoints =
            pointsOfInterest
                .distinctBy { Triple(it.latitude, it.longitude, it.name) }

        return GpsContainer(name, cleanedPoints, track)
    }
}

private const val MAX_DISTANCE_BETWEEN_TRACK_AND_WAYPOINT = 500
