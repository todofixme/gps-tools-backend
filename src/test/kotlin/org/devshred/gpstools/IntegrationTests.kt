package org.devshred.gpstools

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
import org.springframework.http.RequestEntity.get
import org.springframework.http.RequestEntity.post

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class IntegrationTests(
    @Autowired val restTemplate: TestRestTemplate,
) {
    @Test
    fun `trackFile lifecycle`() {
        val filename = randomAlphabetic(8) + ".gpx"
        val fileContent = this::class.java.classLoader.getResource("data/test.gpx")!!.readText(Charsets.UTF_8)

        // upload a file
        val createRequest =
            post("/api/v1/track?filename=$filename")
                .contentType(APPLICATION_XML)
                .body(fileContent)
        val createResponse = restTemplate.exchange<TrackDTO>(createRequest)

        assertThat(createResponse.statusCode).isEqualTo(HttpStatus.OK)
        assertThat(createResponse.body).isNotNull
        assertThat(createResponse.body!!.filename).isEqualTo(filename)

        val uuid = createResponse.body!!.id

        // download a file
        val downloadRequest = get("/files/$uuid").header("Accept", GpsType.GPX.mimeType).build()
        val downloadResponse = restTemplate.exchange<String>(downloadRequest)

        assertThat(downloadResponse.headers.contentType.toString()).isEqualTo(GpsType.GPX.mimeType)
        assertThat(downloadResponse.body).isNotNull()
        // TODO: compare waypoints

        // delete a file
        restTemplate.delete("/files/$uuid")

        // requesting a deleted file should return 404/not found
        val downloadResponse2 = restTemplate.exchange<String>(downloadRequest)
        assertThat(downloadResponse2.statusCode).isEqualTo(NOT_FOUND)
    }
}
