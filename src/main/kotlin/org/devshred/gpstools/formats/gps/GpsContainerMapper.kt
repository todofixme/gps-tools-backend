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
import org.devshred.gpstools.common.Constants.DEFAULT_TIMEZONE
import org.devshred.gpstools.common.orElse
import org.devshred.gpstools.formats.gps.GpsContainerMapper.Constants.SEMICIRCLES_TO_DEGREES
import org.devshred.gpstools.formats.gpx.GPX_CREATOR
import org.devshred.gpstools.formats.proto.ProtoContainer
import org.devshred.gpstools.formats.proto.ProtoPoiType
import org.devshred.gpstools.formats.proto.ProtoPointOfInterest
import org.devshred.gpstools.formats.proto.ProtoTrackPoint
import org.devshred.gpstools.formats.proto.protoContainer
import org.devshred.gpstools.formats.proto.protoPointOfInterest
import org.devshred.gpstools.formats.proto.protoTrack
import org.devshred.gpstools.formats.proto.protoTrackPoint
import org.devshred.gpstools.formats.tcx.Course
import org.devshred.gpstools.formats.tcx.CoursePoint
import org.devshred.gpstools.formats.tcx.Lap
import org.devshred.gpstools.formats.tcx.Trackpoint
import org.devshred.gpstools.formats.tcx.TrainingCenterDatabase
import org.springframework.stereotype.Component
import org.w3c.dom.Node
import java.time.Instant
import java.time.ZonedDateTime
import java.util.UUID
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.jvm.optionals.getOrNull
import kotlin.math.pow
import io.jenetics.jpx.WayPoint as GpxWayPoint
import org.devshred.gpstools.formats.gps.PointOfInterest as GpsPointOfInterest
import org.devshred.gpstools.formats.gps.TrackPoint as GpsTrackPoint
import org.devshred.gpstools.formats.tcx.Position as TcxPosition
import org.devshred.gpstools.formats.tcx.Track as TcxTrack

@Component
class GpsContainerMapper {
    object Constants {
        val SEMICIRCLES_TO_DEGREES = 180 / 2.0.pow(31.0)
    }

    fun toProto(gpsContainer: GpsContainer): ProtoContainer =
        protoContainer {
            gpsContainer.name?.let { name = it }
            gpsContainer.pointsOfInterest.map { it.toProto() }.forEach { pointsOfInterest += it }
            gpsContainer.track?.let { gpsTrack ->
                track =
                    protoTrack {
                        gpsTrack.trackPoints.forEach { trackPoints += it.toProto() }
                    }
            }
        }

    fun fromProto(protoContainer: ProtoContainer): GpsContainer =
        GpsContainer(
            name = protoContainer.name,
            pointsOfInterest = protoContainer.pointsOfInterestList.map { it.toGps() },
            track =
                protoContainer.track
                    ?.let { Track(protoContainer.track.trackPointsList.map { it.toGps() }) }
                    .orElse { null },
        )

    fun toGpx(gpsCont: GpsContainer): GPX {
        val gpxBuilder = GPX.builder().creator(GPX_CREATOR)
        gpsCont.name?.let { gpsName -> gpxBuilder.metadata { gpx -> gpx.name(gpsName) } }
        gpxBuilder.wayPoints(gpsCont.pointsOfInterest.map { it.toGpx() })
        gpxBuilder.addTrack { track ->
            track.name(gpsCont.name)
            track.addSegment { segment ->
                segment.points(gpsCont.track?.trackPoints?.map { it.toGpx() })
            }
        }
        return gpxBuilder.build()
    }

    fun fromGpx(gpx: GPX): GpsContainer {
        val trackName: String? =
            if (gpx.tracks.isNotEmpty() && gpx.tracks[0].name.isPresent) {
                gpx.tracks[0].name.get()
            } else if (gpx.metadata.isPresent &&
                gpx.metadata
                    .get()
                    .name.isPresent
            ) {
                gpx.metadata
                    .get()
                    .name
                    .get()
            } else {
                null
            }
        val wayPoints = gpx.wayPoints.map { it.toGpsPointOfInterest() }
        val track: Track? =
            if (gpx.tracks.isNotEmpty() && gpx.tracks[0].segments.isNotEmpty()) {
                Track(
                    gpx.tracks[0]
                        .segments
                        .flatMap { it.points }
                        .map { it.toGpsTrackPoint() },
                )
            } else {
                null
            }

        return GpsContainer(
            name = trackName,
            pointsOfInterest = wayPoints,
            track = track,
        )
    }

