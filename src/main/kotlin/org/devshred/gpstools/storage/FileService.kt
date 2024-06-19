package org.devshred.gpstools.storage

import com.garmin.fit.FitDecoder
import com.garmin.fit.FitRuntimeException
import mil.nga.sf.geojson.FeatureCollection
import mil.nga.sf.geojson.FeatureConverter
import mil.nga.sf.geojson.Point
import org.devshred.gpstools.api.model.FeatureCollectionDTO
import org.devshred.gpstools.api.model.FeatureDTO
import org.devshred.gpstools.api.model.GeoJsonObjectDTO
import org.devshred.gpstools.api.model.PointDTO
import org.devshred.gpstools.formats.gps.GpsContainer
import org.devshred.gpstools.formats.gps.GpsContainerMapper
import org.devshred.gpstools.formats.gps.PoiType
import org.devshred.gpstools.formats.gps.PointOfInterest
import org.devshred.gpstools.formats.gps.mergePoints
import org.devshred.gpstools.formats.gpx.GpxService
import org.devshred.gpstools.formats.gpx.gpxToByteArrayOutputStream
import org.devshred.gpstools.formats.proto.ProtoService
import org.devshred.gpstools.formats.tcx.TrainingCenterDatabase
import org.devshred.gpstools.formats.tcx.tcxToByteArrayOutputStream
import org.springframework.stereotype.Service
import java.io.ByteArrayInputStream
import java.io.FileInputStream
import java.io.InputStream
import java.util.UUID

@Service
class FileService(
    private val store: FileStore,
    private val ioService: IOService,
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
                    it.takeIf { optimizeWaypoints }?.withOptimizedPointsOfInterest() ?: it
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
                    it.takeIf { optimizeWaypoints }?.withOptimizedPointsOfInterest() ?: it
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

    fun getGpsContainer(
        storageLocation: String,
        name: String?,
        featureCollection: FeatureCollection? = null,
    ): GpsContainer {
        val protoGpsContainer = protoService.readProtoContainer(storageLocation, name)

        if (featureCollection != null) {
            val pointsOfInterest =
                featureCollection.features.map { feature ->
                    val point = feature.geometry as Point
                    PointOfInterest(
                        uuid = UUID.fromString(feature.properties["uuid"] as String?) ?: UUID.randomUUID(),
                        latitude = point.position.y,
                        longitude = point.position.x,
                        name = feature.properties["name"] as String?,
                        type = PoiType.fromString((feature.properties["type"] as String?) ?: "GENERIC"),
                    )
                }
            return gpsMapper.fromProto(protoGpsContainer).copy(
                pointsOfInterest = pointsOfInterest,
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

    fun getWaypoints(trackId: UUID): FeatureCollection {
        val file = store.get(trackId)
        val proto = protoService.readProtoContainer(file.storageLocation)
        val gpsContainer = gpsMapper.fromProto(proto)

        return FeatureCollection(gpsMapper.toGeoJsonPoints(gpsContainer.pointsOfInterest))
    }

    fun handleWayPointUpdate(
        trackId: UUID,
        geoJsonObjectDTO: GeoJsonObjectDTO,
        mode: List<String>?,
        merge: Boolean,
    ): FeatureCollection {
        val optimize: Boolean = mode?.contains("opt") ?: false
        val pointsOfInterest: List<PointOfInterest> = getPointsOfInterestFromDto(geoJsonObjectDTO)
        val originalFile = store.get(trackId)
        val originalProto = protoService.readProtoContainer(originalFile.storageLocation)
        val originalGpsContainer = gpsMapper.fromProto(originalProto)

        val updatedWaiPoints =
            if (merge) {
                mergePoints(originalGpsContainer.pointsOfInterest, pointsOfInterest)
            } else {
                pointsOfInterest
            }

        val newGpsContainer =
            originalGpsContainer.copy(pointsOfInterest = updatedWaiPoints)
                .let { it.takeIf { optimize }?.withOptimizedPointsOfInterest() ?: it }
        val newProto = gpsMapper.toProto(newGpsContainer)
        val protoFile = ioService.createTempFile(newProto.toByteArray().inputStream(), originalFile.filename)

        store.put(trackId, protoFile)
        ioService.delete(originalFile.storageLocation)

        return FeatureCollection(gpsMapper.toGeoJsonPoints(updatedWaiPoints))
    }

    private fun getPointsOfInterestFromDto(geoJsonObjectDTO: GeoJsonObjectDTO) =
        when (geoJsonObjectDTO) {
            is FeatureDTO -> getPointOfInterest(geoJsonObjectDTO)
            is FeatureCollectionDTO -> geoJsonObjectDTO.features.flatMap { getPointOfInterest(it) }
            else -> throw IllegalArgumentException("Unknown GeoJsonObjectDTO")
        }

    private fun getPointOfInterest(featureDTO: FeatureDTO): List<PointOfInterest> {
        val geometry = featureDTO.geometry
        return if (geometry is PointDTO) {
            try {
                val propertiesMap = featureDTO.properties as Map<*, *>
                val name =
                    if (propertiesMap.containsKey("name")) {
                        propertiesMap["name"] as String
                    } else {
                        ""
                    }
                val type =
                    if (propertiesMap.containsKey("type")) {
                        PoiType.valueOf(propertiesMap["type"] as String)
                    } else {
                        PoiType.GENERIC
                    }
                val uuid =
                    if (propertiesMap.containsKey("uuid")) {
                        UUID.fromString(propertiesMap["uuid"] as String)
                    } else {
                        UUID.randomUUID()
                    }

                listOf(
                    PointOfInterest(
                        uuid = uuid,
                        latitude = geometry.coordinates[1].toDouble(),
                        longitude = geometry.coordinates[0].toDouble(),
                        type = type,
                        name = name,
                    ),
                )
            } catch (exception: ClassCastException) {
                throw IllegalArgumentException("Failed to read Waypoint: ${exception.message}")
            }
        } else {
            emptyList()
        }
    }

    fun deleteWayPoint(
        trackId: UUID,
        pointId: UUID,
    ) {
        val originalFile = store.get(trackId)
        val originalProto = protoService.readProtoContainer(originalFile.storageLocation)
        val originalGpsContainer = gpsMapper.fromProto(originalProto)

        val newPointsOfInterest = originalGpsContainer.pointsOfInterest.filter { it.uuid != pointId }
        if (newPointsOfInterest.size == originalGpsContainer.pointsOfInterest.size) {
            throw NotFoundException("Waypoints not found")
        }
        val newGpsContainer = originalGpsContainer.copy(pointsOfInterest = newPointsOfInterest)

        val newProto = gpsMapper.toProto(newGpsContainer)
        val protoFile = ioService.createTempFile(newProto.toByteArray().inputStream(), originalFile.filename)

        store.put(trackId, protoFile)
        ioService.delete(originalFile.storageLocation)
    }

    fun changeTrackName(
        trackId: UUID,
        trackName: String,
    ) {
        if (trackName.isEmpty()) {
            throw IllegalArgumentException("Track name must not be empty.")
        }

        val originalFile = store.get(trackId)
        val originalProto = protoService.readProtoContainer(originalFile.storageLocation)
        val originalGpsContainer = gpsMapper.fromProto(originalProto)

        val newGpsContainer = originalGpsContainer.copy(name = trackName)

        val newProto = gpsMapper.toProto(newGpsContainer)
        val protoFile = ioService.createTempFile(newProto.toByteArray().inputStream(), Filename("$trackName.gps"))

        store.put(trackId, protoFile)
        ioService.delete(originalFile.storageLocation)
    }
}
