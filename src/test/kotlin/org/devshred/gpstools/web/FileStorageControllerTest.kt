package org.devshred.gpstools.web

import com.ninjasquad.springmockk.MockkBean
import io.mockk.called
import io.mockk.every
import io.mockk.verify
import org.apache.commons.io.input.NullInputStream
import org.devshred.gpstools.domain.FileStore
import org.devshred.gpstools.domain.GpxService
import org.devshred.gpstools.domain.IOService
import org.devshred.gpstools.domain.NotFoundException
import org.devshred.gpstools.domain.StoredFile
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
    lateinit var gpxService: GpxService

    @Test
    fun `download file`() {
        val uuid = UUID.randomUUID()
        val storageLocation = "/path/to/file"
        val storedFile = StoredFile(uuid, "test.gpx", APPLICATION_XML_VALUE, "href", 123, storageLocation)

        every { fileStore.get(uuid) } returns storedFile
        every { ioService.getAsStream(storageLocation) } returns InputStreamResource(InputStream.nullInputStream())
        every { gpxService.protoFileToGpxInputStream(storageLocation, null) } returns ByteArrayInputStream(byteArrayOf())

        mockMvc.perform(get("/files/$uuid?m=dl")) //
            .andExpectAll(
                status().isOk,
                content().contentType(APPLICATION_XML_VALUE),
                header().string(HttpHeaders.CONTENT_DISPOSITION, ("attachment; filename=\"test.gpx\"")),
            )

        verify { fileStore.get(uuid) }
        verify { gpxService.protoFileToGpxInputStream(storageLocation, null) }
    }

    @Test
    fun `download file with trackname`() {
        val uuid = UUID.randomUUID()
        val storageLocation = "/path/to/file"
        val storedFile = StoredFile(uuid, "test.gpx", APPLICATION_XML_VALUE, "href", 123, storageLocation)
        val trackname = "My Track"

        every { fileStore.get(uuid) } returns storedFile
        every { ioService.getAsStream(storageLocation) } returns InputStreamResource(InputStream.nullInputStream())
        every { gpxService.protoFileToGpxInputStream(storageLocation, trackname) } returns ByteArrayInputStream(byteArrayOf())

        mockMvc.perform(get("/files/$uuid?m=dl&name=$trackname")) //
            .andExpectAll(
                status().isOk,
                content().contentType(APPLICATION_XML_VALUE),
                header().string(HttpHeaders.CONTENT_DISPOSITION, ("attachment; filename=\"test.gpx\"")),
            )

        verify { fileStore.get(uuid) }
        verify { gpxService.protoFileToGpxInputStream(storageLocation, trackname) }
    }

    @Test
    fun `trying to download file that does not exist in store`() {
        val uuid = UUID.randomUUID()

        every { fileStore.get(uuid) } throws NotFoundException("not found")

        mockMvc.perform(get("/files/$uuid")) //
            .andExpect(status().isNotFound)

        verify { fileStore.get(uuid) }
        verify { ioService.getAsStream(any()) wasNot called }
    }

    @Test
    fun `trying to download file that does not exist in filesystem`() {
        val uuid = UUID.randomUUID()
        val storageLocation = "/path/to/file"
        val storedFile = StoredFile(uuid, "test.gpx", TEXT_PLAIN_VALUE, "href", 123, storageLocation)

        every { fileStore.get(uuid) } returns storedFile
        every { gpxService.protoFileToGpxInputStream(storageLocation, null) } throws NotFoundException("not found")

        mockMvc.perform(get("/files/$uuid")) //
            .andExpect(status().isNotFound)

        verify { fileStore.get(uuid) }
        verify { gpxService.protoFileToGpxInputStream(storageLocation, null) }
    }

    @Test
    fun `trying to download file with invalid UUID`() {
        val uuid = "not a UUID"

        mockMvc.perform(get("/files/$uuid")) //
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `get file`() {
        val uuid = UUID.randomUUID()
        val storageLocation = "/path/to/file"
        val storedFile = StoredFile(uuid, "test.txt", TEXT_PLAIN_VALUE, "href", 123, storageLocation)

        every { fileStore.get(uuid) } returns storedFile
        every { gpxService.protoFileToGpxInputStream(storageLocation, null) } returns ByteArrayInputStream(byteArrayOf())

        mockMvc.perform(get("/files/$uuid")) //
            .andExpectAll(
                status().isOk,
                content().contentType(TEXT_PLAIN_VALUE),
                header().doesNotExist(HttpHeaders.CONTENT_DISPOSITION),
            )

        verify { fileStore.get(uuid) }
        verify { gpxService.protoFileToGpxInputStream(storageLocation, null) }
    }

    @Test
    fun `upload single file`() {
        val filename = "test.gpx"
        val uuid = UUID.randomUUID()
        val storageLocation = "/path/to/file"
        val storedFile = StoredFile(uuid, filename, APPLICATION_XML_VALUE, "href", 123, storageLocation)

        every { ioService.createTempFile(any(), filename) } returns storedFile
        every { gpxService.protoInputStreamFromFileLocation(storageLocation) } returns NullInputStream()
        every { fileStore.put(uuid, storedFile) } returns Unit
        every { ioService.delete(storageLocation) } returns Unit

        mockMvc.perform(
            post("/file")
                .param("filename", filename)
                .content("123"),
        )
            .andExpect(status().isOk)

        verify(exactly = 2) { ioService.createTempFile(any(), filename) }
        verify { fileStore.put(uuid, storedFile) }
        verify { ioService.delete(storageLocation) }
    }

    @Test
    fun `uploading single file fails due wrong file-suffix`() {
        val filename = "test.txt"

        every { ioService.createTempFile(any(), filename) } throws IOException()

        mockMvc.perform(
            post("/file")
                .param("filename", filename)
                .content("123"),
        )
            .andExpect(status().isBadRequest)

        verify(exactly = 0) { ioService.createTempFile(any(), filename) }
        verify(exactly = 0) { fileStore.put(any(), any()) }
    }

    @Test
    fun `uploading single file fails due to IO issues`() {
        val filename = "test.gpx"

        every { ioService.createTempFile(any(), filename) } throws IOException()

        mockMvc.perform(
            post("/file")
                .param("filename", filename)
                .content("123"),
        )
            .andExpect(status().isInternalServerError)

        verify { ioService.createTempFile(any(), filename) }
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
                filename,
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

        every { ioService.createTempFile(any(), filename) } returns storedFile
        every { ioService.delete(any()) } returns Unit
        every { fileStore.put(uuid, storedFile) } returns Unit
        every { gpxService.protoInputStreamFromFileLocation(storageLocation) } returns emptyByteArrayInputStream()

        mockMvc.perform(
            multipart("/files")
                .file(multipartFile),
        )
            .andExpect(status().isOk)

        verify { ioService.createTempFile(any(), filename) }
        verify { fileStore.put(uuid, storedFile) }
        verify { gpxService.protoInputStreamFromFileLocation(storageLocation) }
    }

    @Test
    fun `upload multiple multipartFiles`() {
        val storageLocation = "/path/to/file"

        val filename1 = "test1.gpx"
        val uuid1 = UUID.randomUUID()
        val storedFile1 = StoredFile(uuid1, filename1, APPLICATION_XML_VALUE, "href", 123, storageLocation)

        val filename2 = "test2.gpx"
        val uuid2 = UUID.randomUUID()
        val storedFile2 = StoredFile(uuid2, filename2, APPLICATION_XML_VALUE, "href", 123, storageLocation)

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

        every { ioService.createTempFile(any(), filename1) } returns storedFile1
        every { ioService.createTempFile(any(), filename2) } returns storedFile2
        every { ioService.delete(any()) } returns Unit
        every { fileStore.put(any(), any()) } returns Unit
        every { gpxService.protoInputStreamFromFileLocation(any()) } returns ByteArrayInputStream(byteArrayOf())

        mockMvc.perform(
            multipart("/files")
                .file(multipartFile1)
                .file(multipartFile2),
        )
            .andExpect(status().isOk)

        verify { ioService.createTempFile(any(), filename1) }
        verify { ioService.createTempFile(any(), filename2) }
        verify { fileStore.put(uuid1, storedFile1) }
        verify { fileStore.put(uuid2, storedFile2) }
        verify(exactly = 2) { ioService.delete(any()) }
        verify(exactly = 2) { gpxService.protoInputStreamFromFileLocation(any()) }
    }

    private fun emptyByteArrayInputStream() = ByteArrayInputStream(byteArrayOf())

    @Test
    fun `delete file`() {
        val uuid = UUID.randomUUID()
        val storageLocation = "/path/to/file"
        val storedFile = StoredFile(uuid, "test.txt", TEXT_PLAIN_VALUE, "href", 123, storageLocation)

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
        val storedFile = StoredFile(uuid, "test.txt", TEXT_PLAIN_VALUE, "href", 123, storageLocation)

        every { fileStore.delete(uuid) } returns storedFile
        every { ioService.delete(storageLocation) } throws NotFoundException("not found in filesystem")

        mockMvc.perform(delete("/files/$uuid")) //
            .andExpect(status().isNotFound)

        verify { fileStore.delete(uuid) }
        verify { ioService.delete(storageLocation) }
    }
}
