package org.devshred.gpstools.domain.proto

import org.apache.commons.math3.random.RandomDataGenerator
import org.assertj.core.api.Assertions.assertThat
import org.devshred.gpstools.domain.gps.PoiType
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.springframework.test.context.junit.jupiter.SpringExtension
import java.util.stream.Stream
import io.jenetics.jpx.WayPoint as GpxWayPoint

@ExtendWith(SpringExtension::class)
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

        val gpx = protoBuf.toGpx()

        assertThat(gpx.latitude.toDouble()).isEqualTo(lat)
        assertThat(gpx.longitude.toDouble()).isEqualTo(lon)
    }

    @ParameterizedTest(name = "{0} should convert to {1}")
    @MethodSource("protoToGps")
    fun `convert PoiType to ProtoPoiType`(
        poiType: PoiType,
        protoPoiType: ProtoPoiType,
    ) {
        assertThat(toProtoBuf(poiType)).isEqualTo(protoPoiType)
    }

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
