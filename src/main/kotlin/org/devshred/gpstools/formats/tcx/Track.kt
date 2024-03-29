package org.devshred.gpstools.formats.tcx

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty

class Track(
    @JacksonXmlElementWrapper(useWrapping = false)
    @JacksonXmlProperty(localName = "Trackpoint")
    private val trackpoints: MutableList<Trackpoint> = mutableListOf(),
) {
    @JacksonXmlProperty(localName = "Trackpoint", namespace = TCX_NAMESPACE)
    fun getTrackpoints() = trackpoints

    fun addTrackpoint(trackpoint: Trackpoint) {
        trackpoints.add(trackpoint)
    }
}
