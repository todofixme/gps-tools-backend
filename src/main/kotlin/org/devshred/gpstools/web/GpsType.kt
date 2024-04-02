package org.devshred.gpstools.web

enum class GpsType(val mimeType: String) {
    GPX("application/gpx+xml"),
    TCX("application/vnd.garmin.tcx+xml"),
    FIT("application/fit"),
    JSON("application/geo+json"),
    ;

    companion object {
        fun fromTypeString(type: String): GpsType? = entries.firstOrNull { it.name == type.uppercase() }

        fun fromMimeType(mimeType: String): GpsType? = entries.firstOrNull { it.mimeType == mimeType }
    }
}
