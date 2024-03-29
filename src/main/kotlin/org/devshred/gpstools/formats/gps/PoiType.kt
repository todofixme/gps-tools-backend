package org.devshred.gpstools.formats.gps

enum class PoiType(val tcxType: String, val gpxSym: String) {
    GENERIC("Generic", "generic"),
    SUMMIT("Summit", "summit"),
    VALLEY("Valley", "valley"),
    WATER("Water", "water"),
    FOOD("Food", "food"),
    DANGER("Danger", "danger"),
    LEFT("Left", "left"),
    RIGHT("Right", "right"),
    STRAIGHT("Straight", "straight"),
    FIRST_AID("First Aid", "generic"),
    FOURTH_CATEGORY("4th Category", "summit"),
    THIRD_CATEGORY("3rd Category", "summit"),
    SECOND_CATEGORY("2nd Category", "summit"),
    FIRST_CATEGORY("1st Category", "summit"),
    HORS_CATEGORY("Hors Category", "summit"),
    RESIDENCE("Residence", "residence"),
    SPRINT("Sprint", "sprint"),
    ;

    companion object Factory {
        fun fromString(name: String): PoiType {
            return try {
                PoiType.valueOf(name.uppercase())
            } catch (e: IllegalArgumentException) {
                GENERIC
            }
        }

        fun fromTcxType(tcxType: String): PoiType? = entries.firstOrNull { it.tcxType.equals(tcxType, ignoreCase = true) }

        fun fromGpxSym(gpxSym: String): PoiType? = entries.firstOrNull { it.gpxSym.equals(gpxSym, ignoreCase = true) }
    }
}
