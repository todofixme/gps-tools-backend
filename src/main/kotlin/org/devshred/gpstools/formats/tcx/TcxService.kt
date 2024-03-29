package org.devshred.gpstools.formats.tcx

import org.devshred.gpstools.formats.gps.GpsContainerMapper
import org.devshred.gpstools.formats.gps.WayPoint
import org.devshred.gpstools.formats.proto.ProtoService
import org.geojson.FeatureCollection
import org.geojson.Point
import org.springframework.stereotype.Service
import java.io.ByteArrayInputStream

@Service
class TcxService(private val protoService: ProtoService, private val mapper: GpsContainerMapper) {
    fun protoFileToTcxInputStream(
        storageLocation: String,
        name: String?,
        featureCollection: FeatureCollection?,
    ): ByteArrayInputStream {
        val proto = protoService.readProtoContainer(storageLocation, name)

        val gpsContainer =
            if (featureCollection != null) {
                val wayPoints =
                    featureCollection.features.map { feature ->
                        val point = feature.geometry as Point
                        WayPoint(
                            latitude = point.coordinates.latitude,
                            longitude = point.coordinates.longitude,
                            name = feature.properties["name"] as String?,
                        )
                    }
                mapper.fromProto(proto).copy(wayPoints = wayPoints)
            } else {
                mapper.fromProto(proto)
            }

        val tcx: TrainingCenterDatabase = createTcxFromGpsContainer(gpsContainer)
        val outputStream = tcxToByteArrayOutputStream(tcx)
        return ByteArrayInputStream(outputStream.toByteArray())
    }
}
