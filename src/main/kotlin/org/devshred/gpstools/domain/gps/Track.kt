package org.devshred.gpstools.domain.gps

import io.jenetics.jpx.Length
import io.jenetics.jpx.geom.Geoid

data class Track(val wayPoints: List<WayPoint>) {
    fun calculateLength(): Length {
        return wayPoints
            .map(WayPoint::toGpxPoint)
            .stream()
            .collect(Geoid.WGS84.toPathLength())
    }
}
