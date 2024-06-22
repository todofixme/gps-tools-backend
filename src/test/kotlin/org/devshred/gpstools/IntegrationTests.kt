package org.devshred.gpstools

import mil.nga.sf.geojson.Feature
import mil.nga.sf.geojson.FeatureConverter
import mil.nga.sf.geojson.Point
import mil.nga.sf.geojson.Position
import org.apache.commons.lang3.RandomStringUtils.randomAlphabetic
import org.assertj.core.api.Assertions.assertThat
import org.devshred.gpstools.api.model.TrackDTO
import org.devshred.gpstools.web.GpsType
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.boot.test.web.client.exchange
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatus.NOT_FOUND
import org.springframework.http.MediaType.APPLICATION_XML
import org.springframework.http.RequestEntity.delete
import org.springframework.http.RequestEntity.get
import org.springframework.http.RequestEntity.patch
import org.springframework.http.RequestEntity.post
import org.springframework.http.RequestEntity.put

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class IntegrationTests(
    @Autowired val restTemplate: TestRestTemplate,
) {
    @Test
    fun `trackFile lifecycle`() {
        val trackName = "Billerhuder Insel"
        val fileName = randomAlphabetic(8) + ".gpx"
        val fileContent = this::class.java.classLoader.getResource("data/test.gpx")!!.readText(Charsets.UTF_8)

        // upload a file
        val createRequest =
            post("/api/v1/track?filename=$fileName")
                .contentType(APPLICATION_XML)
                .body(fileContent)
        val createResponse = restTemplate.exchange<TrackDTO>(createRequest)

        assertThat(createResponse.statusCode).isEqualTo(HttpStatus.CREATED)
        assertThat(createResponse.body).isNotNull
        assertThat(createResponse.body!!.name).isEqualTo(trackName)

        val uuid = createResponse.body!!.id

        // download a file
        val downloadRequest = get("/api/v1/tracks/$uuid").header("Accept", GpsType.GPX.mimeType).build()
        val downloadResponse = restTemplate.exchange<String>(downloadRequest)

        assertThat(downloadResponse.statusCode).isEqualTo(HttpStatus.OK)
        assertThat(downloadResponse.headers.contentType.toString()).isEqualTo(GpsType.GPX.mimeType)
        assertThat(downloadResponse.body).isNotNull()
        // TODO: compare waypoints

        // delete a file
        restTemplate.delete("/api/v1/tracks/$uuid")

        // requesting a deleted file should return 404/not found
        val downloadResponse2 = restTemplate.exchange<String>(downloadRequest)
        assertThat(downloadResponse2.statusCode).isEqualTo(NOT_FOUND)
    }

    @Test
    fun `waypoint lifecycle`() {
        val filename = randomAlphabetic(8) + ".gpx"
        val fileContent = this::class.java.classLoader.getResource("data/test.gpx")!!.readText(Charsets.UTF_8)

        // upload a file
        val createRequest =
            post("/api/v1/track?filename=$filename")
                .header("Content-Type", "application/gpx+xml")
                .body(fileContent)
        val createResponse = restTemplate.exchange<TrackDTO>(createRequest)

        assertThat(createResponse.statusCode).isEqualTo(HttpStatus.CREATED)
        assertThat(createResponse.body).isNotNull
        val uuid = createResponse.body!!.id

        // set waypoint
        val position = Position(53.544225, 10.064383)
        val point = Point(position)
        val firstPoiFeature = Feature(point)
        firstPoiFeature.properties["name"] = "K1"
        firstPoiFeature.properties["type"] = "SUMMIT"

        val initialRequest =
            put("/api/v1/tracks/$uuid/points")
                .header("Content-Type", "application/geo+json")
                .body(FeatureConverter.toStringValue(firstPoiFeature))
        val initialResponse = restTemplate.exchange<String>(initialRequest)

        assertThat(initialResponse.statusCode).isEqualTo(HttpStatus.OK)
        assertThat(initialResponse.body).isNotNull
        val initialResponseFeature = FeatureConverter.toFeatureCollection(initialResponse.body)
        assertThat(initialResponseFeature.features[0].properties["uuid"]).isNotNull

        // overwrite waypoint
        val secondPoiFeature = Feature(point)
        secondPoiFeature.properties["name"] = "K2"
        secondPoiFeature.properties["type"] = "DANGER"

        val overwriteRequest =
            put("/api/v1/tracks/$uuid/points")
                .header("Content-Type", "application/geo+json")
                .body(FeatureConverter.toStringValue(secondPoiFeature))
        val overwriteResponse = restTemplate.exchange<String>(overwriteRequest)

        assertThat(overwriteResponse.statusCode).isEqualTo(HttpStatus.OK)
        assertThat(overwriteResponse.body).isNotNull
        val overwriteResponseFeature = FeatureConverter.toFeatureCollection(overwriteResponse.body)
        assertThat(overwriteResponseFeature.features).hasSize(1)
        assertThat(overwriteResponseFeature.features[0].properties["name"]).isEqualTo("K2")
        assertThat(overwriteResponseFeature.features[0].properties["uuid"]).isNotNull
        val secondPointId = overwriteResponseFeature.features[0].properties["uuid"]

        // change waypoint
        val thirdPoiFeature = Feature(point)
        thirdPoiFeature.properties["name"] = "K3"
        thirdPoiFeature.properties["type"] = "WATER"
        thirdPoiFeature.properties["uuid"] = secondPointId

        val changeRequest =
            patch("/api/v1/tracks/$uuid/points")
                .header("Content-Type", "application/geo+json")
                .body(FeatureConverter.toStringValue(thirdPoiFeature))
        val changeResponse = restTemplate.exchange<String>(changeRequest)

        assertThat(changeResponse.statusCode).isEqualTo(HttpStatus.OK)
        assertThat(changeResponse.body).isNotNull
        val changeResponseFeature = FeatureConverter.toFeatureCollection(changeResponse.body)
        assertThat(changeResponseFeature.features).hasSize(1)
        assertThat(changeResponseFeature.features[0].properties["name"]).isEqualTo("K3")
        assertThat(changeResponseFeature.features[0].properties["uuid"]).isNotNull
        val thirdPointId = changeResponseFeature.features[0].properties["uuid"]
        assertThat(thirdPointId).isEqualTo(secondPointId)

        // add waypoint
        val fourthPoiFeature = Feature(point)
        fourthPoiFeature.properties["name"] = "K4"
        fourthPoiFeature.properties["type"] = "VALLEY"

        val addRequest =
            patch("/api/v1/tracks/$uuid/points")
                .header("Content-Type", "application/geo+json")
                .body(FeatureConverter.toStringValue(fourthPoiFeature))
        val addResponse = restTemplate.exchange<String>(addRequest)

        assertThat(addResponse.statusCode).isEqualTo(HttpStatus.OK)
        assertThat(addResponse.body).isNotNull
        val addResponseFeature = FeatureConverter.toFeatureCollection(addResponse.body)
        assertThat(addResponseFeature.features).hasSize(2)
        assertThat(addResponseFeature.features.map { it.properties["name"] }).containsOnly("K3", "K4")

        // delete waypoint
        val deleteRequest = delete("/api/v1/tracks/$uuid/points/$thirdPointId").build()
        val deleteResponse = restTemplate.exchange<Unit>(deleteRequest)

        assertThat(deleteResponse.statusCode).isEqualTo(HttpStatus.NO_CONTENT)

        // check remaining points
        val getRequest =
            get("/api/v1/tracks/$uuid/points")
                .header("Accept", "application/geo+json")
                .build()
        val getResponse = restTemplate.exchange<String>(getRequest)

        assertThat(getResponse.statusCode).isEqualTo(HttpStatus.OK)
        assertThat(getResponse.body).isNotNull
        val getResponseFeature = FeatureConverter.toFeatureCollection(getResponse.body)
        assertThat(getResponseFeature.features).hasSize(1)
        assertThat(getResponseFeature.features[0].properties["name"]).isEqualTo("K4")
    }
}
