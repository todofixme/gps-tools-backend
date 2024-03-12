package org.devshred.gpstools.web

import jakarta.servlet.http.HttpServletRequest
import org.devshred.gpstools.domain.FileStore
import org.devshred.gpstools.domain.IOService
import org.devshred.gpstools.domain.StoredFile
import org.devshred.gpstools.domain.common.orElse
import org.devshred.gpstools.domain.gpx.GpxService
import org.devshred.gpstools.domain.tcx.TcxService
import org.springframework.core.io.InputStreamResource
import org.springframework.core.io.Resource
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.util.StringUtils
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
import java.util.UUID

private const val ERROR_MSG_NO_GPS_FILE = "Not a file format to hold GPS data."

@CrossOrigin(origins = ["*"], maxAge = 3600)
@RestController
class FileStorageController(
    private val store: FileStore,
    private val ioService: IOService,
    private val gpxService: GpxService,
    private val tcxService: TcxService,
) {
    @GetMapping("/files/{id}")
    @Throws(IOException::class)
    fun download(
        @PathVariable id: String,
        @RequestParam("mode", required = false) mode: String?,
        @RequestParam("name", required = false) name: String?,
        @RequestParam("type", required = false) type: String?,
        @RequestHeader headers: Map<String, String>,
    ): ResponseEntity<Resource> {
        val storedFile = store.get(UUID.fromString(id))

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

        val inputStream =
            when (gpsType) {
                GpsType.GPX -> gpxService.protoFileToGpxInputStream(storedFile.storageLocation, name)
                GpsType.TCX -> tcxService.protoFileToTcxInputStream(storedFile.storageLocation, name)
                else -> {
                    throw IllegalArgumentException("$gpsType is not supported yet")
                }
            }
        val inputStreamResource = InputStreamResource(inputStream)

        val responseHeaders = HttpHeaders()
        if (StringUtils.hasText(mode) && mode == "dl") {
            val basename = name.orElse { storedFile.filename }
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
        @RequestParam filename: String,
    ): ResponseEntity<StoredFile> {
        if (!isGpsFile(filename)) {
            throw IllegalArgumentException(ERROR_MSG_NO_GPS_FILE)
        }

        val uploadedFile: StoredFile = ioService.createTempFile(request.inputStream, filename)

        if (uploadedFile.mimeType != org.apache.tika.mime.MediaType.APPLICATION_XML.toString()) {
            ioService.delete(storageLocation = uploadedFile.storageLocation)
            throw IllegalArgumentException(ERROR_MSG_NO_GPS_FILE)
        }

        try {
            val inputStream = gpxService.protoInputStreamFromFileLocation(uploadedFile.storageLocation)
            val protoFile = ioService.createTempFile(inputStream, filename)
            store.put(protoFile.id, protoFile)

            ioService.delete(uploadedFile.storageLocation)
            return ResponseEntity.ok(protoFile)
        } catch (ex: IOException) {
            ioService.delete(storageLocation = uploadedFile.storageLocation)
            throw IllegalArgumentException(ERROR_MSG_NO_GPS_FILE)
        }
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
            .filter { isGpsFile(it.originalFilename!!) }
            .forEach {
                val uploadedFile: StoredFile = ioService.createTempFile(it.inputStream, it.originalFilename!!)

                try {
                    if (uploadedFile.mimeType != org.apache.tika.mime.MediaType.APPLICATION_XML.toString()) {
                        ioService.delete(storageLocation = uploadedFile.storageLocation)
                        throw IllegalArgumentException(ERROR_MSG_NO_GPS_FILE)
                    }

                    val inputStream = gpxService.protoInputStreamFromFileLocation(uploadedFile.storageLocation)
                    val protoFile = ioService.createTempFile(inputStream, it.originalFilename!!)
                    store.put(protoFile.id, protoFile)
                    ioService.delete(uploadedFile.storageLocation)
                    results.add(protoFile)
                } catch (ex: IOException) {
                    ioService.delete(storageLocation = uploadedFile.storageLocation)
                    throw IllegalArgumentException(ERROR_MSG_NO_GPS_FILE)
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

private fun isGpsFile(filename: String) = filename.endsWith(".gpx")

private val notAllowedCharacters = "[^a-zA-Z0-9\\p{L}\\p{M}*\\p{N}.\\-]".toRegex()
private val moreThan2UnderscoresInARow = "_{3,}".toRegex()
private const val TWO_UNDERSCORES = "__"
private val DEFAULT_GPS_TYPE = GpsType.GPX

fun String.sanitize(): String {
    return this
        .trim()
        .replace(notAllowedCharacters, "_")
        .replace(moreThan2UnderscoresInARow, TWO_UNDERSCORES)
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
