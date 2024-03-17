package org.devshred.gpstools.domain.proto

import io.jenetics.jpx.GPX
import io.jenetics.jpx.WayPoint
import io.mockk.every
import io.mockk.mockk
import org.apache.commons.lang3.RandomStringUtils
import org.apache.commons.math3.random.RandomDataGenerator
import org.assertj.core.api.Assertions.assertThat
import org.devshred.gpstools.domain.IOService
import org.junit.jupiter.api.Test
import org.springframework.core.io.InputStreamResource

class ProtoServiceTest {
    private val randomGenerator = RandomDataGenerator().randomGenerator

    private val ioService = mockk<IOService>()

    private var cut = ProtoService(ioService)

    @Test
    fun `set trackname from first track`() {
        val trackname = RandomStringUtils.randomAlphabetic(8)
        val gpx =
            GPX.builder()
                .metadata { m -> m.name("yet another name") }
                .addTrack { track ->
                    run {
                        track.name(trackname)
                        track.addSegment { s -> s.addPoint(randomWayPoint()).build() }
                    }
                }
                .build()

        val gpsContainer = ProtoGpsContainer.parseFrom(cut.gpxToProtobufInputStream(gpx))

        assertThat(gpsContainer.name).isEqualTo(trackname)
    }

    @Test
    fun `set trackname from GPX metadata if no track was found`() {
        val trackname = RandomStringUtils.randomAlphabetic(8)
        val gpx =
            GPX.builder()
                .metadata { m -> m.name(trackname) }
                .build()

        val gpsContainer = ProtoGpsContainer.parseFrom(cut.gpxToProtobufInputStream(gpx))

        assertThat(gpsContainer.name).isEqualTo(trackname)
    }

    @Test
    fun `skip setting trackname if neither track nor metadata was found`() {
        val gpx = GPX.builder().build()

        val gpsContainer = ProtoGpsContainer.parseFrom(cut.gpxToProtobufInputStream(gpx))

        assertThat(gpsContainer.name).isEmpty()
    }

    @Test
    fun `overwrite name of the track`() {
        val nameStoredAtProtoFile = RandomStringUtils.randomAlphabetic(8)
        val namePassedAsRequestParameter = RandomStringUtils.randomAlphabetic(8)
        val storageLocation = RandomStringUtils.randomAlphabetic(8)
        val gpxFromProtoFile =
            GPX.builder()
                .metadata { m -> m.name(nameStoredAtProtoFile) }
                .build()
        val streamFromProtoFile = cut.gpxToProtobufInputStream(gpxFromProtoFile)

        every { ioService.getAsStream(storageLocation) } returns InputStreamResource(streamFromProtoFile)

        val actualGpx =
            cut
                .readProtoGpsContainer(storageLocation, namePassedAsRequestParameter)
                .toGpx()

        assertThat(actualGpx.metadata.get().name.get()).isEqualTo(namePassedAsRequestParameter)
        assertThat(actualGpx.tracks[0].name.get()).isEqualTo(namePassedAsRequestParameter)
    }

    @Test
    fun `don't overwrite name of the track if no trackname was passed`() {
        val nameStoredAtProtoFile = RandomStringUtils.randomAlphabetic(8)
        val namePassedAsRequestParameter = null
        val storageLocation = RandomStringUtils.randomAlphabetic(8)
        val gpxFromProtoFile =
            GPX.builder()
                .metadata { m -> m.name(nameStoredAtProtoFile) }
                .build()
        val streamFromProtoFile = cut.gpxToProtobufInputStream(gpxFromProtoFile)

        every { ioService.getAsStream(storageLocation) } returns InputStreamResource(streamFromProtoFile)

        val actualGpx =
            cut
                .readProtoGpsContainer(storageLocation, namePassedAsRequestParameter)
                .toGpx()

        assertThat(actualGpx.metadata.get().name.get()).isEqualTo(nameStoredAtProtoFile)
        assertThat(actualGpx.tracks[0].name.get()).isEqualTo(nameStoredAtProtoFile)
    }

    private fun randomWayPoint(): WayPoint = WayPoint.builder().lat(randomGenerator.nextDouble()).lon(randomGenerator.nextDouble()).build()
}
