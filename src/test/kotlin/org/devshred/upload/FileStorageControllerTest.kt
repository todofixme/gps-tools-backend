package org.devshred.upload

import com.ninjasquad.springmockk.MockkBean
import io.mockk.called
import io.mockk.every
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.core.io.InputStreamResource
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.MediaType.TEXT_PLAIN_VALUE
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.io.IOException
import java.io.InputStream
import java.util.*

@WebMvcTest
class FileStorageControllerTest(@Autowired val mockMvc: MockMvc) {
    @MockkBean
    lateinit var fileStore: FileStore

    @MockkBean
    lateinit var ioService: IOService

    @Test
    fun `download file`() {
        val uuid = UUID.randomUUID()
        val storageLocation = "/path/to/file"
        val storedFile = StoredFile(uuid, "test.txt", TEXT_PLAIN_VALUE, "href", 123, storageLocation)

        every { fileStore.get(uuid) } returns storedFile
        every { ioService.getAsStream(storageLocation) } returns InputStreamResource(InputStream.nullInputStream())

        mockMvc.perform(get("/files/$uuid")) //
            .andExpectAll(
                status().isOk,
                content().contentType(TEXT_PLAIN_VALUE),
                header().string(HttpHeaders.CONTENT_DISPOSITION, ("attachment; filename=\"test.txt\""))
            )

        verify { fileStore.get(uuid) }
        verify { ioService.getAsStream(storageLocation) }
    }

    @Test
    fun `trying to download file that does not exist in store`() {
        val uuid = UUID.randomUUID()

        every { fileStore.get(uuid) } returns null

        mockMvc.perform(get("/files/${uuid}")) //
            .andExpect(status().isNotFound)

        verify { fileStore.get(uuid) }
        verify { ioService.getAsStream(any()) wasNot called }
    }

    @Test
    fun `trying to download file that does not exist in filesystem`() {
        val uuid = UUID.randomUUID()
        val storageLocation = "/path/to/file"
        val storedFile = StoredFile(uuid, "test.txt", TEXT_PLAIN_VALUE, "href", 123, storageLocation)

        every { fileStore.get(uuid) } returns storedFile
        every { ioService.getAsStream(storageLocation) } throws NotFoundException("not found")

        mockMvc.perform(get("/files/${uuid}")) //
            .andExpect(status().isNotFound)

        verify { fileStore.get(uuid) }
        verify { ioService.getAsStream(storageLocation) }
    }

