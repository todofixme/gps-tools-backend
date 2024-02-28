package org.devshred.gpstools.domain

import io.jenetics.jpx.WayPoint
import org.devshred.gpstools.proto3.GpsContainer
import org.devshred.gpstools.proto3.gpsContainer
import org.devshred.gpstools.proto3.track
import org.devshred.gpstools.proto3.trackPoint
import org.springframework.core.io.InputStreamResource
import java.io.InputStream
import io.jenetics.jpx.WayPoint as GpxWayPoint
import org.devshred.gpstools.proto3.TrackPoint as ProtoTrackPoint

fun trackPointsToProtobufInputStream(wayPoints: List<GpxWayPoint>): InputStream {
    val container =
        gpsContainer {
            track =
                track {
                    wayPoints.forEach { trackPoints += toProtoBuf(it) }
                }
        }
    return container.toByteArray().inputStream()
}

fun protoBufInputStreamResourceToWaypoints(inputStreamResource: InputStreamResource): List<WayPoint> {
    val gpsContainer = GpsContainer.parseFrom(inputStreamResource.contentAsByteArray)
    return gpsContainer.track.trackPointsList.map(::toGpx)
}

fun toProtoBuf(gpx: GpxWayPoint): ProtoTrackPoint =
    trackPoint {
        latitude = gpx.latitude.toDouble()
        longitude = gpx.longitude.toDouble()
        if (gpx.elevation.isPresent) elevation = gpx.elevation.get().toDouble()
        if (gpx.time.isPresent) time = gpx.time.get().epochSecond
    }

fun toGpx(protoWayPoint: ProtoTrackPoint): GpxWayPoint {
    val builder =
        GpxWayPoint.builder()
            .lat(protoWayPoint.latitude)
            .lon(protoWayPoint.longitude)

    if (protoWayPoint.hasElevation()) {
        builder.ele(protoWayPoint.elevation)
    }

    if (protoWayPoint.hasTime()) {
        builder.time(protoWayPoint.time)
    }

    return builder.build()
}
