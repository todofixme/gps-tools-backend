package org.devshred.gpstools.web

import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.validation.Valid
import jakarta.validation.constraints.NotNull
import mil.nga.sf.geojson.FeatureCollection
import org.devshred.gpstools.api.TracksApi
import org.devshred.gpstools.api.model.ChangeNameRequestDTO
import org.devshred.gpstools.api.model.TrackDTO
import org.devshred.gpstools.common.orElse
import org.devshred.gpstools.formats.gps.GpsContainer
import org.devshred.gpstools.formats.gps.PointOfInterest
import org.devshred.gpstools.formats.gps.Track
import org.devshred.gpstools.formats.gps.TrackPoint
import org.devshred.gpstools.storage.FileService
import org.devshred.gpstools.storage.IOService
import org.devshred.gpstools.storage.NotFoundException
import org.devshred.gpstools.storage.StoredTrack
import org.devshred.gpstools.storage.TrackStore
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
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
import java.net.URI
import java.util.Base64
import java.util.UUID

private const val ERROR_MSG_FILE_FORMAT_NOT_SUPPORTED = "Not a supported file format (GPX, FIT)."

@CrossOrigin(origins = ["*"], maxAge = 3600)
@RestController
class TrackController(
    private val trackStore: TrackStore,
    private val ioService: IOService,
    private val fileService: FileService,
) : TracksApi {
    private val log = LoggerFactory.getLogger(javaClass)

    @Value("\${app.base-url}")
    lateinit var baseUrl: String

    override fun download(
        @PathVariable(value = "trackId") trackId: UUID,
        @Valid @RequestParam(required = false, value = "mode") mode: List<String>?,
        @Valid @RequestParam(required = false, value = "name") name: String?,
        @Valid @RequestParam(required = false, value = "type") type: String?,
        @Valid @RequestParam(required = false, value = "wp") wp: String?,
        @RequestHeader(required = false, value = "accept") accept: String?,
    ): ResponseEntity<Resource> {
        val storedFile = trackStore.get(trackId)

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
            val basename = trackName.orElse { storedFile.name }
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
        @NotNull @Valid @RequestParam(required = true, value = "filename") filename: String,
        @Valid @RequestBody body: Resource,
    ): ResponseEntity<TrackDTO> {
        if (isGpsFile(filename)) {
            val uploadedFile: StoredTrack = ioService.createTempFile(body.inputStream, filename)

            if (isGpxFile(filename)) {
                try {
                    val gpsContainer: GpsContainer =
                        fileService.getGpsContainerFromGpxFile(uploadedFile.storageLocation)
                    val storedTrack = ioService.createTempFile(gpsContainer, filename.removeSuffix(".gpx"))

                    trackStore.put(storedTrack)

                    ioService.delete(uploadedFile.storageLocation)
                    return ResponseEntity.created(trackUrl(storedTrack.id)).body(storedTrack.toTrackDTO())
                } catch (ex: IOException) {
                    ioService.delete(storageLocation = uploadedFile.storageLocation)
                    throw IllegalArgumentException(ERROR_MSG_FILE_FORMAT_NOT_SUPPORTED)
                }
            } else if (isFitFile(filename)) {
                val gpsContainer: GpsContainer = fileService.getGpsContainerFromFitFile(uploadedFile.storageLocation)
                val storedTrack = ioService.createTempFile(gpsContainer, filename.removeSuffix(".fit"))
                trackStore.put(storedTrack)

                ioService.delete(uploadedFile.storageLocation)
                return ResponseEntity.created(trackUrl(storedTrack.id)).body(storedTrack.toTrackDTO())
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
                val uploadedFile: StoredTrack =
                    ioService.createTempFile(it.inputStream, it.originalFilename!!)

                if (isGpxFile(it.originalFilename)) {
                    try {
                        val gpsContainer = fileService.getGpsContainerFromGpxFile(uploadedFile.storageLocation)
                        val storedTrack =
                            ioService.createTempFile(gpsContainer, it.originalFilename!!.removeSuffix(".gpx"))
                        trackStore.put(storedTrack)

                        ioService.delete(uploadedFile.storageLocation)

                        results.add(storedTrack.toTrackDTO())
                    } catch (ex: IOException) {
                        ioService.delete(storageLocation = uploadedFile.storageLocation)
                        throw IllegalArgumentException(ERROR_MSG_FILE_FORMAT_NOT_SUPPORTED)
                    }
                } else if (isFitFile(it.originalFilename)) {
                    try {
                        val gpsContainer = fileService.getGpsContainerFromFitFile(uploadedFile.storageLocation)
                        val storedTrack =
                            ioService.createTempFile(gpsContainer, it.originalFilename!!.removeSuffix(".fit"))
                        trackStore.put(storedTrack)

                        ioService.delete(uploadedFile.storageLocation)
                        results.add(storedTrack.toTrackDTO())
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

    override fun delete(trackId: UUID): ResponseEntity<Unit> {
        trackStore.delete(trackId)?.let {
            ioService.delete(it.storageLocation)
        } ?: throw NotFoundException("Track with ID $trackId not found.")

        return ResponseEntity.noContent().build()
    }

    override fun merge(trackIds: List<UUID>): ResponseEntity<TrackDTO> {
        if (trackIds.isEmpty()) {
            throw NotFoundException("No fileId provided.")
        }

        if (trackIds.size == 1) {
            log.info("No need to merge.")
            val storedTrack = trackStore.get(trackIds[0])
            return ResponseEntity.ok(storedTrack.toTrackDTO())
        }

        val allWayPoints: MutableList<PointOfInterest> = mutableListOf()
        val allTrackPoints: MutableList<TrackPoint> = mutableListOf()
        var trackName: String? = null
        trackIds.forEachIndexed { index, uuid ->
            log.info("About to merge $uuid.")
            val storedTrack = trackStore.get(uuid)
            val gpsContainer = fileService.getGpsContainer(storedTrack.storageLocation)
            allWayPoints.addAll(gpsContainer.pointsOfInterest)
            allTrackPoints.addAll(gpsContainer.track?.trackPoints ?: emptyList())
            if (index == 0 && gpsContainer.name?.isNotBlank() == true) {
                trackName = gpsContainer.name
            }
        }
        val mergedTrackName = trackName?.orElse { "merged" }
        val mergedGpsContainer = GpsContainer(mergedTrackName, allWayPoints, Track(allTrackPoints))
        val mergedTrack = ioService.createTempFile(mergedGpsContainer, mergedTrackName)
        trackStore.put(mergedTrack)

        trackIds.forEach { trackId ->
            trackStore.delete(trackId)?.let {
                ioService.delete(it.storageLocation)
            }
        }

        return ResponseEntity.created(trackUrl(mergedTrack.id)).body(mergedTrack.toTrackDTO())
    }

    @LockTrack
    override fun changeName(
        @PathVariable(value = "trackId") trackId: UUID,
        @Valid @RequestBody changeNameRequestDTO: ChangeNameRequestDTO,
    ): ResponseEntity<Unit> {
        val trackName: String? = changeNameRequestDTO.properties?.name
        trackName?.let {
            fileService.changeTrackName(trackId, it)
            return ResponseEntity.noContent().build()
        } ?: throw IllegalArgumentException("No track name provided.")
    }

    private fun trackUrl(id: UUID): URI {
        return URI.create("$baseUrl/api/v1/tracks/$id")
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
