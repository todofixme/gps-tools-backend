package org.devshred.gpstools.domain

import org.assertj.core.api.Assertions.assertThat
import org.devshred.gpstools.web.GpsType
import org.junit.jupiter.api.Test

class GpsTypeTest {
    @Test
    fun `from type to GpsType`() {
        assertThat(GpsType.fromTypeString("tcx")).isEqualTo(GpsType.TCX)
        assertThat(GpsType.fromTypeString("gpx")).isEqualTo(GpsType.GPX)
        assertThat(GpsType.fromTypeString("fit")).isEqualTo(GpsType.FIT)
        assertThat(GpsType.fromTypeString("json")).isEqualTo(GpsType.JSON)

        assertThat(GpsType.fromTypeString("unknown")).isNull()
    }

    @Test
    fun `from mime-type to GpsType`() {
        assertThat(GpsType.fromMimeType("application/vnd.garmin.tcx+xml")).isEqualTo(GpsType.TCX)
        assertThat(GpsType.fromMimeType("application/gpx+xml")).isEqualTo(GpsType.GPX)
        assertThat(GpsType.fromMimeType("application/fit")).isEqualTo(GpsType.FIT)
        assertThat(GpsType.fromMimeType("application/geo+json")).isEqualTo(GpsType.JSON)

        assertThat(GpsType.fromMimeType("application/something")).isNull()
    }
}
