package org.devshred.gpstools.formats.tcx

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty

class Lap(
    private val totalTimeSeconds: Double,
    private val distanceMeters: Double,
    private val beginPosition: Position,
    private val endPosition: Position,
    private val intensity: String,
) {
    @JacksonXmlProperty(localName = "TotalTimeSeconds", namespace = TCX_NAMESPACE)
    fun getTotalTimeSeconds() = totalTimeSeconds

    @JacksonXmlProperty(localName = "DistanceMeters", namespace = TCX_NAMESPACE)
    fun getDistanceMeters() = distanceMeters

    @JacksonXmlProperty(localName = "BeginPosition", namespace = TCX_NAMESPACE)
    fun getBeginPosition() = beginPosition

    @JacksonXmlProperty(localName = "EndPosition", namespace = TCX_NAMESPACE)
    fun getEndPosition() = endPosition

    @JacksonXmlProperty(localName = "Intensity", namespace = TCX_NAMESPACE)
    fun getIntensity() = intensity
}
