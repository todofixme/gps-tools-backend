package org.devshred.gpstools.formats.tcx

import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.dataformat.xml.XmlMapper
import com.fasterxml.jackson.dataformat.xml.ser.ToXmlGenerator
import io.jenetics.jpx.geom.Geoid
import org.devshred.gpstools.common.orElse
import org.devshred.gpstools.formats.gps.GpsContainer
import org.devshred.gpstools.formats.gps.WayPoint
import org.devshred.gpstools.formats.gps.toGpx
import java.io.ByteArrayOutputStream
import java.time.ZoneId
import java.time.ZonedDateTime

object TcxTools {
    val XML_MAPPER: XmlMapper =
        XmlMapper.Builder(XmlMapper())
            .enable(SerializationFeature.INDENT_OUTPUT)
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .build()

    init {
        XML_MAPPER.factory.enable(ToXmlGenerator.Feature.WRITE_XML_DECLARATION)
    }
}

fun tcxToByteArrayOutputStream(tcx: TrainingCenterDatabase): ByteArrayOutputStream {
    val out = ByteArrayOutputStream()
    TcxTools.XML_MAPPER.writeValue(out, tcx)
    return out
}

private const val MAX_DISTANCE_BETWEEN_TRACK_AND_WAYPOINT = 500

fun createTcxFromGpsContainer(
    gpsContainer: GpsContainer,
    findNearest: Boolean = false,
): TrainingCenterDatabase {
    val trainingCenterDatabase = TrainingCenterDatabase()

    val course = Course(gpsContainer.name!!)
    val waiPoints = gpsContainer.track!!.wayPoints
    val lap =
        Lap(
            totalTimeSeconds = (waiPoints[waiPoints.size - 1].time!!.epochSecond - waiPoints[0].time!!.epochSecond).toDouble(),
            distanceMeters = gpsContainer.track.calculateLength().toDouble(),
            beginPosition = Position(waiPoints[0].latitude, waiPoints[0].longitude),
            endPosition =
                Position(
                    waiPoints[waiPoints.size - 1].latitude,
                    waiPoints[waiPoints.size - 1].longitude,
                ),
            intensity = "Active",
        )
    course.setLap(lap)

    val track = Track()
    var distance = 0.0
    var previous: WayPoint? = null
    for (point in gpsContainer.track.wayPoints) {
        if (previous != null) {
            distance += Geoid.WGS84.distance(previous.toGpx(), point.toGpx()).toDouble()
        }
        track.addTrackpoint(
            Trackpoint(
                ZonedDateTime.ofInstant(point.time, ZoneId.of("UTC")),
                Position(point.latitude, point.longitude),
                point.elevation!!.toDouble(),
                distance,
            ),
        )
        previous = point
    }
    course.setTrack(track)

    gpsContainer.wayPoints.forEach { wayPoint: WayPoint ->
        val pointToAdd =
            when (findNearest) {
                true -> gpsContainer.findWayPointOnTrackNearestTo(wayPoint, MAX_DISTANCE_BETWEEN_TRACK_AND_WAYPOINT)
                false -> wayPoint
            }
        pointToAdd?.let {
            course.addCoursePoint(
                CoursePoint(
                    name = wayPoint.name.orElse { "unnamed" },
                    time = wayPoint.time?.atZone(ZoneId.of("UTC")),
                    position = Position(pointToAdd.latitude, pointToAdd.longitude),
                    pointType = wayPoint.type?.tcxType,
                ),
            )
        }
    }

    trainingCenterDatabase.addCourse(course)

    return trainingCenterDatabase
}
