package org.devshred.gpstools.domain

import org.springframework.stereotype.Service
import java.io.ByteArrayInputStream
import java.io.InputStream

@Service
class GpxService(private val ioService: IOService) {
    fun protoFileToGpxInputStream(
        storageLocation: String,
        name: String?,
    ): ByteArrayInputStream {
        val stream = ioService.getAsStream(storageLocation)
        val gpx = protoInputStreamResourceToGpx(stream, name)
        val outputStream = gpxToByteArrayOutputStream(gpx)
        return ByteArrayInputStream(outputStream.toByteArray())
    }

    fun protoInputStreamFromFileLocation(fileLocation: String): InputStream {
        val gpx = gpxFromFileLocation(fileLocation)
        return gpxToProtobufInputStream(gpx)
    }
}
