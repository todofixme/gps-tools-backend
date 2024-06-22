package org.devshred.gpstools.storage

import org.devshred.gpstools.formats.gps.GpsContainer
import org.devshred.gpstools.formats.gps.GpsContainerMapper
import org.slf4j.LoggerFactory
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
class IOService(val gpsMapper: GpsContainerMapper) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun getAsStream(storageLocation: String): InputStreamResource {
        try {
            val inputStreamResource = InputStreamResource(FileInputStream(storageLocation))
            log.info("Retrieved {}", storageLocation)
            return inputStreamResource
        } catch (e: Exception) {
            throw NotFoundException("File not found at $storageLocation")
        }
    }

    fun createTempFile(
        gpsContainer: GpsContainer,
        name: String?,
    ): StoredTrack {
        val protoContainer = gpsMapper.toProto(gpsContainer)
        val inputStream = protoContainer.toByteArray().inputStream()
        return createTempFile(inputStream, gpsContainer.name ?: name ?: "unnamed")
    }

    fun createTempFile(
        inputStream: InputStream,
        name: String,
    ): StoredTrack {
        val uuid = UUID.randomUUID()
        val tempFile = File.createTempFile(uuid.toString(), ".tmp")
        tempFile.deleteOnExit()
        try {
            inputStream.use {
                Files.copy(it, tempFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
            log.info("Copied $name to ${tempFile.absoluteFile}")
        } catch (ioe: IOException) {
            throw IOException("Could not save file: " + tempFile.getName(), ioe)
        }

        return StoredTrack(uuid, name, tempFile.absolutePath)
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
