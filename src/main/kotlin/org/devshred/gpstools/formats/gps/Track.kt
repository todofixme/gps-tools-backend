package org.devshred.gpstools.formats.gps

import io.jenetics.jpx.Length
import io.jenetics.jpx.geom.Geoid

data class Track(
    val trackPoints: List<TrackPoint>,
) {
    fun calculateLength(): Length =
        trackPoints
            .map(TrackPoint::toGpx)
            .stream()
            .collect(Geoid.WGS84.toPathLength())
}
