package org.devshred.upload

import org.apache.tika.Tika
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.io.InputStreamResource
import org.springframework.stereotype.Service
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.UUID

@Service
class IOService {
    private val log = LoggerFactory.getLogger(javaClass)

    @Value("\${app.base-url}")
    lateinit var baseUrl: String

    fun getAsStream(storageLocation: String): InputStreamResource {
        try {
            val inputStreamResource = InputStreamResource(FileInputStream(storageLocation))
            log.info("Retrieved {}", storageLocation)
            return inputStreamResource
        } catch (e: Exception) {
            throw NotFoundException("File not found at $storageLocation")
        }
    }

    fun createTempFile(inputStream: InputStream, filename: String): StoredFile {
        val uuid = UUID.randomUUID()
        val tempFile = File.createTempFile(uuid.toString(), ".tmp")
        tempFile.deleteOnExit()
        try {
            inputStream.use {
                Files.copy(it, tempFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
            log.info("Copied $filename to ${tempFile.absoluteFile}")
        } catch (ioe: IOException) {
            throw IOException("Could not save file: " + tempFile.getName(), ioe)
        }
        val mimeType: String = Tika().detect(tempFile)
        val href = "${baseUrl}/files/$uuid"

        return StoredFile(uuid, filename, mimeType, href, tempFile.length(), tempFile.absolutePath)
    }

    fun delete(storageLocation: String) {
        if (Files.deleteIfExists(Path.of(storageLocation))) {
            log.info("Deleted file {} from filesystem.", storageLocation)
        } else {
            log.error("File not found in filesystem: {}", storageLocation)
            throw NotFoundException("not found in filesystem")
        }
    }
}
