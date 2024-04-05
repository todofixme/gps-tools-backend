package org.devshred.gpstools.formats.gps

import com.garmin.fit.FitMessages
import com.garmin.fit.RecordMesg
import com.google.protobuf.Timestamp
import io.jenetics.jpx.GPX
import io.jenetics.jpx.geom.Geoid
import mil.nga.sf.geojson.Feature
import mil.nga.sf.geojson.FeatureCollection
import mil.nga.sf.geojson.LineString
import mil.nga.sf.geojson.Point
import mil.nga.sf.geojson.Position
import org.devshred.gpstools.common.orElse
import org.devshred.gpstools.formats.gps.GpsContainerMapper.Constants.SEMICIRCLES_TO_DEGREES
import org.devshred.gpstools.formats.gpx.GPX_CREATOR
import org.devshred.gpstools.formats.proto.ProtoContainer
import org.devshred.gpstools.formats.proto.ProtoPoiType
import org.devshred.gpstools.formats.proto.ProtoWayPoint
import org.devshred.gpstools.formats.proto.protoContainer
import org.devshred.gpstools.formats.proto.protoTrack
import org.devshred.gpstools.formats.proto.protoWayPoint
import org.devshred.gpstools.formats.tcx.Course
import org.devshred.gpstools.formats.tcx.CoursePoint
import org.devshred.gpstools.formats.tcx.Lap
import org.devshred.gpstools.formats.tcx.Trackpoint
import org.devshred.gpstools.formats.tcx.TrainingCenterDatabase
import org.springframework.stereotype.Component
import org.w3c.dom.Node
import java.time.Instant
import java.time.ZonedDateTime
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.jvm.optionals.getOrNull
import kotlin.math.pow
import io.jenetics.jpx.WayPoint as GpxWayPoint
import org.devshred.gpstools.formats.gps.WayPoint as GpsWayPoint

@Component
class GpsContainerMapper {
    object Constants {
        val SEMICIRCLES_TO_DEGREES = 180 / 2.0.pow(31.0)
    }

    fun toProto(gpsContainer: GpsContainer): ProtoContainer =
        protoContainer {
            gpsContainer.name?.let { name = it }
            gpsContainer.wayPoints.map { it.toProto() }.forEach { wayPoints += it }
            gpsContainer.track?.let { gpsTrack ->
                track =
                    protoTrack {
                        gpsTrack.wayPoints.forEach { wayPoints += it.toProto() }
                    }
            }
        }

    fun fromProto(protoContainer: ProtoContainer): GpsContainer =
        GpsContainer(
            name = protoContainer.name,
            wayPoints = protoContainer.wayPointsList.map { it.toGps() },
            track =
                protoContainer.track
                    ?.let { Track(protoContainer.track.wayPointsList.map { it.toGps() }) }
                    .orElse { null },
        )

    fun toGpx(gpsCont: GpsContainer): GPX {
        val gpxBuilder = GPX.builder().creator(GPX_CREATOR)
        gpsCont.name?.let { gpsName -> gpxBuilder.metadata { gpx -> gpx.name(gpsName) } }
        gpxBuilder.wayPoints(gpsCont.wayPoints.map { it.toGpx() })
        gpxBuilder.addTrack { track ->
            track.name(gpsCont.name)
            track.addSegment { segment ->
                segment.points(gpsCont.track?.wayPoints?.map { it.toGpx() })
            }
        }
        return gpxBuilder.build()
    }

    fun fromGpx(gpx: GPX): GpsContainer {
        val trackName: String? =
            if (gpx.tracks.isNotEmpty() && gpx.tracks[0].name.isPresent) {
                gpx.tracks[0].name.get()
            } else if (gpx.metadata.isPresent && gpx.metadata.get().name.isPresent) {
                gpx.metadata.get().name.get()
            } else {
                null
            }
        val wayPoints = gpx.wayPoints.map { it.toGps() }
        val track: Track? =
            if (gpx.tracks.isNotEmpty() && gpx.tracks[0].segments.isNotEmpty()) {
                Track(gpx.tracks[0].segments.flatMap { it.points }.map { it.toGps() })
            } else {
                null
            }

        return GpsContainer(
            name = trackName,
            wayPoints = wayPoints,
            track = track,
        )
    }

    fun fromFit(fitMessages: FitMessages?): GpsContainer {
        val track: Track? =
            fitMessages?.let { fit ->
                Track(
                    fit.recordMesgs
                        .filter { it.positionLat != null && it.positionLong != null }
                        .map { records -> records.toGps() },
                )
                    .orElse { null }
            }

        return GpsContainer(
            name = "Activity",
            wayPoints = emptyList(),
            track = track,
        )
    }

