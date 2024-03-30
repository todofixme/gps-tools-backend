package org.devshred.gpstools.storage

import com.garmin.fit.FitDecoder
import com.garmin.fit.FitRuntimeException
import org.devshred.gpstools.formats.gps.GpsContainer
import org.devshred.gpstools.formats.gps.GpsContainerMapper
import org.devshred.gpstools.formats.gps.PoiType
import org.devshred.gpstools.formats.gps.WayPoint
import org.devshred.gpstools.formats.gpx.GpxService
import org.devshred.gpstools.formats.gpx.gpxToByteArrayOutputStream
import org.devshred.gpstools.formats.proto.ProtoService
import org.devshred.gpstools.formats.tcx.TrainingCenterDatabase
import org.devshred.gpstools.formats.tcx.createTcxFromGpsContainer
import org.devshred.gpstools.formats.tcx.tcxToByteArrayOutputStream
import org.geojson.FeatureCollection
import org.geojson.Point
import org.springframework.stereotype.Service
import java.io.ByteArrayInputStream
import java.io.FileInputStream
import java.io.InputStream

@Service
class FileService(
    private val protoService: ProtoService,
    private val gpxService: GpxService,
    private val gpsMapper: GpsContainerMapper,
) {
    fun getGpxInputStream(
        storageLocation: String,
        name: String?,
        featureCollection: FeatureCollection? = null,
    ): InputStream {
        val gpsContainer: GpsContainer = getGpsContainer(storageLocation, name, featureCollection)
        val outputStream = gpxToByteArrayOutputStream(gpsMapper.toGpx(gpsContainer))
        return ByteArrayInputStream(outputStream.toByteArray())
    }

    fun getTcxInputStream(
        storageLocation: String,
        name: String?,
        featureCollection: FeatureCollection?,
    ): InputStream {
        val gpsContainer: GpsContainer = getGpsContainer(storageLocation, name, featureCollection)
        val tcx: TrainingCenterDatabase = createTcxFromGpsContainer(gpsContainer)
        val outputStream = tcxToByteArrayOutputStream(tcx)
        return ByteArrayInputStream(outputStream.toByteArray())
    }

    fun getProtoStreamFromGpxFile(storageLocation: String): InputStream {
        val gpx = gpxService.gpxFromFileLocation(storageLocation)
        val gpsContainer = gpsMapper.fromGpx(gpx)
        val protoContainer = gpsMapper.toProto(gpsContainer)
        return protoContainer.toByteArray().inputStream()
    }

    fun getProtoStreamFromFitFile(storageLocation: String): InputStream {
        val fitDecoder = FitDecoder()
        try {
            val fitMessages = fitDecoder.decode(FileInputStream(storageLocation))
            val gpsContainer = gpsMapper.fromFit(fitMessages)
            val protoContainer = gpsMapper.toProto(gpsContainer)
            return protoContainer.toByteArray().inputStream()
        } catch (e: FitRuntimeException) {
            throw RuntimeException(e)
        }
    }

    private fun getGpsContainer(
        storageLocation: String,
        name: String?,
        featureCollection: FeatureCollection? = null,
    ): GpsContainer {
        val protoGpsContainer = protoService.readProtoContainer(storageLocation, name)

        if (featureCollection != null) {
            val wayPoints =
                featureCollection.features.map { feature ->
                    val point = feature.geometry as Point
                    WayPoint(
                        latitude = point.coordinates.latitude,
                        longitude = point.coordinates.longitude,
                        name = feature.properties["name"] as String?,
                        type = PoiType.fromString((feature.properties["type"] as String?) ?: "GENERIC"),
                    )
                }
            return gpsMapper.fromProto(protoGpsContainer).copy(
                wayPoints = wayPoints,
            )
        } else {
            return gpsMapper.fromProto(protoGpsContainer)
        }
    }
}
