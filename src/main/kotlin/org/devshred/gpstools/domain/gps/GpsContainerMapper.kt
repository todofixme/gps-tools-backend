package org.devshred.gpstools.domain.gps

import io.jenetics.jpx.GPX
import org.devshred.gpstools.domain.common.orElse
import org.devshred.gpstools.domain.gpx.GPX_CREATOR
import org.devshred.gpstools.domain.proto.ProtoGpsContainer
import org.devshred.gpstools.domain.proto.toGps
import org.springframework.stereotype.Component

@Component
class GpsContainerMapper {
    fun toProto(domainModel: GpsContainer): ProtoGpsContainer {
        throw UnsupportedOperationException()
    }

    fun fromProto(protoModel: ProtoGpsContainer): GpsContainer =
        GpsContainer(
            name = protoModel.name,
            wayPoints = protoModel.wayPointsList.map { toGps(it) },
            track =
                protoModel.track
                    ?.let { Track(protoModel.track.wayPointsList.map { toGps(it) }) }
                    .orElse { null },
        )

    fun toGpx(domainModel: GpsContainer): GPX {
        val gpxBuilder = GPX.builder().creator(GPX_CREATOR)
        domainModel.name?.let { gpsName -> gpxBuilder.metadata { gpx -> gpx.name(gpsName) } }
        gpxBuilder.wayPoints(domainModel.wayPoints.map { it.toGpxPoint() })
        gpxBuilder.addTrack { track ->
            track.name(domainModel.name)
            track.addSegment { segment ->
                segment.points(domainModel.track?.wayPoints?.map { it.toGpxPoint() })
            }
        }
        return gpxBuilder.build()
    }

    fun fromGpx(gpxModel: GPX): GpsContainer = throw UnsupportedOperationException()
}
