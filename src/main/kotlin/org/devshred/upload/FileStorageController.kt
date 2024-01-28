package org.devshred.upload

import jakarta.servlet.http.HttpServletRequest
import kotlinx.serialization.*
import kotlinx.serialization.protobuf.*
import org.apache.tika.mime.MediaType.APPLICATION_XML
import org.slf4j.LoggerFactory
import org.springframework.core.io.InputStreamResource
import org.springframework.core.io.Resource
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.util.StringUtils
import org.springframework.web.bind.annotation.CrossOrigin
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.ExceptionHandler
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
import java.io.ByteArrayInputStream
import java.io.IOException
import java.util.*

@CrossOrigin(origins = ["*"], maxAge = 3600)
@RestController
class FileStorageController(private val store: FileStore, private val ioService: IOService) {
    private val log = LoggerFactory.getLogger(javaClass)

    @GetMapping("/files/{id}")
    @Throws(IOException::class)
    fun download(
        @PathVariable id: String,
        @RequestParam("m", required = false) mode: String?,
        @RequestHeader headers: Map<String, String>
    ): ResponseEntity<Resource> {
        val storedFile = store.get(UUID.fromString(id))
        val resource = ioService.getAsStream(storedFile!!.storageLocation)
        val wayPoints = protoBufInputStreamResourceToWaypoints(resource)

        val outputStream = waiPointsToByteArrayOutputStream(wayPoints)
        val inputStreamResource = InputStreamResource(ByteArrayInputStream(outputStream.toByteArray()))

        val responseHeaders = HttpHeaders()
        if (StringUtils.hasText(mode) && mode == "dl") {
            responseHeaders.set(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"${storedFile.filename}\"")
        }

        return ResponseEntity.ok() //
            .contentType(MediaType.valueOf(storedFile.mimeType)) //
            .headers(responseHeaders)
            .body(inputStreamResource)
    }

    @ResponseBody
    @PostMapping(path = ["/file"])
    @Throws(IOException::class)
    fun uploadFile(request: HttpServletRequest, @RequestParam filename: String): ResponseEntity<StoredFile> {
        val storedFile: StoredFile = ioService.createTempFile(request.inputStream, filename)

        if (storedFile.mimeType != APPLICATION_XML.toString()) {
            ioService.delete(storageLocation = storedFile.storageLocation)
            throw IllegalArgumentException("not a file format to hold GPS data")
        }

        try {
            val wayPoints = wayPointsFromFileLocation(storedFile.storageLocation)
            val inputStream = wayPointsToProtobufInputStream(wayPoints)
            val protobufFile = ioService.createTempFile(inputStream, filename)
            store.put(protobufFile.id, protobufFile)

            ioService.delete(storedFile.storageLocation)
            return ResponseEntity.ok(protobufFile)
        } catch (ex: IOException) {
            ioService.delete(storageLocation = storedFile.storageLocation)
            throw IllegalArgumentException("not a file format to hold GPS data")
        }
    }

    @ResponseBody
    @PostMapping(
        path = ["/files"]
    )
    @Throws(IOException::class)
    fun uploadFiles(@RequestParam file: List<MultipartFile>): ResponseEntity<List<StoredFile>> {
        val results = ArrayList<StoredFile>()
        file.forEach {
            val storedFile: StoredFile = ioService.createTempFile(it.inputStream, it.originalFilename!!)
            store.put(storedFile.id, storedFile)

            val wayPoints = wayPointsFromFileLocation(storedFile.storageLocation)
            val inputStream = wayPointsToProtobufInputStream(wayPoints)
            val protobufFile = ioService.createTempFile(inputStream, it.originalFilename!!)
            store.put(protobufFile.id, protobufFile)

            ioService.delete(storedFile.storageLocation)
            results.add(protobufFile)
        }
        return ResponseEntity.ok(results)
    }

    @DeleteMapping("/files/{id}")
    @Throws(IOException::class)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(@PathVariable id: String) {
        store.delete(UUID.fromString(id))?.let {
            ioService.delete(it.storageLocation)
        } ?: throw ResponseStatusException(HttpStatus.NOT_FOUND)
    }

    @ExceptionHandler
    fun handleIllegalArgumentException(ex: IllegalArgumentException): ResponseEntity<ProblemDetail> {
        val errorMessage: ProblemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.message!!)
        log.info(errorMessage.detail)
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorMessage)
    }

    @ExceptionHandler
    fun handleIOException(ex: IOException): ResponseEntity<ProblemDetail> {
        val errorMessage: ProblemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "IO error")
        log.info(errorMessage.detail)
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorMessage)
    }

    @ExceptionHandler
    fun handleNullPointerException(ex: java.lang.NullPointerException): ResponseEntity<ProblemDetail> {
        val errorMessage: ProblemDetail = ProblemDetail.forStatus(HttpStatus.NOT_FOUND)
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorMessage)
    }

    @ExceptionHandler
    fun handleNotFoundException(ex: NotFoundException): ResponseEntity<ProblemDetail> {
        val errorMessage: ProblemDetail = ProblemDetail.forStatus(HttpStatus.NOT_FOUND)
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorMessage)
    }
}
