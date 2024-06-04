package org.devshred.gpstools.web

import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.validation.Valid
import jakarta.validation.constraints.NotNull
import mil.nga.sf.geojson.Feature
import mil.nga.sf.geojson.FeatureCollection
import mil.nga.sf.geojson.GeometryType
import mil.nga.sf.geojson.Point
import org.devshred.gpstools.api.TracksApi
import org.devshred.gpstools.api.model.FeatureCollectionDTO
import org.devshred.gpstools.api.model.FeatureDTO
import org.devshred.gpstools.api.model.GeoJsonObjectDTO
import org.devshred.gpstools.api.model.PointDTO
import org.devshred.gpstools.api.model.TrackDTO
import org.devshred.gpstools.common.orElse
import org.devshred.gpstools.formats.gps.GpsContainerMapper
import org.devshred.gpstools.formats.proto.ProtoService
import org.devshred.gpstools.formats.proto.protoContainer
import org.devshred.gpstools.formats.proto.protoTrack
import org.devshred.gpstools.storage.FileService
import org.devshred.gpstools.storage.FileStore
import org.devshred.gpstools.storage.Filename
import org.devshred.gpstools.storage.IOService
import org.devshred.gpstools.storage.NotFoundException
import org.devshred.gpstools.storage.StoredFile
import org.slf4j.LoggerFactory
import org.springframework.core.io.InputStreamResource
import org.springframework.core.io.Resource
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.CrossOrigin
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.math.BigDecimal
import java.util.Base64
import java.util.UUID

private const val ERROR_MSG_FILE_FORMAT_NOT_SUPPORTED = "Not a supported file format (GPX, FIT)."

