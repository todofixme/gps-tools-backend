package org.devshred.upload

import io.jenetics.jpx.GPX
import io.jenetics.jpx.Length
import io.jenetics.jpx.WayPoint
import io.jenetics.jpx.geom.Geoid
import java.io.ByteArrayOutputStream
import java.nio.file.Path
import java.util.stream.Stream

fun calculateLength(gpx: GPX): Length {
    return gpx.tracks()
        .flatMap(io.jenetics.jpx.Track::segments)
        .findFirst()
        .map { it.points() }.orElse(Stream.empty())
        .collect(Geoid.WGS84.toPathLength())
}

fun wayPointsFromFileLocation(location: String): List<WayPoint> =
    GPX.read(Path.of(location)).tracks[0].segments[0].points

fun waiPointsToByteArrayOutputStream(wayPoints: List<WayPoint>): ByteArrayOutputStream {
    val gpxBuilder = GPX.builder()
    wayPoints.forEach { gpxBuilder.addWayPoint(it) }
    val gpx = gpxBuilder.build()
    val out = ByteArrayOutputStream()
    GPX.Writer.DEFAULT.write(gpx, out)

    return out
}
