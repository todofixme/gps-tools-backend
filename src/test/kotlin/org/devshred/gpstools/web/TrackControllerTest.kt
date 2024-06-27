package org.devshred.gpstools.web

import com.fasterxml.jackson.databind.ObjectMapper
import com.ninjasquad.springmockk.MockkBean
import io.mockk.called
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.apache.commons.lang3.RandomStringUtils.randomAlphabetic
import org.apache.tomcat.util.http.fileupload.impl.SizeLimitExceededException
import org.assertj.core.api.Assertions.assertThat
import org.devshred.gpstools.api.model.TrackDTO
import org.devshred.gpstools.formats.gps.GpsContainer
import org.devshred.gpstools.storage.FileService
import org.devshred.gpstools.storage.IOService
import org.devshred.gpstools.storage.NotFoundException
import org.devshred.gpstools.storage.StoredTrack
import org.devshred.gpstools.storage.TrackStore
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.core.io.InputStreamResource
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType.APPLICATION_XML_VALUE
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.util.UUID

private const val API_PATH_VERSION = "/api/v1"
private const val API_PATH_TRACK = "$API_PATH_VERSION/track"
private const val API_PATH_TRACKS = "$API_PATH_VERSION/tracks"
private const val API_PATH_MERGE = "$API_PATH_VERSION/merge"

