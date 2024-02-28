package org.devshred.gpstools.domain

import org.springframework.stereotype.Service
import java.io.ByteArrayInputStream
import java.io.InputStream

@Service
class GpxService(private val ioService: IOService) {
    fun gpxFileToProtobufInputStream(gpxFile: String): InputStream {
        val wayPoints = trackPointsFromFileLocation(gpxFile)
        return trackPointsToProtobufInputStream(wayPoints)
    }

    fun protobufFileToWaypointInputStream(storageLocation: String): ByteArrayInputStream {
        val resource = ioService.getAsStream(storageLocation)
        val wayPoints = protoBufInputStreamResourceToWaypoints(resource)
        val outputStream = waiPointsToByteArrayOutputStream(wayPoints)
        return ByteArrayInputStream(outputStream.toByteArray())
    }

    fun wayPointInputStreamFromFileLocation(storageLocation: String): InputStream {
        val wayPoints = trackPointsFromFileLocation(storageLocation)
        return trackPointsToProtobufInputStream(wayPoints)
    }
}
