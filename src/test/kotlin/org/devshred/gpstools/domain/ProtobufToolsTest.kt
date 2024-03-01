package org.devshred.gpstools.domain

import io.jenetics.jpx.GPX
import org.apache.commons.lang3.RandomStringUtils.randomAlphabetic
import org.apache.commons.math3.random.RandomDataGenerator
import org.assertj.core.api.Assertions.assertThat
import org.devshred.gpstools.proto3.GpsContainer
import org.devshred.gpstools.proto3.wayPoint
import org.junit.jupiter.api.Test
import io.jenetics.jpx.WayPoint as GpxWayPoint

class ProtobufToolsTest {
    private val randomGenerator = RandomDataGenerator().randomGenerator

    @Test
    fun `convert GPX to protoBuf`() {
        val lat = randomGenerator.nextDouble()
        val lon = randomGenerator.nextDouble()
        val gpx = GpxWayPoint.of(lat, lon)

        val protoBuf = toProtoBuf(gpx)

        assertThat(protoBuf.latitude).isEqualTo(lat)
        assertThat(protoBuf.longitude).isEqualTo(lon)
    }

    @Test
    fun `convert protoBuf to GPX`() {
        val lat = randomGenerator.nextDouble()
        val lon = randomGenerator.nextDouble()
        val protoBuf =
            wayPoint {
                latitude = lat
                longitude = lon
            }

        val gpx = toGpx(protoBuf)

        assertThat(gpx.latitude.toDouble()).isEqualTo(lat)
        assertThat(gpx.longitude.toDouble()).isEqualTo(lon)
    }

    @Test
    fun `set trackname from first track`() {
        val trackname = randomAlphabetic(8)
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

        val gpsContainer = GpsContainer.parseFrom(gpxToProtobufInputStream(gpx))

        assertThat(gpsContainer.name).isEqualTo(trackname)
    }

    @Test
    fun `set trackname from GPX metadata if no track was found`() {
        val trackname = randomAlphabetic(8)
        val gpx =
            GPX.builder()
                .metadata { m -> m.name(trackname) }
                .build()

        val gpsContainer = GpsContainer.parseFrom(gpxToProtobufInputStream(gpx))

        assertThat(gpsContainer.name).isEqualTo(trackname)
    }

    @Test
    fun `skip setting trackname if neither track nor metadata was found`() {
        val gpx = GPX.builder().build()

        val gpsContainer = GpsContainer.parseFrom(gpxToProtobufInputStream(gpx))

        assertThat(gpsContainer.name).isEmpty()
    }

    private fun randomWayPoint(): GpxWayPoint =
        GpxWayPoint.builder().lat(randomGenerator.nextDouble()).lon(randomGenerator.nextDouble()).build()
}
