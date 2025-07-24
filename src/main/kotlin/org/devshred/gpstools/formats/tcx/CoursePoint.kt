package org.devshred.gpstools.formats.tcx

import com.fasterxml.jackson.databind.annotation.JsonSerialize
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement
import com.fasterxml.jackson.datatype.jsr310.ser.ZonedDateTimeSerializer
import java.time.ZonedDateTime

@JacksonXmlRootElement
class CoursePoint(
    @param:JacksonXmlProperty(localName = "Name")
    private val name: String,
    @param:JacksonXmlProperty(localName = "Time")
    private val time: ZonedDateTime? = null,
    @param:JacksonXmlProperty(localName = "Position")
    private val position: Position,
    @param:JacksonXmlProperty(localName = "PointType")
    private val pointType: String? = null,
) {
    @JacksonXmlProperty(localName = "Name", namespace = TCX_NAMESPACE)
    fun getName() = name

    @JacksonXmlProperty(localName = "Time", namespace = TCX_NAMESPACE)
    @JsonSerialize(using = ZonedDateTimeSerializer::class)
    fun getTime(): ZonedDateTime? = time

    @JacksonXmlProperty(localName = "Position", namespace = TCX_NAMESPACE)
    fun getPosition() = position

    @JacksonXmlProperty(localName = "PointType", namespace = TCX_NAMESPACE)
    fun getPointType() = pointType
}
