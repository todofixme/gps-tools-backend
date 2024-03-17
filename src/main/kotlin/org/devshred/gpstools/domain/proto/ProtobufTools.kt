package org.devshred.gpstools.domain.proto

import com.google.protobuf.Timestamp
import io.jenetics.jpx.GPX
import io.jenetics.jpx.TrackSegment
import org.devshred.gpstools.domain.gps.PoiType
import org.devshred.gpstools.domain.gps.WayPoint
import org.devshred.gpstools.domain.gpx.GPX_CREATOR
import java.time.Instant
import io.jenetics.jpx.WayPoint as GpxWayPoint

fun ProtoGpsContainer.toGpx(): GPX {
    val gpxBuilder = GPX.builder().creator(GPX_CREATOR)

    if (hasName()) {
        gpxBuilder.metadata { it.name(name) }
    }

    if (wayPointsCount > 0) {
        wayPointsList.forEach { gpxBuilder.addWayPoint(it.toGpx()) }
    }

    val segmentBuilder = TrackSegment.builder()
    track.wayPointsList.forEach { segmentBuilder.addPoint(it.toGpx()) }
    gpxBuilder.addTrack {
        if (hasName()) {
            it.name(name)
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

fun ProtoWayPoint.toGpx(): GpxWayPoint {
    val builder =
        GpxWayPoint.builder()
            .lat(latitude)
            .lon(longitude)

    if (hasElevation()) {
        builder.ele(elevation)
    }

    if (hasTime()) {
        builder.time(Instant.ofEpochSecond(time.seconds))
    }

    if (hasName()) {
        builder.name(name)
    }

    if (hasType()) {
        builder.sym(PoiType.fromString(type.name).gpxSym)
    }

    return builder.build()
}
