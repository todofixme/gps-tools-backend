package org.devshred.gpstools.formats.gpx

import io.jenetics.jpx.GPX
import java.io.ByteArrayOutputStream

internal const val GPX_CREATOR = "GPS-Tools - https://gps-tools.pages.dev"

fun gpxToByteArrayOutputStream(gpx: GPX): ByteArrayOutputStream {
    val out = ByteArrayOutputStream()
    GPX.Writer.DEFAULT.write(gpx, out)

    return out
}
