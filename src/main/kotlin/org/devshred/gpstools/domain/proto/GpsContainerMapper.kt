package org.devshred.gpstools.domain.proto

import org.devshred.gpstools.domain.common.orElse
import org.devshred.gpstools.domain.gps.GpsContainer
import org.devshred.gpstools.domain.gps.Track
import org.springframework.stereotype.Component

@Component
class GpsContainerMapper {
    fun map(domainModel: GpsContainer): ProtoGpsContainer {
        throw UnsupportedOperationException()
    }

    fun map(protoModel: ProtoGpsContainer): GpsContainer =
        GpsContainer(
            name = protoModel.name,
            wayPoints = protoModel.wayPointsList.map { toGps(it) },
            track =
                protoModel.track
                    ?.let { Track(protoModel.track.wayPointsList.map { toGps(it) }) }
                    .orElse { null },
        )
}
