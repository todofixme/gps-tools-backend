package org.devshred.gpstools.domain.gpx

import org.devshred.gpstools.domain.gps.GpsContainerMapper
import org.devshred.gpstools.domain.gps.PoiType
import org.devshred.gpstools.domain.proto.ProtoService
import org.geojson.FeatureCollection
import org.geojson.Point
import org.springframework.stereotype.Service
import java.io.ByteArrayInputStream
import java.io.InputStream
import org.devshred.gpstools.domain.gps.WayPoint as GpsWaypoint

@Service
class GpxService(private val protoService: ProtoService, private val mapper: GpsContainerMapper) {
    fun protoFileToGpxInputStream(
        storageLocation: String,
        name: String?,
        featureCollection: FeatureCollection? = null,
    ): ByteArrayInputStream {
        val protoGpsContainer = protoService.readProtoGpsContainer(storageLocation, name)

        val gpsContainer =
            if (featureCollection != null) {
                val wayPoints =
                    featureCollection.features.map { feature ->
                        val point = feature.geometry as Point
                        GpsWaypoint(
                            latitude = point.coordinates.latitude,
                            longitude = point.coordinates.longitude,
                            name = feature.properties["name"] as String?,
                            type = PoiType.fromString((feature.properties["type"] as String?) ?: "GENERIC"),
                        )
                    }
                mapper.fromProto(protoGpsContainer).copy(
                    wayPoints = wayPoints,
                )
            } else {
                mapper.fromProto(protoGpsContainer)
            }

        val outputStream = gpxToByteArrayOutputStream(mapper.toGpx(gpsContainer))
        return ByteArrayInputStream(outputStream.toByteArray())
    }

    fun protoInputStreamFromFileLocation(fileLocation: String): InputStream {
        val gpx = gpxFromFileLocation(fileLocation)
        return protoService.gpxToProtobufInputStream(gpx)
    }
}
