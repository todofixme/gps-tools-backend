package org.devshred.gpstools.web

import io.jenetics.jpx.WayPoint
import org.devshred.gpstools.domain.FileStore
import org.devshred.gpstools.domain.IOService
import org.devshred.gpstools.domain.NotFoundException
import org.devshred.gpstools.domain.StoredFile
import org.devshred.gpstools.domain.gpx.buildGpx
import org.devshred.gpstools.domain.proto.ProtoService
import org.devshred.gpstools.domain.proto.toGpx
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
class GpxMergeController(
    private val store: FileStore,
    private val ioService: IOService,
    private val protoService: ProtoService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @ResponseBody
    @PostMapping
    @Throws(IOException::class)
    fun merge(
        @RequestParam(name = "fileId") fileIds: List<UUID>,
    ): ResponseEntity<StoredFile> {
        if (fileIds.isEmpty()) {
            throw NotFoundException("No fileId provided.")
        }

        if (fileIds.size == 1) {
            log.info("No need to merge.")
            return ResponseEntity.ok(store.get(fileIds[0]))
        }

        val allWayPoints: MutableList<WayPoint> = mutableListOf()
        val allTrackPoints: MutableList<WayPoint> = mutableListOf()
        var trackName: String? = null
        fileIds.forEachIndexed { index, uuid ->
            log.info("About to merge $uuid.")
            val protoGpsContainer =
                protoService.readProtoGpsContainer(store.get(uuid).storageLocation)
            allWayPoints.addAll(protoGpsContainer.wayPointsList.map { it.toGpx() })
            allTrackPoints.addAll(protoGpsContainer.track.wayPointsList.map { it.toGpx() })
            if (index == 0 && protoGpsContainer.name.isNotEmpty()) {
                trackName = protoGpsContainer.name
            }
        }
        // TODO: 2021-10-14: [Refactor] remove useless GPX-conversion
        val gpx = buildGpx(trackName, allWayPoints, allTrackPoints)
        val protoStream = protoService.gpxToProtobufInputStream(gpx)
        val protoFile = ioService.createTempFile(protoStream, "merged.gpx")
        store.put(protoFile.id, protoFile)

        return ResponseEntity.ok(protoFile)
    }
}
