package org.devshred.gpstools.web

import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.validation.Valid
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
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import java.io.File
import java.net.URI
import java.util.Base64
import java.util.UUID

private const val ERROR_MSG_FILE_FORMAT_NOT_SUPPORTED = "Not a supported file format (GPX, FIT)."

@RestController
@RequestMapping("/api/v1")
@CrossOrigin(origins = ["*"], maxAge = 3600)
class TrackController(
    private val trackStore: TrackStore,
    private val ioService: IOService,
    private val fileService: FileService,
) : TracksApi {
    private val log = LoggerFactory.getLogger(javaClass)

    @Value("\${app.base-url}")
    lateinit var baseUrl: String

    override fun download(
        @PathVariable("trackId") trackId: UUID,
        @RequestParam(value = "mode", required = false) mode: List<String>?,
        @RequestParam(value = "name", required = false) name: String?,
        @RequestParam(value = "type", required = false) type: String?,
        @RequestParam(value = "wp", required = false) wp: String?,
        @RequestHeader(value = "accept", required = false) accept: String?,
    ): ResponseEntity<Resource> {
        val storedFile = trackStore.get(trackId)

        val waypoints: FeatureCollection? =
            wp
                ?.let {
                    ObjectMapper().readValue(String(Base64.getDecoder().decode(it)), FeatureCollection::class.java)
                }?.orElse { null }

        val gpsType =
            type
                ?.let {
                    GpsType.fromTypeString(it).orElse { throw IllegalArgumentException("unknown type $it") }
                }.orElse {
                    accept
                        ?.let {
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
        val waypointsOnly: Boolean = mode?.contains("wpo") ?: false

        val trackName = name?.let { String(Base64.getDecoder().decode(name)) }

        val inputStream =
            when (gpsType) {
                GpsType.GPX -> fileService.getGpxInputStream(storedFile.storageLocation, trackName, waypoints, optimize)
                GpsType.TCX -> fileService.getTcxInputStream(storedFile.storageLocation, trackName, waypoints, optimize)
                GpsType.JSON ->
                    fileService.getGeoJsonInputStream(
                        storedFile.storageLocation,
                        trackName,
                        waypoints,
                        waypointsOnly,
                    )

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

        return ResponseEntity
            .ok() //
            .contentType(MediaType.valueOf(gpsType.mimeType)) //
            .headers(responseHeaders)
            .body(inputStreamResource)
    }

    override fun uploadFiles(
        @RequestParam(required = false, value = "file") file: Array<MultipartFile>,
    ): ResponseEntity<List<TrackDTO>> {
        val results =
            file
                .filter { isGpsFile(it.originalFilename) }
                .mapNotNull {
                    val uploadedFile = ioService.createTempFile(it.inputStream, it.originalFilename!!)
                    try {
                        val gpsContainer =
                            when {
                                isGpxFile(it.originalFilename) -> fileService.getGpsContainerFromGpxFile(uploadedFile.storageLocation)
                                isFitFile(it.originalFilename) -> fileService.getGpsContainerFromFitFile(uploadedFile.storageLocation)
                                isTcxFile(it.originalFilename) -> fileService.getGpsContainerFromTcxFile(uploadedFile.storageLocation)
                                else -> throw IllegalArgumentException(ERROR_MSG_FILE_FORMAT_NOT_SUPPORTED)
                            }
                        val storedTrack =
                            ioService.createTempFile(
                                gpsContainer,
                                it.originalFilename!!.removeSuffix(".${it.originalFilename!!.substringAfterLast('.')}"),
                            )
                        trackStore.put(storedTrack)
                        ioService.delete(uploadedFile.storageLocation)
                        storedTrack.toTrackDTO()
                    } catch (e: Exception) {
                        log.warn("Failed to process file: ${it.originalFilename}", e)
                        ioService.delete(uploadedFile.storageLocation)
                        null
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

    private fun trackUrl(id: UUID): URI = URI.create("$baseUrl/api/v1/tracks/$id")
}

private fun isGpxFile(filename: String?) = filename?.endsWith(".gpx").orElse { false }

private fun isFitFile(filename: String?) = filename?.endsWith(".fit").orElse { false }

private fun isTcxFile(filename: String?) = filename?.endsWith(".tcx").orElse { false }

private fun isGpsFile(filename: String?) = isGpxFile(filename) || isFitFile(filename) || isTcxFile(filename)

private val notAllowedCharacters = "[^a-zA-Z0-9\\p{L}\\p{M}*\\p{N}.\\-]".toRegex()
private val moreThan2UnderscoresInARow = "_{3,}".toRegex()
private val leadingUnderscoresAndHyphens = "^[-_]+".toRegex()
private val trailingUnderscoresAndHyphens = "[-_]+$".toRegex()
private const val TWO_UNDERSCORES = "__"
private val DEFAULT_GPS_TYPE = GpsType.GPX

fun String.sanitize(): String =
    this
        .trim()
        .replace(notAllowedCharacters, "_")
        .replace(moreThan2UnderscoresInARow, TWO_UNDERSCORES)
        .replace(leadingUnderscoresAndHyphens, "")
        .replace(trailingUnderscoresAndHyphens, "")

fun Map<String, Any>.containsKeyIgnoringCase(other: String): Boolean = any { it.key.equals(other, ignoreCase = true) }

fun <V> Map<String, V>.getIgnoringCase(other: String): V? =
    this
        .filterKeys {
            it.equals(other, ignoreCase = true)
        }.asSequence()
        .firstOrNull()
        ?.value
        .orElse { null }
