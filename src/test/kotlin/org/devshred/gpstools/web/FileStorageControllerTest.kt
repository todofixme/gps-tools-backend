package org.devshred.gpstools.web

import com.ninjasquad.springmockk.MockkBean
import io.mockk.called
import io.mockk.every
import io.mockk.verify
import org.apache.commons.io.input.NullInputStream
import org.apache.tomcat.util.http.fileupload.impl.SizeLimitExceededException
import org.assertj.core.api.Assertions.assertThat
import org.devshred.gpstools.formats.gps.GpsContainerMapper
import org.devshred.gpstools.formats.gpx.GpxService
import org.devshred.gpstools.formats.proto.ProtoService
import org.devshred.gpstools.storage.FileService
import org.devshred.gpstools.storage.FileStore
import org.devshred.gpstools.storage.Filename
import org.devshred.gpstools.storage.IOService
import org.devshred.gpstools.storage.NotFoundException
import org.devshred.gpstools.storage.StoredFile
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.core.io.InputStreamResource
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType.APPLICATION_XML_VALUE
import org.springframework.http.MediaType.TEXT_PLAIN_VALUE
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.util.UUID

@WebMvcTest
class FileStorageControllerTest(
    @Autowired val mockMvc: MockMvc,
) {
    @MockkBean
    lateinit var fileStore: FileStore

    @MockkBean
    lateinit var ioService: IOService

    @MockkBean
    lateinit var fileService: FileService

    @MockkBean
    lateinit var protoService: ProtoService

    @MockkBean
    lateinit var gpxService: GpxService

    @MockkBean
    lateinit var gpsMapper: GpsContainerMapper

    @Test
    fun `download file as GPX`() {
        val uuid = UUID.randomUUID()
        val storageLocation = "/path/to/file"
        val storedFile = StoredFile(uuid, Filename("test.gpx"), APPLICATION_XML_VALUE, "href", 123, storageLocation)

        every { fileStore.get(uuid) } returns storedFile
        every { ioService.getAsStream(storageLocation) } returns InputStreamResource(InputStream.nullInputStream())
        every {
            fileService.getGpxInputStream(
                storageLocation,
                null,
                null,
            )
        } returns emptyByteArrayInputStream()

        mockMvc.perform(get("/files/$uuid?mode=dl").header("Accept", GpsType.GPX.mimeType)) //
            .andExpectAll(
                status().isOk,
                content().contentType(GpsType.GPX.mimeType),
                header().string(HttpHeaders.CONTENT_DISPOSITION, ("attachment; filename=\"test.gpx\"")),
            )

        verify { fileStore.get(uuid) }
    }

    @Test
    fun `download file with trackname as GPX`() {
        val uuid = UUID.randomUUID()
        val storageLocation = "/path/to/file"
        val storedFile = StoredFile(uuid, Filename("test.gpx"), APPLICATION_XML_VALUE, "href", 123, storageLocation)
        val trackname = "My Track"

        every { fileStore.get(uuid) } returns storedFile
        every { ioService.getAsStream(storageLocation) } returns InputStreamResource(InputStream.nullInputStream())
        every {
            fileService.getGpxInputStream(
                storageLocation,
                trackname,
                null,
            )
        } returns emptyByteArrayInputStream()

        mockMvc.perform(get("/files/$uuid?mode=dl&name=$trackname").header("Accept", GpsType.GPX.mimeType))
            .andExpectAll(
                status().isOk,
                content().contentType(GpsType.GPX.mimeType),
                header().string(HttpHeaders.CONTENT_DISPOSITION, ("attachment; filename=\"My_Track.gpx\"")),
            )

        verify { fileStore.get(uuid) }
    }

    @Test
    fun `trying to download file that does not exist in store`() {
        val uuid = UUID.randomUUID()

        every { fileStore.get(uuid) } throws NotFoundException("not found")

        mockMvc.perform(get("/files/$uuid").header("Accept", GpsType.GPX.mimeType))
            .andExpect(status().isNotFound)

        verify { fileStore.get(uuid) }
        verify { ioService.getAsStream(any()) wasNot called }
    }

    @Test
    fun `trying to download file that does not exist in filesystem`() {
        val uuid = UUID.randomUUID()
        val storageLocation = "/path/to/file"
        val storedFile = StoredFile(uuid, Filename("test.gpx"), TEXT_PLAIN_VALUE, "href", 123, storageLocation)

        every { fileStore.get(uuid) } returns storedFile
        every {
            fileService.getGpxInputStream(
                storageLocation,
                null,
                null,
            )
        } throws NotFoundException("not found")

        mockMvc.perform(get("/files/$uuid").header("Accept", GpsType.GPX.mimeType))
            .andExpect(status().isNotFound)

        verify { fileStore.get(uuid) }
    }

    @Test
    fun `trying to download file with invalid UUID`() {
        val uuid = "not a UUID"

        mockMvc.perform(get("/files/$uuid")) //
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `get file as GPX`() {
        val uuid = UUID.randomUUID()
        val storageLocation = "/path/to/file"
        val storedFile = StoredFile(uuid, Filename("test.txt"), TEXT_PLAIN_VALUE, "href", 123, storageLocation)

        every { fileStore.get(uuid) } returns storedFile
        every {
            fileService.getGpxInputStream(
                storageLocation,
                null,
                null,
            )
        } returns emptyByteArrayInputStream()

        mockMvc.perform(get("/files/$uuid").header("Accept", GpsType.GPX.mimeType))
            .andExpectAll(
                status().isOk,
                content().contentType(GpsType.GPX.mimeType),
                header().doesNotExist(HttpHeaders.CONTENT_DISPOSITION),
            )

        verify { fileStore.get(uuid) }
    }

    @Test
    fun `upload single file`() {
        val filename = "test.gpx"
        val uuid = UUID.randomUUID()
        val storageLocation = "/path/to/file"
        val storedFile = StoredFile(uuid, Filename(filename), APPLICATION_XML_VALUE, "href", 123, storageLocation)

        every { ioService.createTempFile(any(), Filename(filename)) } returns storedFile
        every { fileStore.put(uuid, storedFile) } returns Unit
        every { ioService.delete(storageLocation) } returns Unit
        every { fileService.getProtoStreamFromGpxFile(storageLocation) } returns NullInputStream()

        mockMvc.perform(
            post("/file")
                .param("filename", filename)
                .content("123"),
        )
            .andExpect(status().isOk)

        verify(exactly = 2) { ioService.createTempFile(any(), Filename(filename)) }
        verify { fileStore.put(uuid, storedFile) }
        verify { ioService.delete(storageLocation) }
    }

    @Test
    fun `uploading single file fails due wrong file-suffix`() {
        val filename = "test.txt"

        every { ioService.createTempFile(any(), Filename(filename)) } throws IOException()

        mockMvc.perform(
            post("/file")
                .param("filename", filename)
                .content("123"),
        )
            .andExpect(status().isBadRequest)

        verify(exactly = 0) { ioService.createTempFile(any(), Filename(filename)) }
        verify(exactly = 0) { fileStore.put(any(), any()) }
    }

    @Test
    fun `uploading single file fails due to IO issues`() {
        val filename = "test.gpx"

        every { ioService.createTempFile(any(), Filename(filename)) } throws IOException()

        mockMvc.perform(
            post("/file")
                .param("filename", filename)
                .content("123"),
        )
            .andExpect(status().isInternalServerError)

        verify { ioService.createTempFile(any(), Filename(filename)) }
        verify(exactly = 0) { fileStore.put(any(), any()) }
    }

    @Test
    fun `uploading large file fails`() {
        val filename = "test.gpx"

        every {
            ioService.createTempFile(
                any(),
                Filename(filename),
            )
        } throws SizeLimitExceededException("file too large", 2, 1)

        mockMvc.perform(
            post("/file")
                .param("filename", filename)
                .content("123"),
        )
            .andExpect(status().isPayloadTooLarge)

        verify { ioService.createTempFile(any(), Filename(filename)) }
        verify(exactly = 0) { fileStore.put(any(), any()) }
    }

    @Test
    fun `upload multipartFile`() {
        val filename = "test.gpx"
        val uuid = UUID.randomUUID()
        val storageLocation = "/path/to/file"
        val storedFile =
            StoredFile(
                uuid,
                Filename(filename),
                APPLICATION_XML_VALUE,
                "href",
                123,
                storageLocation,
            )

        val multipartFile =
            MockMultipartFile(
                "file",
                filename,
                APPLICATION_XML_VALUE,
                "some data".byteInputStream(),
            )

        every { ioService.createTempFile(any(), Filename(filename)) } returns storedFile
        every { ioService.delete(any()) } returns Unit
        every { fileStore.put(uuid, storedFile) } returns Unit
        every { fileService.getProtoStreamFromGpxFile(storageLocation) } returns emptyByteArrayInputStream()

        mockMvc.perform(
            multipart("/files")
                .file(multipartFile),
        )
            .andExpect(status().isOk)

        verify { ioService.createTempFile(any(), Filename(filename)) }
        verify { fileStore.put(uuid, storedFile) }
    }

    @Test
    fun `upload multiple multipartFiles`() {
        val storageLocation = "/path/to/file"

        val filename1 = "test1.gpx"
        val uuid1 = UUID.randomUUID()
        val storedFile1 = StoredFile(uuid1, Filename(filename1), APPLICATION_XML_VALUE, "href", 123, storageLocation)

        val filename2 = "test2.gpx"
        val uuid2 = UUID.randomUUID()
        val storedFile2 = StoredFile(uuid2, Filename(filename2), APPLICATION_XML_VALUE, "href", 123, storageLocation)

        val multipartFile1 =
            MockMultipartFile(
                "file",
                filename1,
                APPLICATION_XML_VALUE,
                "some data".byteInputStream(),
            )
        val multipartFile2 =
            MockMultipartFile(
                "file",
                filename2,
                APPLICATION_XML_VALUE,
                "some data".byteInputStream(),
            )

        every { ioService.createTempFile(any(), Filename(filename1)) } returns storedFile1
        every { ioService.createTempFile(any(), Filename(filename2)) } returns storedFile2
        every { ioService.delete(any()) } returns Unit
        every { fileStore.put(any(), any()) } returns Unit
        every { fileService.getProtoStreamFromGpxFile(any()) } returns emptyByteArrayInputStream()

        mockMvc.perform(
            multipart("/files")
                .file(multipartFile1)
                .file(multipartFile2),
        )
            .andExpect(status().isOk)

        verify { ioService.createTempFile(any(), Filename(filename1)) }
        verify { ioService.createTempFile(any(), Filename(filename2)) }
        verify { fileStore.put(uuid1, storedFile1) }
        verify { fileStore.put(uuid2, storedFile2) }
        verify(exactly = 2) { ioService.delete(any()) }
    }

    @Test
    fun `delete file`() {
        val uuid = UUID.randomUUID()
        val storageLocation = "/path/to/file"
        val storedFile = StoredFile(uuid, Filename("test.txt"), TEXT_PLAIN_VALUE, "href", 123, storageLocation)

        every { fileStore.delete(uuid) } returns storedFile
        every { ioService.delete(storageLocation) } returns Unit

        mockMvc.perform(delete("/files/$uuid")) //
            .andExpect(status().isNoContent)

        verify { fileStore.delete(uuid) }
        verify { ioService.delete(storageLocation) }
    }

    @Test
    fun `delete file fails since file is not in store`() {
        val uuid = UUID.randomUUID()
        val storageLocation = "/path/to/file"

        every { fileStore.delete(uuid) } returns null

        mockMvc.perform(delete("/files/$uuid")) //
            .andExpect(status().isNotFound)

        verify { fileStore.delete(uuid) }
        verify(exactly = 0) { ioService.delete(storageLocation) }
    }

    @Test
    fun `delete file fails since file is in store but not in filesystem`() {
        val uuid = UUID.randomUUID()
        val storageLocation = "/path/to/file"
        val storedFile = StoredFile(uuid, Filename("test.txt"), TEXT_PLAIN_VALUE, "href", 123, storageLocation)

        every { fileStore.delete(uuid) } returns storedFile
        every { ioService.delete(storageLocation) } throws NotFoundException("not found in filesystem")

        mockMvc.perform(delete("/files/$uuid")) //
            .andExpect(status().isNotFound)

        verify { fileStore.delete(uuid) }
        verify { ioService.delete(storageLocation) }
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
