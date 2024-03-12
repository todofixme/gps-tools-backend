package org.devshred.gpstools.domain.gpx

import org.devshred.gpstools.domain.IOService
import org.devshred.gpstools.domain.proto.gpxToProtobufInputStream
import org.devshred.gpstools.domain.proto.protoInputStreamResourceToGpx
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