@CrossOrigin(origins = ["*"], maxAge = 3600)
@RestController
class TrackController(
    private val store: FileStore,
    private val ioService: IOService,
    private val fileService: FileService,
    private val protoService: ProtoService,
    private val gpsContainerMapper: GpsContainerMapper,
) : TracksApi {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun download(
        @PathVariable(value = "id") id: UUID,
        @Valid @RequestParam(required = false, value = "mode") mode: List<String>?,
        @Valid @RequestParam(required = false, value = "name") name: String?,
        @Valid @RequestParam(required = false, value = "type") type: String?,
        @Valid @RequestParam(required = false, value = "wp") wp: String?,
        @RequestHeader(required = false, value = "accept") accept: String?,
    ): ResponseEntity<Resource> {
        val storedFile = store.get(id)

        val waypoints: FeatureCollection? =
            wp?.let {
                ObjectMapper().readValue(String(Base64.getDecoder().decode(it)), FeatureCollection::class.java)
            }?.orElse { null }

        val gpsType =
            type?.let {
                GpsType.fromTypeString(it).orElse { throw IllegalArgumentException("unknown type $it") }
            }.orElse {
                accept?.let {
                    GpsType.fromMimeType(it).orElse {
                        if (it == "*/*") {
                            DEFAULT_GPS_TYPE
                        } else {
                            throw IllegalArgumentException("invalid accept header $it")
                        }
                    }
                }.orElse {
                    DEFAULT_GPS_TYPE
                }
            }

        val optimize: Boolean = mode?.contains("opt") ?: false

        val trackName = name?.let { String(Base64.getDecoder().decode(name)) }

        val inputStream =
            when (gpsType) {
                GpsType.GPX -> fileService.getGpxInputStream(storedFile.storageLocation, trackName, waypoints, optimize)
                GpsType.TCX -> fileService.getTcxInputStream(storedFile.storageLocation, trackName, waypoints, optimize)
                GpsType.JSON -> fileService.getGeoJsonInputStream(storedFile.storageLocation, trackName, waypoints)
                else -> {
                    throw IllegalArgumentException("$gpsType is not supported yet")
                }
            }
        val inputStreamResource = InputStreamResource(inputStream)

        val responseHeaders = HttpHeaders()
        if (mode != null && mode.contains("dl")) {
            val basename = trackName.orElse { storedFile.filename.value }
            val file = File(basename)
            val filename =
                file.nameWithoutExtension
                    .sanitize()
                    .ifBlank { "unnamed" }
            val extension = gpsType.name.lowercase()
            responseHeaders.set(
                HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"$filename.$extension\"",
            )
        }

        return ResponseEntity.ok() //
            .contentType(MediaType.valueOf(gpsType.mimeType)) //
            .headers(responseHeaders)
            .body(inputStreamResource)
    }

    override fun uploadFile(
        @NotNull @Valid @RequestParam(required = true, value = "filename") filename: Filename,
        @Valid @RequestBody body: Resource,
    ): ResponseEntity<TrackDTO> {
        if (isGpsFile(filename.value)) {
            val uploadedFile: StoredFile = ioService.createTempFile(body.inputStream, filename)

            if (isGpxFile(filename.value)) {
                if (uploadedFile.mimeType != org.apache.tika.mime.MediaType.APPLICATION_XML.toString()) {
                    ioService.delete(storageLocation = uploadedFile.storageLocation)
                    throw IllegalArgumentException(ERROR_MSG_FILE_FORMAT_NOT_SUPPORTED)
                }

                try {
                    val inputStream: InputStream = fileService.getProtoStreamFromGpxFile(uploadedFile.storageLocation)
                    val protoFile = ioService.createTempFile(inputStream, filename)
                    store.put(protoFile.id, protoFile)

                    ioService.delete(uploadedFile.storageLocation)
                    return ResponseEntity.ok(protoFile.toTrackDTO())
                } catch (ex: IOException) {
                    ioService.delete(storageLocation = uploadedFile.storageLocation)
                    throw IllegalArgumentException(ERROR_MSG_FILE_FORMAT_NOT_SUPPORTED)
                }
            } else if (isFitFile(filename.value)) {
                val inputStream: InputStream = fileService.getProtoStreamFromFitFile(uploadedFile.storageLocation)
                val protoFile = ioService.createTempFile(inputStream, filename)
                store.put(protoFile.id, protoFile)

                ioService.delete(uploadedFile.storageLocation)
                return ResponseEntity.ok(protoFile.toTrackDTO())
            }
        } else {
            throw IllegalArgumentException(ERROR_MSG_FILE_FORMAT_NOT_SUPPORTED)
        }

        throw IllegalArgumentException(ERROR_MSG_FILE_FORMAT_NOT_SUPPORTED)
    }

    override fun uploadFiles(
        @RequestParam(
            required = false,
            value = "file",
        ) file: List<MultipartFile>?,
    ): ResponseEntity<List<TrackDTO>> {
        val results = ArrayList<TrackDTO>()

        file
            ?.filter { isGpsFile(it.originalFilename) }
            ?.forEach {
                val uploadedFile: StoredFile =
                    ioService.createTempFile(it.inputStream, Filename(it.originalFilename!!))

                if (isGpxFile(it.originalFilename)) {
                    try {
                        if (uploadedFile.mimeType != org.apache.tika.mime.MediaType.APPLICATION_XML.toString()) {
                            ioService.delete(storageLocation = uploadedFile.storageLocation)
                            throw IllegalArgumentException(ERROR_MSG_FILE_FORMAT_NOT_SUPPORTED)
                        }

                        val inputStream = fileService.getProtoStreamFromGpxFile(uploadedFile.storageLocation)
                        val protoFile = ioService.createTempFile(inputStream, Filename(it.originalFilename!!))
                        store.put(protoFile.id, protoFile)
                        ioService.delete(uploadedFile.storageLocation)
                        results.add(protoFile.toTrackDTO())
                    } catch (ex: IOException) {
                        ioService.delete(storageLocation = uploadedFile.storageLocation)
                        throw IllegalArgumentException(ERROR_MSG_FILE_FORMAT_NOT_SUPPORTED)
                    }
                } else if (isFitFile(it.originalFilename)) {
                    try {
                        val inputStream: InputStream =
                            fileService.getProtoStreamFromFitFile(uploadedFile.storageLocation)
                        val protoFile = ioService.createTempFile(inputStream, Filename(it.originalFilename!!))
                        store.put(protoFile.id, protoFile)
                        ioService.delete(uploadedFile.storageLocation)
                        results.add(protoFile.toTrackDTO())
                    } catch (e: Exception) {
                        log.warn("Failed to process FIT file.", e)
                        ioService.delete(storageLocation = uploadedFile.storageLocation)
                        throw IllegalArgumentException(ERROR_MSG_FILE_FORMAT_NOT_SUPPORTED)
                    }
                }
            }
        if (results.isEmpty()) {
            throw IllegalArgumentException("Failed to upload files.")
        }
        return ResponseEntity.ok(results)
    }

    override fun delete(id: UUID): ResponseEntity<Unit> {
        store.delete(id)?.let {
            ioService.delete(it.storageLocation)
        } ?: throw NotFoundException("File with ID $id not found.")

        return ResponseEntity.noContent().build()
    }

    override fun merge(trackIds: List<UUID>): ResponseEntity<TrackDTO> {
        if (trackIds.isEmpty()) {
            throw NotFoundException("No fileId provided.")
        }

        if (trackIds.size == 1) {
            log.info("No need to merge.")
            return ResponseEntity.ok(store.get(trackIds[0]).toTrackDTO())
        }

        val allWayPoints: MutableList<org.devshred.gpstools.formats.proto.ProtoWayPoint> = mutableListOf()
        val allTrackPoints: MutableList<org.devshred.gpstools.formats.proto.ProtoWayPoint> = mutableListOf()
        var trackName: String? = null
        trackIds.forEachIndexed { index, uuid ->
            log.info("About to merge $uuid.")
            val protoGpsContainer =
                protoService.readProtoContainer(store.get(uuid).storageLocation)
            allWayPoints.addAll(protoGpsContainer.wayPointsList)
            allTrackPoints.addAll(protoGpsContainer.track.wayPointsList)
            if (index == 0 && protoGpsContainer.name.isNotEmpty()) {
                trackName = protoGpsContainer.name
            }
        }
        val mergedProto =
            protoContainer {
                trackName?.run { name = this }
                wayPoints.addAll(allWayPoints)
                track = protoTrack { wayPoints.addAll(allTrackPoints) }
            }
        val protoFile = ioService.createTempFile(mergedProto.toByteArray().inputStream(), Filename("merged.gpx"))
        store.put(protoFile.id, protoFile)

        return ResponseEntity.ok(protoFile.toTrackDTO())
    }

    override fun changePoints(
        id: UUID,
        geoJsonObjectDTO: GeoJsonObjectDTO,
        mode: List<String>?,
    ): ResponseEntity<GeoJsonObjectDTO> {
        return ResponseEntity.ok(fileService.handleWayPointUpdate(id, geoJsonObjectDTO, mode, false).toDto())
    }

    override fun addPoints(
        id: UUID,
        geoJsonObjectDTO: GeoJsonObjectDTO,
        mode: List<String>?,
    ): ResponseEntity<GeoJsonObjectDTO> {
        return ResponseEntity.ok(fileService.handleWayPointUpdate(id, geoJsonObjectDTO, mode, true).toDto())
    }
}

