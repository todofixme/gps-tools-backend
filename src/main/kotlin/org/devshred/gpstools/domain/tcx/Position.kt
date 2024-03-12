package org.devshred.gpstools.domain.tcx

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty
import java.math.BigDecimal
import java.math.RoundingMode

class Position(
    private val latitudeDegrees: BigDecimal,
    private val longitudeDegrees: BigDecimal,
) {
    constructor(
        latitudeDegrees: Double,
        longitudeDegrees: Double,
    ) : this(
        BigDecimal(latitudeDegrees).setScale(8, RoundingMode.HALF_UP),
        BigDecimal(longitudeDegrees).setScale(8, RoundingMode.HALF_UP),
    )

    @JacksonXmlProperty(localName = "LatitudeDegrees", namespace = TCX_NAMESPACE)
    fun getLatitudeDegrees() = latitudeDegrees

    @JacksonXmlProperty(localName = "LongitudeDegrees", namespace = TCX_NAMESPACE)
    fun getLongitudeDegrees() = longitudeDegrees
}
