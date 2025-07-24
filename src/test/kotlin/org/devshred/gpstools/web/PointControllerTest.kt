package org.devshred.gpstools.web

import com.ninjasquad.springmockk.MockkBean
import io.mockk.called
import io.mockk.every
import io.mockk.verify
import mil.nga.sf.geojson.Feature
import mil.nga.sf.geojson.FeatureCollection
import mil.nga.sf.geojson.Point
import mil.nga.sf.geojson.Position
import org.assertj.core.api.Assertions.assertThat
import org.devshred.gpstools.api.model.PointDTO
import org.devshred.gpstools.formats.geojson.GeoJsonResponseAdvice
import org.devshred.gpstools.storage.FileService
import org.devshred.gpstools.storage.IOService
import org.devshred.gpstools.storage.StoredTrack
import org.devshred.gpstools.storage.TrackStore
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.HttpHeaders
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.UUID

@WebMvcTest
@Import(GeoJsonResponseAdvice::class)
class PointControllerTest(
    @param:Autowired val mockMvc: MockMvc,
) {
    @MockkBean
    lateinit var trackStore: TrackStore

    @MockkBean
    lateinit var ioService: IOService

    @MockkBean
    lateinit var fileService: FileService

    @Test
    fun `map GeoJSON point to DTO`() {
        val position = Position(53.544225, 10.064383)
        val point = Point(position)
        val feature = Feature(point)
        feature.properties["name"] = "K1"
        feature.properties["type"] = "SUMMIT"

        val featureDTO = feature.toDto()

        assertThat(featureDTO.type).isEqualTo("Feature")
        assertThat(featureDTO.geometry.type).isEqualTo("Point")
        assertThat(featureDTO.geometry).isInstanceOf(PointDTO::class.java)
        assertThat((featureDTO.geometry as PointDTO).coordinates[0].toDouble()).isEqualTo(53.544225)
        assertThat((featureDTO.geometry as PointDTO).coordinates[1].toDouble()).isEqualTo(10.064383)
        assertThat(featureDTO.properties).isInstanceOf(Map::class.java)
        assertThat((featureDTO.properties as Map<*, *>)["name"]).isEqualTo("K1")
        assertThat((featureDTO.properties as Map<*, *>)["type"]).isEqualTo("SUMMIT")
    }

    @Test
    fun `map collection of GeoJSON points to DTO`() {
        val position1 = Position(53.544225, 10.064383)
        val point1 = Point(position1)
        val feature1 = Feature(point1)
        feature1.properties["name"] = "K1"
        feature1.properties["type"] = "SUMMIT"

        val position2 = Position(63.544225, 20.064383)
        val point2 = Point(position2)
        val feature2 = Feature(point2)
        feature2.properties["name"] = "K2"
        feature2.properties["type"] = "VALLEY"

        val featureCollection = FeatureCollection(listOf(feature1, feature2))

        val featureCollectionDTO = featureCollection.toDto()

        assertThat(featureCollectionDTO.type).isEqualTo("FeatureCollection")
        assertThat(featureCollectionDTO.features).hasSize(2)

        assertThat(featureCollectionDTO.features.map { (it.properties as Map<*, *>)["name"] }).containsOnly("K1", "K2")
    }

    @Test
    fun `get waypoints as GeoJSON`() {
        val trackId = UUID.randomUUID()
        val featureCollection = FeatureCollection()
        val point = Point(Position(44.0, 55.0))
        val feature = Feature(point)
        feature.properties["name"] = "K1"
        feature.properties["type"] = "SUMMIT"
        featureCollection.addFeature(feature)
        every {
            fileService.getWaypoints(trackId)
        } returns featureCollection

        mockMvc
            .perform(
                get("$API_PATH_TRACKS/$trackId/points")
                    .header("Accept", "application/geo+json"),
            ).andExpectAll(
                status().isOk,
                content().contentType("application/geo+json"),
                content().json(
                    """
                    {
                      "type": "FeatureCollection",
                      "features": [
                        {
                          "type": "Feature",
                          "geometry": {
                            "type": "Point",
                            "coordinates": [
                              44,
                              55
                            ]
                          },
                          "properties": {
                            "name": "K1",
                            "type": "SUMMIT"
                          }
                        }
                      ]
                    }
                    """,
                ),
            )

        verify { fileService.getWaypoints(trackId) }
        verify { trackStore.get(any()) wasNot called }
    }

    @Test
    fun `download waypoints as GeoJSON`() {
        val trackId = UUID.randomUUID()
        val featureCollection = FeatureCollection()
        val point = Point(Position(12.0, 34.0))
        val feature = Feature(point)
        feature.properties["name"] = "K1"
        feature.properties["type"] = "SUMMIT"
        featureCollection.addFeature(feature)
        every {
            fileService.getWaypoints(trackId)
        } returns featureCollection
        every {
            trackStore.get(trackId)
        } returns StoredTrack(id = trackId, name = "Test Track", storageLocation = "test/path")

        mockMvc
            .perform(
                get("$API_PATH_TRACKS/$trackId/points?mode=dl")
                    .header("Accept", "application/geo+json"),
            ).andExpectAll(
                status().isOk,
                content().contentType("application/geo+json"),
                header().string(HttpHeaders.CONTENT_DISPOSITION, ("attachment; filename=\"Test_Track.json\"")),
                content().json(
                    """
                    {
                      "type": "FeatureCollection",
                      "features": [
                        {
                          "type": "Feature",
                          "geometry": {
                            "type": "Point",
                            "coordinates": [
                              12,
                              34
                            ]
                          },
                          "properties": {
                            "name": "K1",
                            "type": "SUMMIT"
                          }
                        }
                      ]
                    }
                    """,
                ),
            )

        verify { fileService.getWaypoints(trackId) }
        verify { trackStore.get(trackId) }
    }
}
