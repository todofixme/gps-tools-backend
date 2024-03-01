package org.devshred.gpstools.domain

import io.jenetics.jpx.GPX
import io.jenetics.jpx.TrackSegment
import org.devshred.gpstools.proto3.GpsContainer
import org.devshred.gpstools.proto3.gpsContainer
import org.devshred.gpstools.proto3.track
import org.devshred.gpstools.proto3.wayPoint
import org.springframework.core.io.InputStreamResource
import java.io.InputStream
import java.util.Optional
import io.jenetics.jpx.WayPoint as GpxWayPoint
import org.devshred.gpstools.proto3.WayPoint as ProtoWayPoint

fun gpxToProtobufInputStream(gpx: GPX): InputStream {
    val trackName: Optional<String> =
        if (gpx.tracks.isNotEmpty() && gpx.tracks[0].name.isPresent) {
            gpx.tracks[0].name
        } else if (gpx.metadata.isPresent && gpx.metadata.get().name.isPresent) {
            gpx.metadata.get().name
        } else {
            Optional.empty()
        }

    val container =
        gpsContainer {
            if (trackName.isPresent) {
                name = trackName.get()
            }
            gpx.wayPoints.map(::toProtoBuf).forEach { wayPoints += it }
            if (gpx.tracks.isNotEmpty()) {
                track =
                    track {
                        gpx.tracks[0].segments[0].points.forEach { wayPoints += toProtoBuf(it) }
                    }
            }
        }
    return container.toByteArray().inputStream()
}

fun protoInputStreamResourceToGpsContainer(inputStreamResource: InputStreamResource): GpsContainer =
    GpsContainer.parseFrom(inputStreamResource.contentAsByteArray)

fun protoInputStreamResourceToGpx(inputStreamResource: InputStreamResource): GPX {
    val gpsContainer = protoInputStreamResourceToGpsContainer(inputStreamResource)
    val gpxBuilder = GPX.builder()

    if (gpsContainer.hasName()) {
        gpxBuilder.metadata { it.name(gpsContainer.name) }
    }

    if (gpsContainer.wayPointsCount > 0) {
        gpsContainer.wayPointsList.forEach { gpxBuilder.addWayPoint(toGpx(it)) }
    }

    val segmentBuilder = TrackSegment.builder()
    gpsContainer.track.wayPointsList.forEach { segmentBuilder.addPoint(toGpx(it)) }
    gpxBuilder.addTrack {
        if (gpsContainer.hasName()) {
            it.name(gpsContainer.name)
        }
        it.addSegment(segmentBuilder.build())
    }

    return gpxBuilder.build()
}

fun toProtoBuf(gpx: GpxWayPoint): ProtoWayPoint =
    wayPoint {
        latitude = gpx.latitude.toDouble()
        longitude = gpx.longitude.toDouble()
        if (gpx.elevation.isPresent) elevation = gpx.elevation.get().toDouble()
        if (gpx.time.isPresent) time = gpx.time.get().epochSecond
        if (gpx.name.isPresent) name = gpx.name.get()
        if (gpx.symbol.isPresent) symbol = gpx.symbol.get()
    }

fun toGpx(proto: ProtoWayPoint): GpxWayPoint {
    val builder =
        GpxWayPoint.builder()
            .lat(proto.latitude)
            .lon(proto.longitude)

    if (proto.hasElevation()) {
        builder.ele(proto.elevation)
    }

    if (proto.hasTime()) {
        builder.time(proto.time)
    }

    if (proto.hasName()) {
        builder.name(proto.name)
    }

    if (proto.hasSymbol()) {
        builder.sym(proto.symbol)
    }

    return builder.build()
}
