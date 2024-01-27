package org.devshred.upload

import io.jenetics.jpx.GPX
import io.jenetics.jpx.TrackSegment
import org.slf4j.LoggerFactory
import org.springframework.core.io.InputStreamResource
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.CrossOrigin
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseBody
import org.springframework.web.bind.annotation.RestController
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.util.*

@CrossOrigin(origins = ["*"], maxAge = 3600)
@RestController
@RequestMapping("/merge")
class GpxMergeController(private val store: FileStore, private val ioService: IOService) {
    private val log = LoggerFactory.getLogger(javaClass)

    @ResponseBody
    @PostMapping
    @Throws(IOException::class)
    fun merge(@RequestParam(name = "fileId") fileIds: List<UUID>): ResponseEntity<StoredFile> {
        val segmentBuilder = TrackSegment.builder()
        fileIds.forEach { uuid ->
            log.info("merging $uuid")
            val wayPoints =
                protoBufInputStreamResourceToWaypoints(ioService.getAsStream(store.get(uuid)!!.storageLocation))
            wayPoints.forEach { segmentBuilder.addPoint(it) }
        }
        val gpx = GPX.builder().addTrack { track -> track.addSegment(segmentBuilder.build()) }.build()
        val gpxFile = File.createTempFile(UUID.randomUUID().toString(), ".tmp")
        GPX.write(gpx, gpxFile.toPath())
        val inputStream = InputStreamResource(FileInputStream(gpxFile.absolutePath)).inputStream

        val mergedGpx = ioService.createTempFile(inputStream, "merged.gpx")
        store.put(mergedGpx.id, mergedGpx)

        gpxFile.delete()

        return ResponseEntity.ok(mergedGpx)
    }
}
