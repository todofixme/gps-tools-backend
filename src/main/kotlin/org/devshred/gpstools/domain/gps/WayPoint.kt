package org.devshred.gpstools.domain.gps

import java.time.Instant
import javax.xml.parsers.DocumentBuilderFactory
import io.jenetics.jpx.WayPoint as GpxWayPoint

data class WayPoint(
    val latitude: Double,
    val longitude: Double,
    val elevation: Double? = null,
    val time: Instant? = null,
    val name: String? = null,
    val type: PoiType? = null,
    val speed: Double? = null,
    val power: Int? = null,
    val temperature: Int? = null,
    val heartrate: Int? = null,
    val cadence: Int? = null,
) {
    companion object {
        fun fromGpxPoint(gpxPoint: GpxWayPoint): WayPoint =
            WayPoint(
                latitude = gpxPoint.latitude.toDouble(),
                longitude = gpxPoint.longitude.toDouble(),
                elevation = gpxPoint.elevation.orElse(null)?.toDouble(),
                time = gpxPoint.time.orElse(null),
            )
    }

    fun toGpxPoint(): GpxWayPoint {
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

            if (temperature != null || heartrate != null || cadence != null) {
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
                if (heartrate != null) {
                    val heartrateElement = document.createElement("gpxtpx:hr")
                    heartrateElement.textContent = heartrate.toString()
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

    private fun hasExtensionValues(): Boolean {
        return power != null || temperature != null || heartrate != null || cadence != null
    }
}
