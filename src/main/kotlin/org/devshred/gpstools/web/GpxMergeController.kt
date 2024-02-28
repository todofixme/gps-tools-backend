package org.devshred.gpstools.web

import io.jenetics.jpx.GPX
import io.jenetics.jpx.TrackSegment
import org.devshred.gpstools.domain.FileStore
import org.devshred.gpstools.domain.IOService
import org.devshred.gpstools.domain.StoredFile
import org.devshred.gpstools.domain.protoBufInputStreamResourceToWaypoints
import org.devshred.gpstools.domain.trackPointsToProtobufInputStream
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.CrossOrigin
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseBody
import org.springframework.web.bind.annotation.RestController
import java.io.IOException
import java.util.UUID

@CrossOrigin(origins = ["*"], maxAge = 3600)
@RestController
@RequestMapping("/merge")
class GpxMergeController(private val store: FileStore, private val ioService: IOService) {
    private val log = LoggerFactory.getLogger(javaClass)

    @ResponseBody
    @PostMapping
    @Throws(IOException::class)
    fun merge(
        @RequestParam(name = "fileId") fileIds: List<UUID>,
    ): ResponseEntity<StoredFile> {
        val segmentBuilder = TrackSegment.builder()
        fileIds.forEach { uuid ->
            log.info("merging $uuid")
            val wayPoints =
                protoBufInputStreamResourceToWaypoints(ioService.getAsStream(store.get(uuid).storageLocation))
            wayPoints.forEach { segmentBuilder.addPoint(it) }
        }
        val gpx = GPX.builder().addTrack { track -> track.addSegment(segmentBuilder.build()) }.build()

        val protoStream = trackPointsToProtobufInputStream(gpx.tracks[0].segments[0].points)
        val protobufFile = ioService.createTempFile(protoStream, "merged.gpx")
        store.put(protobufFile.id, protobufFile)

        return ResponseEntity.ok(protobufFile)
    }
}
