package org.devshred.gpstools.formats.tcx

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty

class Course(
    @param:JacksonXmlProperty(localName = "Name")
    private val name: String,
    @param:JacksonXmlProperty(localName = "Lap")
    private var lap: Lap? = null,
    @param:JacksonXmlProperty(localName = "Track")
    private var track: Track? = null,
    @param:JacksonXmlProperty(localName = "CoursePoint")
    @param:JacksonXmlElementWrapper(useWrapping = false)
    private val coursePoints: MutableList<CoursePoint> = mutableListOf(),
) {
    @JacksonXmlProperty(localName = "Name", namespace = TCX_NAMESPACE)
    fun getName() = name

    @JacksonXmlProperty(localName = "Lap", namespace = TCX_NAMESPACE)
    fun getLap() = lap

    fun setLap(lap: Lap?) {
        this.lap = lap
    }

    @JacksonXmlProperty(localName = "Track", namespace = TCX_NAMESPACE)
    fun getTrack() = track

    fun setTrack(track: Track?) {
        this.track = track
    }

    @JacksonXmlProperty(localName = "CoursePoint", namespace = TCX_NAMESPACE)
    fun getCoursePoints() = coursePoints

    fun addCoursePoint(coursePoint: CoursePoint) {
        coursePoints.add(coursePoint)
    }
}
