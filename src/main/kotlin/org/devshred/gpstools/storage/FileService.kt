package org.devshred.gpstools.storage

import com.garmin.fit.FitDecoder
import com.garmin.fit.FitRuntimeException
import mil.nga.sf.geojson.FeatureCollection
import mil.nga.sf.geojson.FeatureConverter
import mil.nga.sf.geojson.Point
import org.devshred.gpstools.formats.gps.GpsContainer
import org.devshred.gpstools.formats.gps.GpsContainerMapper
import org.devshred.gpstools.formats.gps.PoiType
import org.devshred.gpstools.formats.gps.WayPoint
import org.devshred.gpstools.formats.gpx.GpxService
import org.devshred.gpstools.formats.gpx.gpxToByteArrayOutputStream
import org.devshred.gpstools.formats.proto.ProtoService
import org.devshred.gpstools.formats.tcx.TrainingCenterDatabase
import org.devshred.gpstools.formats.tcx.tcxToByteArrayOutputStream
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
        optimizeWaypoints: Boolean = false,
    ): InputStream {
        val gpsContainer: GpsContainer =
            getGpsContainer(storageLocation, name, featureCollection)
                .let {
                    it.takeIf { optimizeWaypoints }?.withOptimizedWayPoints() ?: it
                }
        val outputStream = gpxToByteArrayOutputStream(gpsMapper.toGpx(gpsContainer))
        return ByteArrayInputStream(outputStream.toByteArray())
    }

    fun getTcxInputStream(
        storageLocation: String,
        name: String?,
        featureCollection: FeatureCollection?,
        optimizeWaypoints: Boolean = false,
    ): InputStream {
        val gpsContainer: GpsContainer =
            getGpsContainer(storageLocation, name, featureCollection)
                .let {
                    it.takeIf { optimizeWaypoints }?.withOptimizedWayPoints() ?: it
                }
        val tcx: TrainingCenterDatabase = gpsMapper.toTcx(gpsContainer)
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
                        latitude = point.position.y,
                        longitude = point.position.x,
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

    fun getGeoJsonInputStream(
        storageLocation: String,
        name: String?,
        waypoints: FeatureCollection?,
    ): InputStream {
        val gpsContainer: GpsContainer = getGpsContainer(storageLocation, name, waypoints)
        val geoJson = gpsMapper.toGeoJson(gpsContainer)
        val jsonString = FeatureConverter.toStringValue(geoJson)
        return ByteArrayInputStream(jsonString.toByteArray())
    }
}
