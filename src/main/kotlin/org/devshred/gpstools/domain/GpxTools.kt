package org.devshred.gpstools.domain

import io.jenetics.jpx.GPX
import io.jenetics.jpx.WayPoint
import org.springframework.core.io.InputStreamResource
import java.io.ByteArrayOutputStream
import java.nio.file.Path

private const val GPX_CREATOR = "GPS-Tools - https://gps-tools.pages.dev"

fun gpxFromFileLocation(location: String): GPX = GPX.read(Path.of(location))

fun gpxToByteArrayOutputStream(gpx: GPX): ByteArrayOutputStream {
    val out = ByteArrayOutputStream()
    GPX.Writer.DEFAULT.write(gpx, out)

    return out
}

fun extractPointsFromGpxTrack(inputStream: InputStreamResource): Pair<List<WayPoint>, List<WayPoint>> {
    val gpx = protoInputStreamResourceToGpx(inputStream)
    return gpx.wayPoints to gpx.tracks[0].segments[0].points
}

fun buildGpx(
    wayPoints: List<WayPoint>,
    trackPoints: List<WayPoint>,
): GPX {
    val gpxBuilder = GPX.builder().creator(GPX_CREATOR)
    gpxBuilder.wayPoints(wayPoints)
    gpxBuilder.addTrack { track -> track.addSegment { segment -> segment.points(trackPoints) } }
    return gpxBuilder.build()
}