    /**
     * Converts a [GpsContainer] to a GeoJson [FeatureCollection].
     * To reduce the amount of data to be transferred, only the properties that are used by the frontend to visualize the track are converted.
     *
     * The track is represented as a [LineString] feature.
     * The wayPoints are represented as [Point] features.
     */
    fun toGeoJson(gpsContainer: GpsContainer): FeatureCollection {
        val geoTrackPoints =
            gpsContainer.track?.wayPoints?.map {
                val position = Position(it.longitude, it.latitude)
                val point = Point(position)
                point
            }
        val lineString = LineString(geoTrackPoints)
        val lineStringFeature = Feature(lineString)
        lineStringFeature.properties["name"] = gpsContainer.name

        val featureCollection = FeatureCollection()
        featureCollection.addFeature(lineStringFeature)

        gpsContainer.wayPoints.forEach { wayPoint ->
            val position = Position(wayPoint.latitude, wayPoint.longitude)
            val point = Point(position)
            val feature = Feature(point)

            wayPoint.name?.let { feature.properties["name"] = wayPoint.name.toString() }
            wayPoint.type?.let { feature.properties["type"] = wayPoint.type.toString() }

            featureCollection.addFeature(feature)
        }

        return featureCollection
    }

    fun fromGeoJson(featureCollection: FeatureCollection): GpsContainer {
        throw UnsupportedOperationException("Not yet implemented")
    }

    fun toTcx(gpsContainer: GpsContainer): TrainingCenterDatabase {
        val trainingCenterDatabase = TrainingCenterDatabase()

        val course = Course(gpsContainer.name!!)
        val wayPoints = gpsContainer.track!!.wayPoints
        val lap =
            Lap(
                totalTimeSeconds = (wayPoints[wayPoints.size - 1].time!!.epochSecond - wayPoints[0].time!!.epochSecond).toDouble(),
                distanceMeters = gpsContainer.track.calculateLength().toDouble(),
                beginPosition =
                    org.devshred.gpstools.formats.tcx.Position(
                        wayPoints[0].latitude,
                        wayPoints[0].longitude,
                    ),
                endPosition =
                    org.devshred.gpstools.formats.tcx.Position(
                        wayPoints[wayPoints.size - 1].latitude,
                        wayPoints[wayPoints.size - 1].longitude,
                    ),
                intensity = "Active",
            )
        course.setLap(lap)

        val track = org.devshred.gpstools.formats.tcx.Track()
        var distance = 0.0
        var previous: GpsWayPoint? = null
        for (point in gpsContainer.track.wayPoints) {
            if (previous != null) {
                distance += Geoid.WGS84.distance(previous.toGpx(), point.toGpx()).toDouble()
            }
            track.addTrackpoint(
                Trackpoint(
                    ZonedDateTime.ofInstant(point.time, org.devshred.gpstools.common.Constants.DEFAULT_TIMEZONE),
                    org.devshred.gpstools.formats.tcx.Position(point.latitude, point.longitude),
                    point.elevation!!.toDouble(),
                    distance,
                ),
            )
            previous = point
        }
        course.setTrack(track)

        gpsContainer.wayPoints.forEach { wayPoint: GpsWayPoint ->
            course.addCoursePoint(
                CoursePoint(
                    name = wayPoint.name.orElse { "unnamed" },
                    time = wayPoint.time?.atZone(org.devshred.gpstools.common.Constants.DEFAULT_TIMEZONE),
                    position = org.devshred.gpstools.formats.tcx.Position(wayPoint.latitude, wayPoint.longitude),
                    pointType = wayPoint.type?.tcxType,
                ),
            )
        }

        trainingCenterDatabase.addCourse(course)

        return trainingCenterDatabase
    }
}