    @Test
    fun `trying to download file with invalid UUID`() {
        val uuid = "not a UUID"

        mockMvc.perform(get("/files/${uuid}")) //
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `show file`() {
        val uuid = UUID.randomUUID()
        val storageLocation = "/path/to/file"
        val storedFile = StoredFile(uuid, "test.txt", TEXT_PLAIN_VALUE, "href", 123, storageLocation)

        every { fileStore.get(uuid) } returns storedFile
        every { ioService.getAsStream(storageLocation) } returns InputStreamResource(InputStream.nullInputStream())

        mockMvc.perform(get("/show/${uuid}")) //
            .andExpectAll(
                status().isOk,
                content().contentType(TEXT_PLAIN_VALUE),
                header().doesNotExist(HttpHeaders.CONTENT_DISPOSITION)
            )

        verify { fileStore.get(uuid) }
        verify { ioService.getAsStream(storageLocation) }
    }

    @Test
    fun `upload file`() {
        val uuid = UUID.randomUUID()
        val storageLocation = "/path/to/file"
        val storedFile = StoredFile(uuid, "test.txt", TEXT_PLAIN_VALUE, "href", 123, storageLocation)

        every { fileStore.get(uuid) } returns storedFile
        every { ioService.getAsStream(storageLocation) } returns InputStreamResource(InputStream.nullInputStream())

        mockMvc.perform(get("/show/${uuid}")) //
            .andExpectAll(
                status().isOk,
                content().contentType(TEXT_PLAIN_VALUE),
                header().doesNotExist(HttpHeaders.CONTENT_DISPOSITION)
            )

        verify { fileStore.get(uuid) }
        verify { ioService.getAsStream(storageLocation) }
    }

    @Test
    fun `upload single file`() {
        val filename = "test.txt"
        val uuid = UUID.randomUUID()
        val storageLocation = "/path/to/file"
        val storedFile = StoredFile(uuid, filename, TEXT_PLAIN_VALUE, "href", 123, storageLocation)

        every { ioService.createTempFile(any(), filename) } returns storedFile
        every { fileStore.put(uuid, storedFile) } returns Unit

        mockMvc.perform(
            post("/file")
                .param("filename", filename)
                .content("123")
        )
            .andExpect(status().isOk)

        verify { ioService.createTempFile(any(), filename) }
        verify { fileStore.put(uuid, storedFile) }
    }

    @Test
    fun `uploading single file fails due to IO issues`() {
        val filename = "test.txt"

        every { ioService.createTempFile(any(), filename) } throws IOException()

        mockMvc.perform(
            post("/file")
                .param("filename", filename)
                .content("123")
        )
            .andExpect(status().isInternalServerError)

        verify { ioService.createTempFile(any(), filename) }
        verify(exactly = 0) { fileStore.put(any(), any()) }
    }

    @Test
    fun `upload multipartFile`() {
        val filename = "test.txt"
        val uuid = UUID.randomUUID()
        val storageLocation = "/path/to/file"
        val storedFile = StoredFile(uuid, filename, TEXT_PLAIN_VALUE, "href", 123, storageLocation)

        val multipartFile = MockMultipartFile(
            "file", filename, "text/plain", "some data".byteInputStream()
        )

        every { ioService.createTempFile(any(), filename) } returns storedFile
        every { fileStore.put(uuid, storedFile) } returns Unit

        mockMvc.perform(
            multipart("/files")
                .file(multipartFile)
        )
            .andExpect(status().isOk)

        verify { ioService.createTempFile(any(), filename) }
        verify { fileStore.put(uuid, storedFile) }
    }

    @Test
    fun `upload multiple multipartFiles`() {
        val storageLocation = "/path/to/file"

        val filename1 = "test1.txt"
        val uuid1 = UUID.randomUUID()
        val storedFile1 = StoredFile(uuid1, filename1, TEXT_PLAIN_VALUE, "href", 123, storageLocation)

        val filename2 = "test2.txt"
        val uuid2 = UUID.randomUUID()
        val storedFile2 = StoredFile(uuid2, filename2, TEXT_PLAIN_VALUE, "href", 123, storageLocation)

        val multipartFile1 = MockMultipartFile(
            "file", filename1, "text/plain", "some data".byteInputStream()
        )
        val multipartFile2 = MockMultipartFile(
            "file", filename2, "text/plain", "some data".byteInputStream()
        )

        every { ioService.createTempFile(any(), filename1) } returns storedFile1
        every { ioService.createTempFile(any(), filename2) } returns storedFile2
        every { fileStore.put(any(), any()) } returns Unit

        mockMvc.perform(
            multipart("/files")
                .file(multipartFile1)
                .file(multipartFile2)
        )
            .andExpect(status().isOk)

        verify { ioService.createTempFile(any(), filename1) }
        verify { ioService.createTempFile(any(), filename2) }
        verify { fileStore.put(uuid1, storedFile1) }
        verify { fileStore.put(uuid2, storedFile2) }
    }

    @Test
    fun `delete file`() {
        val uuid = UUID.randomUUID()
        val storageLocation = "/path/to/file"
        val storedFile = StoredFile(uuid, "test.txt", TEXT_PLAIN_VALUE, "href", 123, storageLocation)

        every { fileStore.delete(uuid) } returns storedFile
        every { ioService.delete(storageLocation) } returns Unit

        mockMvc.perform(delete("/files/${uuid}")) //
            .andExpect(
                status().isNoContent
            )

        verify { fileStore.delete(uuid) }
        verify { ioService.delete(storageLocation) }
    }

    @Test
    fun `delete file fails since file is not in store`() {
        val uuid = UUID.randomUUID()
        val storageLocation = "/path/to/file"

        every { fileStore.delete(uuid) } returns null

        mockMvc.perform(delete("/files/${uuid}")) //
            .andExpect(
                status().isNotFound
            )

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

        mockMvc.perform(delete("/files/${uuid}")) //
            .andExpect(
                status().isNotFound
            )

        verify { fileStore.delete(uuid) }
        verify { ioService.delete(storageLocation) }
    }
}