private fun isGpxFile(filename: String?) = filename?.endsWith(".gpx").orElse { false }

private fun isFitFile(filename: String?) = filename?.endsWith(".fit").orElse { false }

private fun isGpsFile(filename: String?) = isGpxFile(filename) || isFitFile(filename)

private val notAllowedCharacters = "[^a-zA-Z0-9\\p{L}\\p{M}*\\p{N}.\\-]".toRegex()
private val moreThan2UnderscoresInARow = "_{3,}".toRegex()
private val leadingUnderscoresAndHyphens = "^[-_]+".toRegex()
private val trailingUnderscoresAndHyphens = "[-_]+$".toRegex()
private const val TWO_UNDERSCORES = "__"
private val DEFAULT_GPS_TYPE = GpsType.GPX

fun String.sanitize(): String {
    return this
        .trim()
        .replace(notAllowedCharacters, "_")
        .replace(moreThan2UnderscoresInARow, TWO_UNDERSCORES)
        .replace(leadingUnderscoresAndHyphens, "")
        .replace(trailingUnderscoresAndHyphens, "")
}

fun Map<String, Any>.containsKeyIgnoringCase(other: String): Boolean {
    return any { it.key.equals(other, ignoreCase = true) }
}

fun <V> Map<String, V>.getIgnoringCase(other: String): V? {
    return this.filterKeys {
        it.equals(other, ignoreCase = true)
    }
        .asSequence().firstOrNull()
        ?.value
        .orElse { null }
}

fun FeatureCollection.toDto(): FeatureCollectionDTO {
    return FeatureCollectionDTO(
        features = this.features.map { it.toDto() },
        type = "FeatureCollection",
    )
}

fun Feature.toDto(): FeatureDTO {
    if (geometryType == GeometryType.POINT) {
        val point = geometry as Point
        val pointDTO =
            PointDTO(
                coordinates = listOf(BigDecimal.valueOf(point.coordinates.x), BigDecimal.valueOf(point.coordinates.y)),
                type = "Point",
            )
        return FeatureDTO(
            geometry = pointDTO,
            properties = properties,
            type = "Feature",
        )
    }

    throw UnsupportedOperationException("Geometry $geometryType not implemented yet.")
}
