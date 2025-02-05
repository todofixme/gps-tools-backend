package org.devshred.gpstools

import mil.nga.sf.geojson.Feature
import mil.nga.sf.geojson.FeatureConverter
import mil.nga.sf.geojson.Point
import mil.nga.sf.geojson.Position
import org.assertj.core.api.Assertions.assertThat
import org.devshred.gpstools.api.model.TrackDTO
import org.devshred.gpstools.web.GpsType
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType.MULTIPART_FORM_DATA
import org.springframework.http.client.MultipartBodyBuilder
import org.springframework.http.client.reactive.ClientHttpRequest
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.web.reactive.function.BodyInserter
import org.springframework.web.reactive.function.BodyInserters
import kotlin.collections.set

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class IntegrationTests(
    @Autowired val testClient: WebTestClient,
) {
    @Test
    fun `trackFile lifecycle`() {
        // upload a file as multipart
        val createResponse =
            testClient
                .post()
                .uri("/api/v1/tracks")
                .contentType(MULTIPART_FORM_DATA)
                .body(multipartInserterFromFile("data/test.gpx"))
                .exchange()
                .expectStatus()
                .isOk
                .expectBody(Array<TrackDTO>::class.java)
                .value { assertThat(it[0].name).isEqualTo("Billerhuder Insel") }
                .returnResult()

        val uuid = createResponse.responseBody!![0].id

        // download a file
        testClient
            .get()
            .uri("/api/v1/tracks/$uuid")
            .header("Accept", GpsType.GPX.mimeType)
            .exchange()
            .expectStatus()
            .isOk
            .expectHeader()
            .contentType(GpsType.GPX.mimeType)
            .expectBody()
            .consumeWith {
                assertThat(it.responseBody).isNotNull()
            }

        // TODO: compare waypoints

        // delete a file
        testClient
            .delete()
            .uri("/api/v1/tracks/$uuid")
            .exchange()
            .expectStatus()
            .isNoContent

        // requesting a deleted file should return 404/not found
        testClient
            .delete()
            .uri("/api/v1/tracks/$uuid")
            .exchange()
            .expectStatus()
            .isNotFound
    }

    @Test
    fun `waypoint lifecycle`() {
        // upload a file as multipart
        val createResponse =
            testClient
                .post()
                .uri("/api/v1/tracks")
                .contentType(MULTIPART_FORM_DATA)
                .body(multipartInserterFromFile("data/test.gpx"))
                .exchange()
                .expectStatus()
                .isOk
                .expectBody(Array<TrackDTO>::class.java)
                .value { assertThat(it[0].name).isEqualTo("Billerhuder Insel") }
                .returnResult()

        val uuid = createResponse.responseBody!![0].id

        // set waypoint
        val position = Position(53.544225, 10.064383)
        val point = Point(position)
        val firstPoiFeature = Feature(point)
        firstPoiFeature.properties["name"] = "K1"
        firstPoiFeature.properties["type"] = "SUMMIT"

        val initialResponse =
            testClient
                .put()
                .uri("/api/v1/tracks/$uuid/points")
                .header("Content-Type", "application/geo+json")
                .bodyValue(FeatureConverter.toStringValue(firstPoiFeature))
                .exchange()
                .expectStatus()
                .isOk
                .expectBody(String::class.java)
                .returnResult()

        val initialResponseFeature = FeatureConverter.toFeatureCollection(initialResponse.responseBody)
        assertThat(initialResponseFeature.features[0].properties["uuid"]).isNotNull

        // overwrite waypoint
        val secondPoiFeature = Feature(point)
        secondPoiFeature.properties["name"] = "K2"
        secondPoiFeature.properties["type"] = "DANGER"

        val overwriteResponse =
            testClient
                .put()
                .uri("/api/v1/tracks/$uuid/points")
                .header("Content-Type", "application/geo+json")
                .bodyValue(FeatureConverter.toStringValue(secondPoiFeature))
                .exchange()
                .expectStatus()
                .isOk
                .expectBody(String::class.java)
                .returnResult()

        val overwriteResponseFeature = FeatureConverter.toFeatureCollection(overwriteResponse.responseBody)
        assertThat(overwriteResponseFeature.features).hasSize(1)
        assertThat(overwriteResponseFeature.features[0].properties["name"]).isEqualTo("K2")
        assertThat(overwriteResponseFeature.features[0].properties["uuid"]).isNotNull
        val secondPointId = overwriteResponseFeature.features[0].properties["uuid"]

        // change waypoint
        val thirdPoiFeature = Feature(point)
        thirdPoiFeature.properties["name"] = "K3"
        thirdPoiFeature.properties["type"] = "WATER"
        thirdPoiFeature.properties["uuid"] = secondPointId

        val changeResponse =
            testClient
                .patch()
                .uri("/api/v1/tracks/$uuid/points")
                .header("Content-Type", "application/geo+json")
                .bodyValue(FeatureConverter.toStringValue(thirdPoiFeature))
                .exchange()
                .expectStatus()
                .isOk
                .expectBody(String::class.java)
                .returnResult()

        val changeResponseFeature = FeatureConverter.toFeatureCollection(changeResponse.responseBody)
        assertThat(changeResponseFeature.features).hasSize(1)
        assertThat(changeResponseFeature.features[0].properties["name"]).isEqualTo("K3")
        assertThat(changeResponseFeature.features[0].properties["uuid"]).isNotNull
        val thirdPointId = changeResponseFeature.features[0].properties["uuid"]
        assertThat(thirdPointId).isEqualTo(secondPointId)

        // add waypoint
        val fourthPoiFeature = Feature(point)
        fourthPoiFeature.properties["name"] = "K4"
        fourthPoiFeature.properties["type"] = "VALLEY"

        val addResponse =
            testClient
                .patch()
                .uri("/api/v1/tracks/$uuid/points")
                .header("Content-Type", "application/geo+json")
                .bodyValue(FeatureConverter.toStringValue(fourthPoiFeature))
                .exchange()
                .expectStatus()
                .isOk
                .expectBody(String::class.java)
                .returnResult()

        val addResponseFeature = FeatureConverter.toFeatureCollection(addResponse.responseBody)
        assertThat(addResponseFeature.features).hasSize(2)
        assertThat(addResponseFeature.features.map { it.properties["name"] }).containsOnly("K3", "K4")

        // delete waypoint
        testClient
            .delete()
            .uri("/api/v1/tracks/$uuid/points/$thirdPointId")
            .exchange()
            .expectStatus()
            .isNoContent

        // check remaining points
        val getResponse =
            testClient
                .get()
                .uri("/api/v1/tracks/$uuid/points")
                .header("Accept", "application/geo+json")
                .exchange()
                .expectStatus()
                .isOk
                .expectBody(String::class.java)
                .returnResult()

        val getResponseFeature = FeatureConverter.toFeatureCollection(getResponse.responseBody)
        assertThat(getResponseFeature.features).hasSize(1)
        assertThat(getResponseFeature.features[0].properties["name"]).isEqualTo("K4")
    }

    private fun multipartInserterFromFile(filename: String): BodyInserter<*, in ClientHttpRequest> {
        val fileContent =
            this::class.java.classLoader
                .getResource(filename)!!
                .readText(Charsets.UTF_8)
        val multipartData =
            MultipartBodyBuilder()
                .apply {
                    part("file", fileContent)
                        .header("Content-Disposition", "form-data; name=\"file\"; filename=\"test.gpx\"")
                }.build()
        return BodyInserters.fromMultipartData(multipartData)
    }
}