fun GpsWayPoint.toGpx(): GpxWayPoint {
    val builder = GpxWayPoint.builder()

    builder.lat(latitude)
    builder.lon(longitude)

    elevation?.let { builder.ele(it) }
    time?.let { builder.time(it) }
    name?.let { builder.name(it) }
    type?.let { builder.sym(it.gpxSym) }
    speed?.let { builder.speed(it) }

    if (hasExtensionValues()) {
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument()
        val extensions = document.createElement("extensions")

        power?.let {
            val powerElement = document.createElement("power")
            powerElement.textContent = it.toString()
            extensions.appendChild(powerElement)
        }

        if (temperature != null || heartRate != null || cadence != null) {
            val trackPointExtensionElement = document.createElement("gpxtpx:TrackPointExtension")
            val xmlnsAttribute = document.createAttribute("xmlns:gpxtpx")

            // TODO: try to set xmlns once at root-level
            xmlnsAttribute.value = "http://www.garmin.com/xmlschemas/TrackPointExtension/v1"
            trackPointExtensionElement.setAttributeNodeNS(xmlnsAttribute)

            if (temperature != null) {
                val temperatureElement = document.createElement("gpxtpx:atemp")
                temperatureElement.textContent = temperature.toString()
                trackPointExtensionElement.appendChild(temperatureElement)
            }
            if (heartRate != null) {
                val heartrateElement = document.createElement("gpxtpx:hr")
                heartrateElement.textContent = heartRate.toString()
                trackPointExtensionElement.appendChild(heartrateElement)
            }
            if (cadence != null) {
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

private fun GpsWayPoint.hasExtensionValues(): Boolean {
    return power != null || temperature != null || heartRate != null || cadence != null
}

fun PoiType.toProto(): ProtoPoiType =
    when (this) {
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

fun GpsWayPoint.toProto(): ProtoWayPoint {
    val gps: GpsWayPoint = this
    return protoWayPoint {
        latitude = gps.latitude
        longitude = gps.longitude
        gps.elevation?.let { elevation = gps.elevation }
        gps.time?.let { time = Timestamp.newBuilder().setSeconds(gps.time.epochSecond).build() }
        gps.name?.let { name = gps.name }
        gps.type?.let { type = gps.type.toProto() }
        gps.speed?.let { speed = gps.speed }
        gps.power?.let { power = gps.power }
        gps.temperature?.let { temperature = gps.temperature }
        gps.heartRate?.let { heartRate = gps.heartRate }
        gps.cadence?.let { cadence = gps.cadence }
    }
}

fun ProtoWayPoint.toGps(): GpsWayPoint =
    GpsWayPoint(
        latitude = this.latitude,
        longitude = this.longitude,
        elevation = if (this.hasElevation()) this.elevation else null,
        name = if (this.hasName()) this.name else null,
        time = if (this.hasTime()) Instant.ofEpochSecond(this.time.seconds) else null,
        type = if (this.hasType()) this.type.toGps() else null,
        speed = if (this.hasSpeed()) this.speed else null,
        power = if (this.hasPower()) this.power else null,
        temperature = if (this.hasTemperature()) this.temperature else null,
        heartRate = if (this.hasHeartRate()) this.heartRate else null,
        cadence = if (this.hasCadence()) this.cadence else null,
    )

fun ProtoPoiType.toGps(): PoiType = PoiType.fromString(this.name)

fun GpxWayPoint.toGps(): GpsWayPoint {
    val gpx = this

    val extensionValues: ExtensionValues? =
        gpx.extensions.map {
            exploreDocument(it.documentElement)
        }.getOrNull()

    return GpsWayPoint(
        latitude = gpx.latitude.toDouble(),
        longitude = gpx.longitude.toDouble(),
        elevation = gpx.elevation.map { it.toDouble() }.getOrNull(),
        time = gpx.time.map { Instant.ofEpochSecond(it.epochSecond) }.getOrNull(),
        name = gpx.name.map { it }.getOrNull(),
        type = gpx.symbol.map { PoiType.fromGpxSym(it) }.orElseGet { null },
        power = extensionValues?.power,
        cadence = extensionValues?.cadence,
        temperature = extensionValues?.temperature,
        heartRate = extensionValues?.heartRate,
    )
}

fun exploreDocument(node: Node): ExtensionValues {
    var extensionValues = ExtensionValues()
    var childNode = node.firstChild
    while (childNode != null) {
        when (childNode.nodeName) {
            "power" -> extensionValues = extensionValues.copy(power = childNode.textContent.toInt())
            "gpxtpx:cad" -> extensionValues = extensionValues.copy(cadence = childNode.textContent.toInt())
            "gpxtpx:atemp" -> extensionValues = extensionValues.copy(temperature = childNode.textContent.toInt())
            "gpxtpx:hr" -> extensionValues = extensionValues.copy(heartRate = childNode.textContent.toInt())
        }
        val childValues = exploreDocument(childNode)
        extensionValues = extensionValues.union(childValues)
        childNode = childNode.nextSibling
    }
    return extensionValues
}

data class ExtensionValues(
    val power: Int? = null,
    val cadence: Int? = null,
    val temperature: Int? = null,
    val heartRate: Int? = null,
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

fun RecordMesg.toGps(): GpsWayPoint {
    return GpsWayPoint(
        latitude = this.getLatDegrees(),
        longitude = this.getLongDegrees(),
        elevation = this.altitude?.toDouble(),
        time = Instant.ofEpochSecond(this.timestamp.timestamp),
        speed = this.speed?.toDouble(),
        power = this.power,
        cadence = this.cadence?.toInt(),
        temperature = this.temperature?.toInt(),
        heartRate = this.heartRate?.toInt(),
    )
}

private fun RecordMesg.getLatDegrees() = this.positionLat * SEMICIRCLES_TO_DEGREES

private fun RecordMesg.getLongDegrees() = this.positionLong * SEMICIRCLES_TO_DEGREES
