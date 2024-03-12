package org.devshred.gpstools.domain.proto

import com.google.protobuf.Timestamp
import io.jenetics.jpx.GPX
import io.jenetics.jpx.TrackSegment
import org.devshred.gpstools.domain.common.orElse
import org.devshred.gpstools.domain.gps.PoiType
import org.devshred.gpstools.domain.gps.WayPoint
import org.devshred.gpstools.domain.gpx.GPX_CREATOR
import org.springframework.core.io.InputStreamResource
import java.io.InputStream
import java.time.Instant
import java.util.Optional
import io.jenetics.jpx.WayPoint as GpxWayPoint

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
        protoGpsContainer {
            if (trackName.isPresent) {
                name = trackName.get()
            }
            gpx.wayPoints.map(::toProtoBuf).forEach { wayPoints += it }
            if (gpx.tracks.isNotEmpty()) {
                track =
                    protoTrack {
                        gpx.tracks[0].segments[0].points.forEach { wayPoints += toProtoBuf(it) }
                    }
            }
        }
    return container.toByteArray().inputStream()
}

fun protoInputStreamResourceToProtoGpsContainer(inputStreamResource: InputStreamResource): ProtoGpsContainer =
    ProtoGpsContainer.parseFrom(inputStreamResource.contentAsByteArray)

fun protoInputStreamResourceToProtoGpsContainer(
    inputStreamResource: InputStreamResource,
    trackname: String?,
): ProtoGpsContainer =
    trackname?.let {
        protoInputStreamResourceToProtoGpsContainer(inputStreamResource).toBuilder().setName(trackname).build()
    }.orElse {
        protoInputStreamResourceToProtoGpsContainer(inputStreamResource)
    }

fun protoInputStreamResourceToGpx(
    inputStreamResource: InputStreamResource,
    trackname: String?,
): GPX {
    val gpsContainer =
        trackname?.let {
            protoInputStreamResourceToProtoGpsContainer(inputStreamResource).toBuilder().setName(trackname).build()
        }.orElse {
            protoInputStreamResourceToProtoGpsContainer(inputStreamResource)
        }
    val gpxBuilder = GPX.builder().creator(GPX_CREATOR)

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
    protoWayPoint {
        latitude = gpx.latitude.toDouble()
        longitude = gpx.longitude.toDouble()
        if (gpx.elevation.isPresent) elevation = gpx.elevation.get().toDouble()
        if (gpx.time.isPresent) time = Timestamp.newBuilder().setSeconds(gpx.time.get().epochSecond).build()
        if (gpx.name.isPresent) name = gpx.name.get()
        if (gpx.symbol.isPresent) type = toProtoBuf(PoiType.fromGpxSym(gpx.symbol.get())!!)
    }

fun toProtoBuf(poiType: PoiType): ProtoPoiType =
    when (poiType) {
        PoiType.GENERIC -> ProtoPoiType.GENERIC
        PoiType.SUMMIT -> ProtoPoiType.SUMMIT
        PoiType.VALLEY -> ProtoPoiType.VALLEY
        PoiType.WATER -> ProtoPoiType.WATER
        PoiType.FOOD -> ProtoPoiType.FOOD
        PoiType.DANGER -> ProtoPoiType.DANGER
        PoiType.LEFT -> ProtoPoiType.LEFT
        PoiType.RIGHT -> ProtoPoiType.RIGHT
        PoiType.STRAIGHT -> ProtoPoiType.STRAIGHT
        PoiType.FIRST_AID -> ProtoPoiType.FIRST_AID
        PoiType.FOURTH_CATEGORY -> ProtoPoiType.FOURTH_CATEGORY
        PoiType.THIRD_CATEGORY -> ProtoPoiType.THIRD_CATEGORY
        PoiType.SECOND_CATEGORY -> ProtoPoiType.SECOND_CATEGORY
        PoiType.FIRST_CATEGORY -> ProtoPoiType.FIRST_CATEGORY
        PoiType.HORS_CATEGORY -> ProtoPoiType.HORS_CATEGORY
        PoiType.RESIDENCE -> ProtoPoiType.RESIDENCE
        PoiType.SPRINT -> ProtoPoiType.SPRINT
    }

fun toGps(protoPoiType: ProtoPoiType): PoiType = PoiType.fromString(protoPoiType.name)

fun toGps(proto: ProtoWayPoint): WayPoint =
    WayPoint(
        latitude = proto.latitude,
        longitude = proto.longitude,
        elevation = if (proto.hasElevation()) proto.elevation else null,
        name = if (proto.hasName()) proto.name else null,
        time = if (proto.hasTime()) Instant.ofEpochSecond(proto.time.seconds) else null,
        type = if (proto.hasType()) toGps(proto.type) else null,
    )

fun toGpx(proto: ProtoWayPoint): GpxWayPoint {
    val builder =
        GpxWayPoint.builder()
            .lat(proto.latitude)
            .lon(proto.longitude)

    if (proto.hasElevation()) {
        builder.ele(proto.elevation)
    }

    if (proto.hasTime()) {
        builder.time(Instant.ofEpochSecond(proto.time.seconds))
    }

    if (proto.hasName()) {
        builder.name(proto.name)
    }

    if (proto.hasType()) {
        builder.sym(PoiType.fromString(proto.type.name).gpxSym)
    }

    return builder.build()
}
