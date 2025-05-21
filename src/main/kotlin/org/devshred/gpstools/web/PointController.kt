package org.devshred.gpstools.web

import mil.nga.sf.geojson.Feature
import mil.nga.sf.geojson.FeatureCollection
import mil.nga.sf.geojson.GeometryType
import mil.nga.sf.geojson.Point
import org.devshred.gpstools.api.PointsApi
import org.devshred.gpstools.api.model.FeatureCollectionDTO
import org.devshred.gpstools.api.model.FeatureDTO
import org.devshred.gpstools.api.model.GeoJsonObjectDTO
import org.devshred.gpstools.api.model.PointDTO
import org.devshred.gpstools.storage.FileService
import org.devshred.gpstools.storage.TrackStore
import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.CrossOrigin
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.io.File
import java.math.BigDecimal
import java.util.UUID

@RestController
@RequestMapping("/api/v1")
@CrossOrigin(origins = ["*"], maxAge = 3600)
class PointController(
    private val fileService: FileService,
    private val trackStore: TrackStore,
) : PointsApi {
    @Suppress("ktlint")
    override fun getPoints(
        trackId: UUID,
        mode: List<String>?
    ): ResponseEntity<GeoJsonObjectDTO> {
        val responseHeaders = HttpHeaders()
        if (mode != null && mode.contains("dl")) {
            val file = File(trackStore.get(trackId).name)
            val filename =
                file.nameWithoutExtension
                    .sanitize()
                    .ifBlank { "waypoints" }
            responseHeaders.set(
                HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"$filename.json\"",
            )
        }
        responseHeaders.set(HttpHeaders.CONTENT_TYPE, "application/geo+json")

        return ResponseEntity
            .ok() //
            .headers(responseHeaders) //
            .body(fileService.getWaypoints(trackId).toDto())
    }

    @LockTrack
    override fun changePoints(
        trackId: UUID,
        geoJsonObjectDTO: GeoJsonObjectDTO,
        mode: List<String>?,
    ): ResponseEntity<GeoJsonObjectDTO> =
        ResponseEntity.ok(fileService.handleWayPointUpdate(trackId, geoJsonObjectDTO, mode, false).toDto())

    @LockTrack
    override fun addPoints(
        trackId: UUID,
        geoJsonObjectDTO: GeoJsonObjectDTO,
        mode: List<String>?,
    ): ResponseEntity<GeoJsonObjectDTO> =
        ResponseEntity
            .ok(fileService.handleWayPointUpdate(trackId, geoJsonObjectDTO, mode, true).toDto())

    @LockTrack
    override fun deletePoint(
        trackId: UUID,
        pointId: UUID,
    ): ResponseEntity<Unit> {
        fileService.deleteWayPoint(trackId, pointId)
        return ResponseEntity.noContent().build()
    }
}

fun FeatureCollection.toDto(): FeatureCollectionDTO =
    FeatureCollectionDTO(
        features = this.features.map { it.toDto() },
        type = "FeatureCollection",
    )

fun Feature.toDto(): FeatureDTO {
    if (geometryType == GeometryType.POINT) {
        val point = geometry as Point
        val pointDTO =
            PointDTO(
                coordinates = listOf(BigDecimal.valueOf(point.coordinates.x), BigDecimal.valueOf(point.coordinates.y)),
                type = "Point",
            )
        return FeatureDTO(
            geometry = pointDTO,
            properties = properties,
            type = "Feature",
        )
    }

    throw UnsupportedOperationException("Geometry $geometryType not implemented yet.")
}