    fun fromFit(fitMessages: FitMessages?): GpsContainer {
        val track: Track? =
            fitMessages?.let { fit ->
                Track(
                    fit.recordMesgs
                        .filter { it.positionLat != null && it.positionLong != null }
                        .map { records -> records.toGpsTrackPoint() },
                ).orElse { null }
            }

        return GpsContainer(
            name = "Activity",
            pointsOfInterest = emptyList(),
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
        val featureCollection = FeatureCollection()

        gpsContainer.track?.trackPoints?.map {
            val position = Position(it.longitude, it.latitude)
            val point = Point(position)
            point
        }?.let {
            val lineString = LineString(it)
            val lineStringFeature = Feature(lineString)
            lineStringFeature.properties["name"] = gpsContainer.name

            featureCollection.addFeature(lineStringFeature)
        }

        toGeoJsonPoints(gpsContainer.pointsOfInterest)
            .forEach { feature -> featureCollection.addFeature(feature) }

        return featureCollection
    }

    fun toGeoJsonPoints(wayPoints: List<GpsPointOfInterest>): List<Feature> =
        wayPoints.map { wayPoint ->
            val position = Position(wayPoint.latitude, wayPoint.longitude)
            val point = Point(position)

            val feature = Feature(point)
            feature.properties["uuid"] = wayPoint.uuid.toString()
            wayPoint.name?.let { feature.properties["name"] = wayPoint.name.toString() }
            wayPoint.type?.let { feature.properties["type"] = wayPoint.type.toString() }

            feature
        }

    fun toTcx(gpsContainer: GpsContainer): TrainingCenterDatabase {
        val trainingCenterDatabase = TrainingCenterDatabase()

        val course = Course(gpsContainer.name!!)
        gpsContainer.track?.let { track ->
            val trackPoints = track.trackPoints
            val firstTrackPoint = trackPoints[0]
            val lastTrackPoint = trackPoints[trackPoints.size - 1]
            val durationInSeconds =
                if (firstTrackPoint.time != null && lastTrackPoint.time != null && firstTrackPoint.time < lastTrackPoint.time) {
                    (lastTrackPoint.time.epochSecond - firstTrackPoint.time.epochSecond).toDouble()
                } else {
                    0.0
                }
            val lap =
                Lap(
                    totalTimeSeconds = durationInSeconds,
                    distanceMeters = track.calculateLength().toDouble(),
                    beginPosition =
                        TcxPosition(
                            firstTrackPoint.latitude,
                            firstTrackPoint.longitude,
                        ),
                    endPosition =
                        TcxPosition(
                            lastTrackPoint.latitude,
                            lastTrackPoint.longitude,
                        ),
                    intensity = "Active",
                )
            course.setLap(lap)

            val tcxTrack = TcxTrack()
            var distance = 0.0
            var previous: GpsTrackPoint? = null
            val timeFallback = ZonedDateTime.now(DEFAULT_TIMEZONE)
            for (trackPoint in track.trackPoints) {
                if (previous != null) {
                    distance += Geoid.WGS84.distance(previous.toGpx(), trackPoint.toGpx()).toDouble()
                }
                tcxTrack.addTrackpoint(
                    Trackpoint(
                        trackPoint.time
                            ?.let { ZonedDateTime.ofInstant(trackPoint.time, DEFAULT_TIMEZONE) }
                            .orElse { timeFallback },
                        TcxPosition(trackPoint.latitude, trackPoint.longitude),
                        trackPoint.elevation!!.toDouble(),
                        distance,
                    ),
                )
                previous = trackPoint
            }
            course.setTrack(tcxTrack)
        }

        gpsContainer.pointsOfInterest.forEach { poi: GpsPointOfInterest ->
            course.addCoursePoint(
                CoursePoint(
                    name = poi.name.orElse { "unnamed" },
                    time = poi.time?.atZone(DEFAULT_TIMEZONE),
                    position = TcxPosition(poi.latitude, poi.longitude),
                    pointType = poi.type?.tcxType,
                ),
            )
        }

        trainingCenterDatabase.addCourse(course)

        return trainingCenterDatabase
    }
}

fun GpsTrackPoint.toGpx(): GpxWayPoint {
    val builder = GpxWayPoint.builder()

    builder.lat(latitude)
    builder.lon(longitude)

    elevation?.let { builder.ele(it) }
    time?.let { builder.time(it) }
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

fun GpsPointOfInterest.toGpx(): GpxWayPoint {
    val builder = GpxWayPoint.builder()

    builder.lat(latitude)
    builder.lon(longitude)

    elevation?.let { builder.ele(it) }
    time?.let { builder.time(it) }

    name?.let { builder.name(it) }
    type?.let { builder.type(it.name) }

    return builder.build()
}

@Suppress("ktlint")
private fun GpsTrackPoint.hasExtensionValues(): Boolean =
    power != null || temperature != null || heartRate != null || cadence != null

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

fun GpsTrackPoint.toProto(): ProtoTrackPoint {
    val gps: GpsTrackPoint = this
    return protoTrackPoint {
        latitude = gps.latitude
        longitude = gps.longitude
        gps.elevation?.let { elevation = gps.elevation }
        gps.time?.let { time = Timestamp.newBuilder().setSeconds(gps.time.epochSecond).build() }
        gps.speed?.let { speed = gps.speed }
        gps.power?.let { power = gps.power }
        gps.temperature?.let { temperature = gps.temperature }
        gps.heartRate?.let { heartRate = gps.heartRate }
        gps.cadence?.let { cadence = gps.cadence }
    }
}

fun GpsPointOfInterest.toProto(): ProtoPointOfInterest {
    val gps: GpsPointOfInterest = this
    return protoPointOfInterest {
        uuid = gps.uuid.toString()
        latitude = gps.latitude
        longitude = gps.longitude
        gps.elevation?.let { elevation = gps.elevation }
        gps.time?.let { time = Timestamp.newBuilder().setSeconds(gps.time.epochSecond).build() }
        gps.name?.let { name = gps.name }
        gps.type?.let { type = gps.type.toProto() }
    }
}

fun ProtoTrackPoint.toGps(): GpsTrackPoint =
    GpsTrackPoint(
        latitude = this.latitude,
        longitude = this.longitude,
        elevation = if (this.hasElevation()) this.elevation else null,
        time = if (this.hasTime()) Instant.ofEpochSecond(this.time.seconds) else null,
        speed = if (this.hasSpeed()) this.speed else null,
        power = if (this.hasPower()) this.power else null,
        temperature = if (this.hasTemperature()) this.temperature else null,
        heartRate = if (this.hasHeartRate()) this.heartRate else null,
        cadence = if (this.hasCadence()) this.cadence else null,
    )

fun ProtoPointOfInterest.toGps(): GpsPointOfInterest =
    GpsPointOfInterest(
        uuid = UUID.fromString(this.uuid),
        latitude = this.latitude,
        longitude = this.longitude,
        elevation = if (this.hasElevation()) this.elevation else null,
        name = if (this.hasName()) this.name else null,
        time = if (this.hasTime()) Instant.ofEpochSecond(this.time.seconds) else null,
        type = if (this.hasType()) this.type.toGps() else null,
    )

fun ProtoPoiType.toGps(): PoiType = PoiType.fromString(this.name)

fun GpxWayPoint.toGpsPointOfInterest(): GpsPointOfInterest {
    val gpx = this

    return GpsPointOfInterest(
        uuid = UUID.randomUUID(),
        latitude = gpx.latitude.toDouble(),
        longitude = gpx.longitude.toDouble(),
        elevation = gpx.elevation.map { it.toDouble() }.getOrNull(),
        time = gpx.time.map { Instant.ofEpochSecond(it.epochSecond) }.getOrNull(),
        name = gpx.name.map { it }.getOrNull(),
        type = gpx.type.map { PoiType.fromGpxSym(it) }.orElseGet { null },
    )
}

fun GpxWayPoint.toGpsTrackPoint(): GpsTrackPoint {
    val gpx = this

    val extensionValues: ExtensionValues? =
        gpx.extensions
            .map {
                exploreDocument(it.documentElement)
            }.getOrNull()

    return GpsTrackPoint(
        latitude = gpx.latitude.toDouble(),
        longitude = gpx.longitude.toDouble(),
        elevation = gpx.elevation.map { it.toDouble() }.getOrNull(),
        time = gpx.time.map { Instant.ofEpochSecond(it.epochSecond) }.getOrNull(),
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
    fun union(newValues: ExtensionValues): ExtensionValues =
        this.copy(
            power = newValues.power ?: this.power,
            cadence = newValues.cadence ?: this.cadence,
            temperature = newValues.temperature ?: this.temperature,
            heartRate = newValues.heartRate ?: this.heartRate,
        )
}

fun RecordMesg.toGpsTrackPoint(): GpsTrackPoint =
    GpsTrackPoint(
        latitude = this.getLatDegrees(),
        longitude = this.getLongDegrees(),
        elevation = this.altitude?.toDouble(),
        time = this.timestamp.date.toInstant(),
        speed = this.speed?.toDouble(),
        power = this.power,
        cadence = this.cadence?.toInt(),
        temperature = this.temperature?.toInt(),
        heartRate = this.heartRate?.toInt(),
    )

private fun RecordMesg.getLatDegrees() = this.positionLat * SEMICIRCLES_TO_DEGREES

private fun RecordMesg.getLongDegrees() = this.positionLong * SEMICIRCLES_TO_DEGREES
