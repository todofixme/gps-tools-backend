package org.devshred.gpstools.domain.gpx

import org.devshred.gpstools.domain.proto.ProtoService
import org.devshred.gpstools.domain.proto.toGpx
import org.springframework.stereotype.Service
import java.io.ByteArrayInputStream
import java.io.InputStream

@Service
class GpxService(private val protoService: ProtoService) {
    fun protoFileToGpxInputStream(
        storageLocation: String,
        name: String?,
    ): ByteArrayInputStream {
        val protoGpsContainer = protoService.readProtoGpsContainer(storageLocation, name)
        val outputStream = gpxToByteArrayOutputStream(protoGpsContainer.toGpx())
        return ByteArrayInputStream(outputStream.toByteArray())
    }

    fun protoInputStreamFromFileLocation(fileLocation: String): InputStream {
        val gpx = gpxFromFileLocation(fileLocation)
        return protoService.gpxToProtobufInputStream(gpx)
    }
}