@WebMvcTest
class TrackControllerTest(
    @Autowired val mockMvc: MockMvc,
) {
    @MockkBean
    lateinit var trackStore: TrackStore

    @MockkBean
    lateinit var ioService: IOService

    @MockkBean
    lateinit var fileService: FileService

    private val emptyGpsContainer = GpsContainer("empty track", emptyList(), null)

    @Test
    fun `download file as GPX`() {
        val uuid = UUID.randomUUID()
        val storageLocation = "/path/to/file"
        val storedTrack = StoredTrack(uuid, "test", storageLocation)

        every { trackStore.get(uuid) } returns storedTrack
        every { ioService.getAsStream(storageLocation) } returns InputStreamResource(InputStream.nullInputStream())
        every {
            fileService.getGpxInputStream(
                storageLocation,
                null,
                null,
            )
        } returns emptyByteArrayInputStream()

        mockMvc.perform(get("$API_PATH_TRACKS/$uuid?mode=dl").header("Accept", GpsType.GPX.mimeType)) //
            .andExpectAll(
                status().isOk,
                content().contentType(GpsType.GPX.mimeType),
                header().string(HttpHeaders.CONTENT_DISPOSITION, ("attachment; filename=\"test.gpx\"")),
            )

        verify { trackStore.get(uuid) }
    }

    @Test
    fun `download file with name of track as GPX`() {
        val uuid = UUID.randomUUID()
        val storageLocation = "/path/to/file"
        val storedTrack = StoredTrack(uuid, "test", storageLocation)
        val trackName = "My Track"
        val encodedTrackName = "TXkgVHJhY2s="

        every { trackStore.get(uuid) } returns storedTrack
        every { ioService.getAsStream(storageLocation) } returns InputStreamResource(InputStream.nullInputStream())
        every {
            fileService.getGpxInputStream(
                storageLocation,
                trackName,
                null,
            )
        } returns emptyByteArrayInputStream()

        mockMvc.perform(
            get("$API_PATH_TRACKS/$uuid?mode=dl&name=$encodedTrackName").header(
                "Accept",
                GpsType.GPX.mimeType,
            ),
        )
            .andExpectAll(
                status().isOk,
                content().contentType(GpsType.GPX.mimeType),
                header().string(HttpHeaders.CONTENT_DISPOSITION, ("attachment; filename=\"My_Track.gpx\"")),
            )

        verify { trackStore.get(uuid) }
    }

    @Test
    fun `trying to download file that does not exist in store`() {
        val uuid = UUID.randomUUID()

        every { trackStore.get(uuid) } throws NotFoundException("not found")

        mockMvc.perform(get("$API_PATH_TRACKS/$uuid").header("Accept", GpsType.GPX.mimeType))
            .andExpect(status().isNotFound)

        verify { trackStore.get(uuid) }
        verify { ioService.getAsStream(any()) wasNot called }
    }

    @Test
    fun `trying to download file that does not exist in filesystem`() {
        val uuid = UUID.randomUUID()
        val storageLocation = "/path/to/file"
        val storedTrack = StoredTrack(uuid, "test", storageLocation)

        every { trackStore.get(uuid) } returns storedTrack
        every {
            fileService.getGpxInputStream(
                storageLocation,
                null,
                null,
            )
        } throws NotFoundException("not found")

        mockMvc.perform(get("$API_PATH_TRACKS/$uuid").header("Accept", GpsType.GPX.mimeType))
            .andExpect(status().isNotFound)

        verify { trackStore.get(uuid) }
    }

    @Test
    fun `trying to download file with invalid UUID`() {
        val uuid = "not a UUID"

        mockMvc.perform(get("$API_PATH_TRACKS/$uuid")) //
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `get file as GPX`() {
        val uuid = UUID.randomUUID()
        val storageLocation = "/path/to/file"
        val storedTrack = StoredTrack(uuid, "test", storageLocation)

        every { trackStore.get(uuid) } returns storedTrack
        every {
            fileService.getGpxInputStream(
                storageLocation,
                null,
                null,
            )
        } returns emptyByteArrayInputStream()

        mockMvc.perform(get("$API_PATH_TRACKS/$uuid").header("Accept", GpsType.GPX.mimeType))
            .andExpectAll(
                status().isOk,
                content().contentType(GpsType.GPX.mimeType),
                header().doesNotExist(HttpHeaders.CONTENT_DISPOSITION),
            )

        verify { trackStore.get(uuid) }
    }

    @Test
    fun `upload single file`() {
        val trackName = "test"
        val fileName = "$trackName.gpx"
        val uuid = UUID.randomUUID()
        val storageLocation = "/path/to/file"
        val storedTrack = StoredTrack(uuid, trackName, storageLocation)

        every { ioService.createTempFile(any<InputStream>(), fileName) } returns storedTrack
        every { ioService.createTempFile(any<GpsContainer>(), trackName) } returns storedTrack
        every { trackStore.put(any()) } returns Unit
        every { ioService.delete(storageLocation) } returns Unit
        every { fileService.getGpsContainerFromGpxFile(storageLocation) } returns emptyGpsContainer

        mockMvc.perform(
            post(API_PATH_TRACK)
                .param("filename", fileName)
                .content("123"),
        )
            .andExpect(status().isCreated)

        // temporarily store the uploaded file
        verify { ioService.createTempFile(any<InputStream>(), fileName) }
        // store the resulting proto file
        verify { ioService.createTempFile(any<GpsContainer>(), trackName) }
        // store the proto file in the file store
        verify { trackStore.put(any()) }
        // delete the temporary file
        verify { ioService.delete(storageLocation) }
    }

    @Test
    fun `uploading single file fails due wrong file-suffix`() {
        val filename = "test.txt"

        every { ioService.createTempFile(any<GpsContainer>(), filename) } throws IOException()

        mockMvc.perform(
            post(API_PATH_TRACK)
                .param("filename", filename)
                .content("123"),
        )
            .andExpect(status().isBadRequest)

        verify(exactly = 0) { ioService.createTempFile(any<GpsContainer>(), filename) }
        verify(exactly = 0) { trackStore.put(any(), any()) }
    }

    @Test
    fun `uploading single file fails due to IO issues`() {
        val filename = "test.gpx"

        every { ioService.createTempFile(any<InputStream>(), filename) } throws IOException()

        mockMvc.perform(
            post(API_PATH_TRACK)
                .param("filename", filename)
                .content("123"),
        )
            .andExpect(status().isInternalServerError)

        verify { ioService.createTempFile(any<InputStream>(), filename) }
        verify(exactly = 0) { trackStore.put(any(), any()) }
    }

    @Test
    fun `uploading large file fails`() {
        val filename = "test.gpx"

        every {
            ioService.createTempFile(any<InputStream>(), filename)
        } throws SizeLimitExceededException("file too large", 2, 1)

        mockMvc.perform(
            post(API_PATH_TRACK)
                .param("filename", filename)
                .content("123"),
        )
            .andExpect(status().isPayloadTooLarge)

        verify { ioService.createTempFile(any<InputStream>(), filename) }
        verify(exactly = 0) { trackStore.put(any(), any()) }
    }

    @Test
    fun `upload multipartFile`() {
        val trackName = "test"
        val fileName = "$trackName.gpx"
        val uuid = UUID.randomUUID()
        val storageLocation = "/path/to/file"
        val storedTrack = StoredTrack(uuid, fileName, storageLocation)

        val multipartFile =
            MockMultipartFile(
                "file",
                fileName,
                APPLICATION_XML_VALUE,
                "some data".byteInputStream(),
            )

        every { ioService.createTempFile(any<InputStream>(), fileName) } returns storedTrack
        every { ioService.createTempFile(any<GpsContainer>(), trackName) } returns storedTrack
        every { ioService.delete(any()) } returns Unit
        every { trackStore.put(storedTrack) } returns Unit
        every { fileService.getGpsContainerFromGpxFile(storageLocation) } returns mockk<GpsContainer>()

        mockMvc.perform(
            multipart(API_PATH_TRACKS)
                .file(multipartFile),
        )
            .andExpect(status().isOk)

        verify { ioService.createTempFile(any<InputStream>(), fileName) }
        verify { ioService.createTempFile(any<GpsContainer>(), trackName) }
        verify { trackStore.put(storedTrack) }
    }

    @Test
    fun `upload multiple multipartFiles`() {
        val storageLocation = "/path/to/file"

        val trackName1 = "test1"
        val fileName1 = "$trackName1.gpx"
        val uuid1 = UUID.randomUUID()
        val storedTrack1 = StoredTrack(uuid1, fileName1, storageLocation)

        val trackName2 = "test2"
        val fileName2 = "$trackName2.gpx"
        val uuid2 = UUID.randomUUID()
        val storedTrack2 = StoredTrack(uuid2, fileName2, storageLocation)

        val multipartFile1 =
            MockMultipartFile(
                "file",
                fileName1,
                APPLICATION_XML_VALUE,
                "some data".byteInputStream(),
            )
        val multipartFile2 =
            MockMultipartFile(
                "file",
                fileName2,
                APPLICATION_XML_VALUE,
                "some data".byteInputStream(),
            )

        every { ioService.createTempFile(any<InputStream>(), fileName1) } returns storedTrack1
        every { ioService.createTempFile(any<InputStream>(), fileName2) } returns storedTrack2
        every { ioService.createTempFile(any<GpsContainer>(), trackName1) } returns storedTrack1
        every { ioService.createTempFile(any<GpsContainer>(), trackName2) } returns storedTrack2
        every { ioService.delete(any()) } returns Unit
        every { trackStore.put(any()) } returns Unit
        every { fileService.getGpsContainerFromGpxFile(any()) } returns mockk<GpsContainer>()

        mockMvc.perform(
            multipart(API_PATH_TRACKS)
                .file(multipartFile1)
                .file(multipartFile2),
        )
            .andExpect(status().isOk)

        verify { ioService.createTempFile(any<GpsContainer>(), trackName1) }
        verify { ioService.createTempFile(any<GpsContainer>(), trackName2) }
        verify { trackStore.put(storedTrack1) }
        verify { trackStore.put(storedTrack2) }
        verify(exactly = 2) { ioService.delete(any()) }
    }

    @Test
    fun `delete file`() {
        val uuid = UUID.randomUUID()
        val storageLocation = "/path/to/file"
        val storedTrack = StoredTrack(uuid, "test", storageLocation)

        every { trackStore.delete(uuid) } returns storedTrack
        every { ioService.delete(storageLocation) } returns Unit

        mockMvc.perform(delete("$API_PATH_TRACKS/$uuid")) //
            .andExpect(status().isNoContent)

        verify { trackStore.delete(uuid) }
        verify { ioService.delete(storageLocation) }
    }

    @Test
    fun `delete file fails since file is not in store`() {
        val uuid = UUID.randomUUID()
        val storageLocation = "/path/to/file"

        every { trackStore.delete(uuid) } returns null

        mockMvc.perform(delete("$API_PATH_TRACKS/$uuid")) //
            .andExpect(status().isNotFound)

        verify { trackStore.delete(uuid) }
        verify(exactly = 0) { ioService.delete(storageLocation) }
    }

    @Test
    fun `delete file fails since file is in store but not in filesystem`() {
        val uuid = UUID.randomUUID()
        val storageLocation = "/path/to/file"
        val storedTrack = StoredTrack(uuid, "test", storageLocation)

        every { trackStore.delete(uuid) } returns storedTrack
        every { ioService.delete(storageLocation) } throws NotFoundException("not found in filesystem")

        mockMvc.perform(delete("$API_PATH_TRACKS/$uuid")) //
            .andExpect(status().isNotFound)

        verify { trackStore.delete(uuid) }
        verify { ioService.delete(storageLocation) }
    }

    @Test
    fun `merge without trackIds`() {
        mockMvc.perform(post(API_PATH_MERGE)).andExpect(status().isBadRequest)
    }

    @Test
    fun `merge with single trackId simply return the track and does not create a new one`() {
        val uuid = UUID.randomUUID()
        val storageLocation = "/path/to/file"
        val storedTrack = StoredTrack(uuid, "test", storageLocation)

        every { trackStore.get(uuid) } returns storedTrack

        mockMvc.perform(post("$API_PATH_MERGE?trackIds=$uuid")).andExpect(status().isOk)

        verify(exactly = 0) { trackStore.put(any(), any()) }
    }

    @Test
    fun `merge with multiple trackIds creates new track and deletes previous ones`() {
        val trackId1 = UUID.randomUUID()
        val trackId2 = UUID.randomUUID()
        val newTrackId = UUID.randomUUID()
        val storageLocation = "/path/to/file"
        val storedTrack = StoredTrack(trackId1, "test", storageLocation)
        val newFile = StoredTrack(newTrackId, "test", storageLocation)

        every { trackStore.get(any()) } returns storedTrack
        every { fileService.getGpsContainer(storageLocation) } returns emptyGpsContainer
        every { ioService.createTempFile(any<GpsContainer>(), any()) } returns newFile
        every { trackStore.put(newFile) } returns Unit
        every { trackStore.delete(any()) } returns storedTrack
        every { ioService.delete(any()) } returns Unit

        val result =
            mockMvc
                .perform(post("$API_PATH_MERGE?trackIds=$trackId1&trackIds=$trackId2"))
                .andExpect(status().isCreated)
                .andReturn()

        val responseContent = result.response.contentAsString
        val responseDto = ObjectMapper().readValue(responseContent, TrackDTO::class.java)
        assertThat(responseDto.id).isEqualTo(newTrackId)

        verify(exactly = 2) { trackStore.get(any()) }
        verify { trackStore.delete(trackId1) }
        verify { trackStore.delete(trackId2) }
        verify(exactly = 2) { ioService.delete(storageLocation) }
        verify { trackStore.put(any()) }
    }

    @Test
    fun `change the name of a track`() {
        val uuid = UUID.randomUUID()
        val newTrackName = randomAlphabetic(8)

        every { fileService.changeTrackName(uuid, newTrackName) } returns Unit

        mockMvc.perform(
            patch("/api/v1/tracks/$uuid")
                .header("Content-Type", "application/json")
                .content("{\"properties\": { \"name\": \"$newTrackName\" } }"),
        ).andExpect(status().isNoContent)

        verify { fileService.changeTrackName(uuid, newTrackName) }
    }

    @Test
    fun `change the name of a track fails if track name is missing`() {
        val uuid = UUID.randomUUID()

        mockMvc.perform(
            patch("/api/v1/tracks/$uuid")
                .header("Content-Type", "application/json")
                .content("{\"properties\": {} }"),
        ).andExpect(status().isBadRequest)
    }

    @Test
    fun `keep alphanumerical characters`() {
        assertThat("foobar".sanitize()).isEqualTo("foobar")
        assertThat("FooBar123".sanitize()).isEqualTo("FooBar123")
    }

    @Test
    fun `keep umlauts`() {
        assertThat("Fähre".sanitize()).isEqualTo("Fähre")
    }

    @Test
    fun `replace emojis with underscore`() {
        assertThat("f🙂🙃bar".sanitize()).isEqualTo("f__bar")
    }

    @Test
    fun `replace whitespace with underscore`() {
        assertThat("foo bar".sanitize()).isEqualTo("foo_bar")
    }

    @Test
    fun `trim leading and trailing whitespaces`() {
        assertThat(" foobar ".sanitize()).isEqualTo("foobar")
    }

    @Test
    fun `keep some special characters`() {
        assertThat("foo_bar".sanitize()).isEqualTo("foo_bar")
        assertThat("foo-bar".sanitize()).isEqualTo("foo-bar")
        assertThat("foo.bar".sanitize()).isEqualTo("foo.bar")
    }

    @Test
    fun `don't use more than two consecutive underscores`() {
        assertThat("F_O__O___B____A_R".sanitize()).isEqualTo("F_O__O__B__A_R")
    }

    @Test
    fun `remove leading and trailing underscores and hyphens`() {
        assertThat("__foo_bar--".sanitize()).isEqualTo("foo_bar")
        assertThat("--foo_bar__".sanitize()).isEqualTo("foo_bar")
        assertThat("-_foo_bar_-".sanitize()).isEqualTo("foo_bar")
    }

    @Test
    fun `test containsKeyIgnoringCase`() {
        assertThat(mapOf("FOO" to "bar").containsKeyIgnoringCase("FOO")).isTrue()
        assertThat(mapOf("FOO" to "bar").containsKeyIgnoringCase("foo")).isTrue()
        assertThat(mapOf("FOO" to "bar").containsKeyIgnoringCase("BAR")).isFalse()

        assertThat(mapOf("foo" to "bar").containsKeyIgnoringCase("FOO")).isTrue()
        assertThat(mapOf("foo" to "bar").containsKeyIgnoringCase("foo")).isTrue()
        assertThat(mapOf("foo" to "bar").containsKeyIgnoringCase("BAR")).isFalse()
    }

    @Test
    fun `test getIgnoringCase`() {
        assertThat(mapOf("FOO" to "bar").getIgnoringCase("FOO")).isEqualTo("bar")
        assertThat(mapOf("FOO" to "bar").getIgnoringCase("foo")).isEqualTo("bar")
        assertThat(mapOf("FOO" to "bar").getIgnoringCase("BAR")).isNull()

        assertThat(mapOf("foo" to "bar").getIgnoringCase("FOO")).isEqualTo("bar")
        assertThat(mapOf("foo" to "bar").getIgnoringCase("foo")).isEqualTo("bar")
        assertThat(mapOf("foo" to "bar").getIgnoringCase("BAR")).isNull()
    }

    private fun emptyByteArrayInputStream() = ByteArrayInputStream(byteArrayOf())
}
