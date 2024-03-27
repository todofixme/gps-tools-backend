package org.devshred.gpstools.domain.proto

import com.google.protobuf.Timestamp
import io.jenetics.jpx.GPX
import io.jenetics.jpx.TrackSegment
import org.devshred.gpstools.domain.gps.PoiType
import org.devshred.gpstools.domain.gps.WayPoint
import org.devshred.gpstools.domain.gpx.GPX_CREATOR
import org.w3c.dom.Node
import java.time.Instant
import javax.xml.parsers.DocumentBuilderFactory
import io.jenetics.jpx.WayPoint as GpxWayPoint

fun ProtoGpsContainer.toGpx(): GPX {
    val gpxBuilder = GPX.builder().creator(GPX_CREATOR)

    if (hasName()) {
        gpxBuilder.metadata { it.name(name) }
    }

    if (wayPointsCount > 0) {
        wayPointsList.forEach { gpxBuilder.addWayPoint(it.toGpx()) }
    }

    val segmentBuilder = TrackSegment.builder()
    track.wayPointsList.forEach { segmentBuilder.addPoint(it.toGpx()) }
    gpxBuilder.addTrack {
        if (hasName()) {
            it.name(name)
        }
        it.addSegment(segmentBuilder.build())
    }

    return gpxBuilder.build()
}

fun toProtoBuf(gpx: GpxWayPoint): ProtoWayPoint =
    protoWayPoint {
        latitude = gpx.latitude.toDouble()
        longitude = gpx.longitude.toDouble()
        if (gpx.elevation.isPresent) elevation = gpx.elevation.get().toDouble()
        if (gpx.time.isPresent) time = Timestamp.newBuilder().setSeconds(gpx.time.get().epochSecond).build()
        if (gpx.name.isPresent) name = gpx.name.get()
        if (gpx.symbol.isPresent) type = toProtoBuf(PoiType.fromGpxSym(gpx.symbol.get())!!)

        gpx.extensions.map { document ->
            val node: Node = document.documentElement
            val extensionValues = exploreDocument(node)
            extensionValues.power?.let { power = it }
            extensionValues.cadence?.let { cadence = it }
            extensionValues.temperature?.let { temperature = it }
            extensionValues.heartRate?.let { heartrate = it }
        }
    }

fun exploreDocument(node: Node): ExtensionValues {
    val extensionValues = ExtensionValues()
    var childNode = node.firstChild
    while (childNode != null) {
        when (childNode.nodeName) {
            "power" -> extensionValues.power = childNode.textContent.toInt()
            "gpxtpx:cad" -> extensionValues.cadence = childNode.textContent.toInt()
            "gpxtpx:atemp" -> extensionValues.temperature = childNode.textContent.toInt()
            "gpxtpx:hr" -> extensionValues.heartRate = childNode.textContent.toInt()
        }
        extensionValues.union(exploreDocument(childNode))
        childNode = childNode.nextSibling
    }
    return extensionValues
}

data class ExtensionValues(
    var power: Int? = null,
    var cadence: Int? = null,
    var temperature: Int? = null,
    var heartRate: Int? = null,
) {
    fun union(newValues: ExtensionValues): ExtensionValues {
        return this.copy(
            power = newValues.power ?: this.power,
            cadence = newValues.cadence ?: this.cadence,
            temperature = newValues.temperature ?: this.temperature,
            heartRate = newValues.heartRate ?: this.heartRate,
        )
    }
}

