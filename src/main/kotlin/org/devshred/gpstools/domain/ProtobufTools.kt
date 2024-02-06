package org.devshred.gpstools.domain

import io.jenetics.jpx.Latitude
import io.jenetics.jpx.Length
import io.jenetics.jpx.Longitude
import io.jenetics.jpx.WayPoint
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.encoding.decodeStructure
import kotlinx.serialization.encoding.encodeStructure
import kotlinx.serialization.protobuf.ProtoBuf
import org.springframework.core.io.InputStreamResource
import java.io.InputStream
import java.time.Instant
import java.util.Optional
import kotlin.jvm.optionals.getOrNull

@Serializable(with = WayPointSerializer::class)
data class ProtoPoint(
    val latitude: Latitude,
    val longitude: Longitude,
    val elevation: Optional<Length>,
    val time: Optional<Instant>,
)

@OptIn(ExperimentalSerializationApi::class)
fun wayPointsToProtobufInputStream(wayPoints: List<WayPoint>): InputStream {
    val protoPoints = wayPoints.map(::toProtoPoint).toList()
    val byteArray = ProtoBuf.encodeToByteArray(protoPoints)
    return byteArray.inputStream()
}

@OptIn(ExperimentalSerializationApi::class)
fun protoBufInputStreamResourceToWaypoints(inputStreamResource: InputStreamResource): List<WayPoint> {
    val protoPoints = ProtoBuf.decodeFromByteArray<List<ProtoPoint>>(inputStreamResource.contentAsByteArray)
    return protoPoints.map(::toWayPoint).toList()
}

fun toProtoPoint(wayPoint: WayPoint): ProtoPoint =
    ProtoPoint(
        wayPoint.latitude,
        wayPoint.longitude,
        wayPoint.elevation,
        wayPoint.time,
    )

fun toWayPoint(protoWayPoint: ProtoPoint): WayPoint =
    WayPoint.builder()
        .lat(protoWayPoint.latitude)
        .lon(protoWayPoint.longitude)
        .ele(protoWayPoint.elevation.getOrNull())
        .time(protoWayPoint.time.getOrNull())
        .build()

class WayPointSerializer : KSerializer<ProtoPoint> {
    override val descriptor: SerialDescriptor =
        buildClassSerialDescriptor("WayPoint") {
            element("latitude", Double.serializer().descriptor)
            element("longitude", Double.serializer().descriptor)
            element("elevation", Double.serializer().descriptor)
            element("time", Long.serializer().descriptor)
        }

    override fun serialize(
        encoder: Encoder,
        value: ProtoPoint,
    ) {
        encoder.encodeStructure(descriptor) {
            encodeDoubleElement(descriptor, 0, value.latitude.toDouble())
            encodeDoubleElement(descriptor, 1, value.longitude.toDouble())
            if (value.elevation.isPresent) encodeDoubleElement(descriptor, 2, value.elevation.get().toDouble())
            if (value.time.isPresent) encodeLongElement(descriptor, 3, value.time.get().epochSecond)
        }
    }

    override fun deserialize(decoder: Decoder): ProtoPoint {
        var latitude: Latitude? = null
        var longitude: Longitude? = null
        var elevation: Optional<Length> = Optional.empty()
        var time: Optional<Instant> = Optional.empty()
        decoder.decodeStructure(descriptor) {
            while (true) {
                when (val index = decodeElementIndex(descriptor)) {
                    0 -> latitude = Latitude.ofDegrees(decodeDoubleElement(descriptor, 0))
                    1 -> longitude = Longitude.ofDegrees(decodeDoubleElement(descriptor, 1))
                    2 -> elevation = Optional.of(Length.of(decodeDoubleElement(descriptor, 2), Length.Unit.METER))
                    3 -> time = Optional.of(Instant.ofEpochSecond(decodeLongElement(descriptor, 3)))
                    CompositeDecoder.DECODE_DONE -> break
                    else -> error("Unexpected index: $index")
                }
            }
        }
        return ProtoPoint(
            latitude ?: error("Missing required field 'latitude'"),
            longitude ?: error("Missing required field 'longitude'"),
            elevation,
            time,
        )
    }
}
