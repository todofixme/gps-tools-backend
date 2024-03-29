package org.devshred.gpstools.formats.tcx

import com.fasterxml.jackson.databind.annotation.JsonSerialize
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty
import com.fasterxml.jackson.datatype.jsr310.ser.ZonedDateTimeSerializer
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.ZonedDateTime

data class Trackpoint(
    @JsonSerialize(using = ZonedDateTimeSerializer::class)
    @JacksonXmlProperty(localName = "Time")
    private val time: ZonedDateTime,
    @JacksonXmlProperty(localName = "Position")
    private val position: Position,
    @JacksonXmlProperty(localName = "AltitudeMeters")
    private val altitudeMeters: BigDecimal,
    @JacksonXmlProperty(localName = "DistanceMeters")
    private val distanceMeters: BigDecimal,
) {
    constructor(time: ZonedDateTime, position: Position, altitudeMeters: Double, distanceMeters: Double) : this(
        time,
        position,
        BigDecimal(altitudeMeters).setScale(6, RoundingMode.HALF_UP),
        BigDecimal(distanceMeters).setScale(3, RoundingMode.HALF_UP),
    )

    @JacksonXmlProperty(localName = "Time", namespace = TCX_NAMESPACE)
    fun getTime() = time

    @JacksonXmlProperty(localName = "Position", namespace = TCX_NAMESPACE)
    fun getPosition() = position

    @JacksonXmlProperty(localName = "AltitudeMeters", namespace = TCX_NAMESPACE)
    fun getAltitudeMeters() = altitudeMeters

    @JacksonXmlProperty(localName = "DistanceMeters", namespace = TCX_NAMESPACE)
    fun getDistanceMeters() = distanceMeters
}
