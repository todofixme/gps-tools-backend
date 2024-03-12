package org.devshred.gpstools.domain

import io.jenetics.jpx.GPX
import org.apache.commons.lang3.RandomStringUtils.randomAlphabetic
import org.apache.commons.math3.random.RandomDataGenerator
import org.assertj.core.api.Assertions.assertThat
import org.devshred.gpstools.domain.gps.PoiType
import org.devshred.gpstools.domain.proto.ProtoGpsContainer
import org.devshred.gpstools.domain.proto.ProtoPoiType
import org.devshred.gpstools.domain.proto.gpxToProtobufInputStream
import org.devshred.gpstools.domain.proto.protoInputStreamResourceToGpx
import org.devshred.gpstools.domain.proto.protoWayPoint
import org.devshred.gpstools.domain.proto.toGpx
import org.devshred.gpstools.domain.proto.toProtoBuf
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.springframework.core.io.InputStreamResource
import java.util.stream.Stream
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
            protoWayPoint {
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

        val gpsContainer = ProtoGpsContainer.parseFrom(gpxToProtobufInputStream(gpx))

        assertThat(gpsContainer.name).isEqualTo(trackname)
    }

    @Test
    fun `set trackname from GPX metadata if no track was found`() {
        val trackname = randomAlphabetic(8)
        val gpx =
            GPX.builder()
                .metadata { m -> m.name(trackname) }
                .build()

        val gpsContainer = ProtoGpsContainer.parseFrom(gpxToProtobufInputStream(gpx))

        assertThat(gpsContainer.name).isEqualTo(trackname)
    }

    @Test
    fun `skip setting trackname if neither track nor metadata was found`() {
        val gpx = GPX.builder().build()

        val gpsContainer = ProtoGpsContainer.parseFrom(gpxToProtobufInputStream(gpx))

        assertThat(gpsContainer.name).isEmpty()
    }

    @Test
    fun `overwrite name of the track`() {
        val nameStoredAtProtoFile = randomAlphabetic(8)
        val namePassedAsRequestParameter = randomAlphabetic(8)
        val gpxFromProtoFile =
            GPX.builder()
                .metadata { m -> m.name(nameStoredAtProtoFile) }
                .build()
        val streamFromProtoFile = gpxToProtobufInputStream(gpxFromProtoFile)

        val actualGpx =
            protoInputStreamResourceToGpx(InputStreamResource(streamFromProtoFile), namePassedAsRequestParameter)

        assertThat(actualGpx.metadata.get().name.get()).isEqualTo(namePassedAsRequestParameter)
        assertThat(actualGpx.tracks[0].name.get()).isEqualTo(namePassedAsRequestParameter)
    }

    @Test
    fun `don't overwrite name of the track if no trackname was passed`() {
        val nameStoredAtProtoFile = randomAlphabetic(8)
        val namePassedAsRequestParameter = null
        val gpxFromProtoFile =
            GPX.builder()
                .metadata { m -> m.name(nameStoredAtProtoFile) }
                .build()
        val streamFromProtoFile = gpxToProtobufInputStream(gpxFromProtoFile)

        val actualGpx =
            protoInputStreamResourceToGpx(InputStreamResource(streamFromProtoFile), namePassedAsRequestParameter)

        assertThat(actualGpx.metadata.get().name.get()).isEqualTo(nameStoredAtProtoFile)
        assertThat(actualGpx.tracks[0].name.get()).isEqualTo(nameStoredAtProtoFile)
    }

    @ParameterizedTest(name = "{0} should convert to {1}")
    @MethodSource("protoToGps")
    fun `convert PoiType to ProtoPoiType`(
        poiType: PoiType,
        protoPoiType: ProtoPoiType,
    ) {
        assertThat(toProtoBuf(poiType)).isEqualTo(protoPoiType)
    }

    private fun randomWayPoint(): GpxWayPoint =
        GpxWayPoint.builder().lat(randomGenerator.nextDouble()).lon(randomGenerator.nextDouble()).build()

    companion object {
        @JvmStatic
        private fun protoToGps(): Stream<Arguments> {
            return Stream.of(
                Arguments.of(PoiType.GENERIC, ProtoPoiType.GENERIC),
                Arguments.of(PoiType.SUMMIT, ProtoPoiType.SUMMIT),
                Arguments.of(PoiType.VALLEY, ProtoPoiType.VALLEY),
                Arguments.of(PoiType.WATER, ProtoPoiType.WATER),
                Arguments.of(PoiType.FOOD, ProtoPoiType.FOOD),
                Arguments.of(PoiType.DANGER, ProtoPoiType.DANGER),
                Arguments.of(PoiType.LEFT, ProtoPoiType.LEFT),
                Arguments.of(PoiType.RIGHT, ProtoPoiType.RIGHT),
                Arguments.of(PoiType.STRAIGHT, ProtoPoiType.STRAIGHT),
                Arguments.of(PoiType.FIRST_AID, ProtoPoiType.FIRST_AID),
                Arguments.of(PoiType.FOURTH_CATEGORY, ProtoPoiType.FOURTH_CATEGORY),
                Arguments.of(PoiType.THIRD_CATEGORY, ProtoPoiType.THIRD_CATEGORY),
                Arguments.of(PoiType.SECOND_CATEGORY, ProtoPoiType.SECOND_CATEGORY),
                Arguments.of(PoiType.FIRST_AID, ProtoPoiType.FIRST_AID),
                Arguments.of(PoiType.HORS_CATEGORY, ProtoPoiType.HORS_CATEGORY),
                Arguments.of(PoiType.RESIDENCE, ProtoPoiType.RESIDENCE),
                Arguments.of(PoiType.SPRINT, ProtoPoiType.SPRINT),
            )
        }
    }
}
