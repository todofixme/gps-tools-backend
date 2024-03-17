package org.devshred.gpstools.domain.proto

import io.jenetics.jpx.GPX
import org.devshred.gpstools.domain.IOService
import org.devshred.gpstools.domain.common.orElse
import org.springframework.core.io.InputStreamResource
import org.springframework.stereotype.Service
import java.io.InputStream
import java.util.Optional

@Service
class ProtoService(private val ioService: IOService) {
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

    fun readProtoGpsContainer(storageLocation: String): ProtoGpsContainer {
        val inputStreamResource: InputStreamResource = ioService.getAsStream(storageLocation)
        return ProtoGpsContainer.parseFrom(inputStreamResource.contentAsByteArray)
    }

    fun readProtoGpsContainer(
        storageLocation: String,
        trackname: String?,
    ): ProtoGpsContainer =
        trackname?.let {
            readProtoGpsContainer(storageLocation).toBuilder().setName(trackname).build()
        }.orElse {
            readProtoGpsContainer(storageLocation)
        }
}