fun toProtoBuf(poiType: PoiType): ProtoPoiType =
    when (poiType) {
        PoiType.GENERIC -> ProtoPoiType.GENERIC
        PoiType.SUMMIT -> ProtoPoiType.SUMMIT
        PoiType.VALLEY -> ProtoPoiType.VALLEY
        PoiType.WATER -> ProtoPoiType.WATER
        PoiType.FOOD -> ProtoPoiType.FOOD
        PoiType.DANGER -> ProtoPoiType.DANGER
        PoiType.LEFT -> ProtoPoiType.LEFT
        PoiType.RIGHT -> ProtoPoiType.RIGHT
        PoiType.STRAIGHT -> ProtoPoiType.STRAIGHT
        PoiType.FIRST_AID -> ProtoPoiType.FIRST_AID
        PoiType.FOURTH_CATEGORY -> ProtoPoiType.FOURTH_CATEGORY
        PoiType.THIRD_CATEGORY -> ProtoPoiType.THIRD_CATEGORY
        PoiType.SECOND_CATEGORY -> ProtoPoiType.SECOND_CATEGORY
        PoiType.FIRST_CATEGORY -> ProtoPoiType.FIRST_CATEGORY
        PoiType.HORS_CATEGORY -> ProtoPoiType.HORS_CATEGORY
        PoiType.RESIDENCE -> ProtoPoiType.RESIDENCE
        PoiType.SPRINT -> ProtoPoiType.SPRINT
    }

fun toGps(protoPoiType: ProtoPoiType): PoiType = PoiType.fromString(protoPoiType.name)

fun toGps(proto: ProtoWayPoint): WayPoint =
    WayPoint(
        latitude = proto.latitude,
        longitude = proto.longitude,
        elevation = if (proto.hasElevation()) proto.elevation else null,
        name = if (proto.hasName()) proto.name else null,
        time = if (proto.hasTime()) Instant.ofEpochSecond(proto.time.seconds) else null,
        type = if (proto.hasType()) toGps(proto.type) else null,
        speed = if (proto.hasSpeed()) proto.speed else null,
        power = if (proto.hasPower()) proto.power else null,
        temperature = if (proto.hasTemperature()) proto.temperature else null,
        heartrate = if (proto.hasHeartrate()) proto.heartrate else null,
        cadence = if (proto.hasCadence()) proto.cadence else null,
    )

fun ProtoWayPoint.toGpx(): GpxWayPoint {
    val builder =
        GpxWayPoint.builder()
            .lat(latitude)
            .lon(longitude)

    if (hasElevation()) {
        builder.ele(elevation)
    }

    if (hasTime()) {
        builder.time(Instant.ofEpochSecond(time.seconds))
    }

    if (hasName()) {
        builder.name(name)
    }

    if (hasType()) {
        builder.sym(PoiType.fromString(type.name).gpxSym)
    }

    if (hasSpeed()) {
        builder.speed(speed)
    }

    if (hasPower() || hasCadence() || hasTemperature() || hasHeartrate()) {
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument()
        val extensions = document.createElement("extensions")

        if (hasPower()) {
            val powerElement = document.createElement("power")
            powerElement.textContent = power.toString()
            extensions.appendChild(powerElement)
        }

        if (hasTemperature() || hasHeartrate() || hasCadence()) {
            val trackPointExtensionElement = document.createElement("gpxtpx:TrackPointExtension")
            val xmlnsAttribute = document.createAttribute("xmlns:gpxtpx")

            // TODO: try to set xmlns once at root-level
            xmlnsAttribute.value = "http://www.garmin.com/xmlschemas/TrackPointExtension/v1"
            trackPointExtensionElement.setAttributeNodeNS(xmlnsAttribute)

            if (hasTemperature()) {
                val temperatureElement = document.createElement("gpxtpx:atemp")
                temperatureElement.textContent = temperature.toString()
                trackPointExtensionElement.appendChild(temperatureElement)
            }

            if (hasHeartrate()) {
                val heartrateElement = document.createElement("gpxtpx:hr")
                heartrateElement.textContent = heartrate.toString()
                trackPointExtensionElement.appendChild(heartrateElement)
            }

            if (hasCadence()) {
                val cadenceElement = document.createElement("gpxtpx:cad")
                cadenceElement.textContent = cadence.toString()
                trackPointExtensionElement.appendChild(cadenceElement)
            }

            extensions.appendChild(trackPointExtensionElement)
        }

        document.appendChild(extensions)
        builder.extensions(document)
    }

    return builder.build()
}
