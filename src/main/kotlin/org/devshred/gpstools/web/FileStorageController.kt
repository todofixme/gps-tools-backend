package org.devshred.gpstools.web

import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.servlet.http.HttpServletRequest
import mil.nga.sf.geojson.FeatureCollection
import org.devshred.gpstools.common.orElse
import org.devshred.gpstools.storage.FileService
import org.devshred.gpstools.storage.FileStore
import org.devshred.gpstools.storage.Filename
import org.devshred.gpstools.storage.IOService
import org.devshred.gpstools.storage.StoredFile
import org.slf4j.LoggerFactory
import org.springframework.core.io.InputStreamResource
import org.springframework.core.io.Resource
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.CrossOrigin
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseBody
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import org.springframework.web.server.ResponseStatusException
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.Base64
import java.util.UUID

private const val ERROR_MSG_FILE_FORMAT_NOT_SUPPORTED = "Not a supported file format (GPX, FIT)."

@CrossOrigin(origins = ["*"], maxAge = 3600)
@RestController
class FileStorageController(
    private val store: FileStore,
    private val ioService: IOService,
    private val fileService: FileService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @GetMapping("/files/{id}")
    @Throws(IOException::class)
    fun download(
        @PathVariable id: String,
        @RequestParam("mode", required = false) mode: Array<String>?,
        @RequestParam("name", required = false) name: String?,
        @RequestParam("type", required = false) type: String?,
        @RequestParam("wp", required = false) waypointsEncoded: String?,
        @RequestHeader headers: Map<String, String>,
    ): ResponseEntity<Resource> {
        val storedFile = store.get(UUID.fromString(id))

        val waypoints: FeatureCollection? =
            waypointsEncoded?.let {
                ObjectMapper().readValue(String(Base64.getDecoder().decode(it)), FeatureCollection::class.java)
            }?.orElse { null }

        val gpsType =
            type?.let {
                GpsType.fromTypeString(it).orElse { throw IllegalArgumentException("unknown type $it") }
            }.orElse {
                if (headers.containsKeyIgnoringCase("accept")) {
                    headers.getIgnoringCase("accept")?.let {
                        GpsType.fromMimeType(it).orElse { throw IllegalArgumentException("invalid accept header $it") }
                    }
                } else {
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

    @ResponseBody
    @PostMapping(path = ["/file"])
    @Throws(IOException::class)
    fun uploadFile(
        request: HttpServletRequest,
        @RequestParam filename: Filename,
    ): ResponseEntity<StoredFile> {
        if (isGpsFile(filename.value)) {
            val uploadedFile: StoredFile = ioService.createTempFile(request.inputStream, filename)

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
                    return ResponseEntity.ok(protoFile)
                } catch (ex: IOException) {
                    ioService.delete(storageLocation = uploadedFile.storageLocation)
                    throw IllegalArgumentException(ERROR_MSG_FILE_FORMAT_NOT_SUPPORTED)
                }
            } else if (isFitFile(filename.value)) {
                val inputStream: InputStream = fileService.getProtoStreamFromFitFile(uploadedFile.storageLocation)
                val protoFile = ioService.createTempFile(inputStream, filename)
                store.put(protoFile.id, protoFile)

                ioService.delete(uploadedFile.storageLocation)
                return ResponseEntity.ok(protoFile)
            }
        } else {
            throw IllegalArgumentException(ERROR_MSG_FILE_FORMAT_NOT_SUPPORTED)
        }

        throw IllegalArgumentException(ERROR_MSG_FILE_FORMAT_NOT_SUPPORTED)
    }

    @ResponseBody
    @PostMapping(
        path = ["/files"],
    )
    @Throws(IOException::class)
    fun uploadFiles(
        @RequestParam file: List<MultipartFile>,
    ): ResponseEntity<List<StoredFile>> {
        val results = ArrayList<StoredFile>()
        file
            .filter { isGpsFile(it.originalFilename) }
            .forEach {
                val uploadedFile: StoredFile = ioService.createTempFile(it.inputStream, Filename(it.originalFilename!!))

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
                        results.add(protoFile)
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
                        results.add(protoFile)
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

    @DeleteMapping("/files/{id}")
    @Throws(IOException::class)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(
        @PathVariable id: String,
    ) {
        store.delete(UUID.fromString(id))?.let {
            ioService.delete(it.storageLocation)
        } ?: throw ResponseStatusException(HttpStatus.NOT_FOUND)
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
