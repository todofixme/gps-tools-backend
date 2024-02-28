package org.devshred.gpstools.domain

import org.apache.commons.math3.random.RandomDataGenerator
import org.assertj.core.api.Assertions.assertThat
import org.devshred.gpstools.proto3.trackPoint
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
            trackPoint {
                latitude = lat
                longitude = lon
            }

        val gpx = toGpx(protoBuf)

        assertThat(gpx.latitude.toDouble()).isEqualTo(lat)
        assertThat(gpx.longitude.toDouble()).isEqualTo(lon)
    }
}
