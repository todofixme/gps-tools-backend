package org.devshred.gpstools.domain.proto

import io.mockk.every
import io.mockk.mockk
import org.apache.commons.lang3.RandomStringUtils
import org.assertj.core.api.Assertions.assertThat
import org.devshred.gpstools.formats.gps.GpsContainerMapper
import org.devshred.gpstools.formats.proto.ProtoService
import org.devshred.gpstools.formats.proto.protoContainer
import org.devshred.gpstools.storage.IOService
import org.junit.jupiter.api.Test
import org.springframework.core.io.InputStreamResource

class ProtoServiceTest {
    private val ioService = mockk<IOService>()
    private val mapper = GpsContainerMapper()

    private var cut = ProtoService(ioService)

    @Test
    fun `take name from proto-file`() {
        val nameStoredAtProtoFile = RandomStringUtils.randomAlphabetic(8)
        val storageLocation = RandomStringUtils.randomAlphabetic(8)

        val protoContainer =
            protoContainer {
                name = nameStoredAtProtoFile
            }

        every { ioService.getAsStream(storageLocation) } returns
            InputStreamResource(
                protoContainer.toByteArray().inputStream(),
            )

        val actual = cut.readProtoContainer(storageLocation)

        assertThat(actual.name).isEqualTo(nameStoredAtProtoFile)
    }

    @Test
    fun `overwrite name of the track as set in proto-file`() {
        val nameStoredAtProtoFile = RandomStringUtils.randomAlphabetic(8)
        val namePassedAsRequestParameter = RandomStringUtils.randomAlphabetic(8)
        val storageLocation = RandomStringUtils.randomAlphabetic(8)

        val protoContainer =
            protoContainer {
                name = nameStoredAtProtoFile
            }

        every { ioService.getAsStream(storageLocation) } returns
            InputStreamResource(
                protoContainer.toByteArray().inputStream(),
            )

        val actual =
            cut.readProtoContainer(storageLocation, namePassedAsRequestParameter)

        assertThat(actual.name).isEqualTo(namePassedAsRequestParameter)
    }
}
