package org.devshred.gpstools.domain.gps

import org.assertj.core.api.Assertions.assertThat
import org.devshred.gpstools.domain.gps.PoiType.Factory.fromGpxSym
import org.devshred.gpstools.domain.gps.PoiType.Factory.fromString
import org.devshred.gpstools.domain.gps.PoiType.Factory.fromTcxType
import org.junit.jupiter.api.Test

class PoiTypeTest {
    @Test
    fun `check fromString`() {
        assertThat(fromString("GENERIC")).isEqualTo(PoiType.GENERIC)
        assertThat(fromString("SUMMIT")).isEqualTo(PoiType.SUMMIT)
        assertThat(fromString("VALLEY")).isEqualTo(PoiType.VALLEY)
        assertThat(fromString("WATER")).isEqualTo(PoiType.WATER)
        assertThat(fromString("FOOD")).isEqualTo(PoiType.FOOD)
        assertThat(fromString("DANGER")).isEqualTo(PoiType.DANGER)
        assertThat(fromString("LEFT")).isEqualTo(PoiType.LEFT)
        assertThat(fromString("RIGHT")).isEqualTo(PoiType.RIGHT)
        assertThat(fromString("STRAIGHT")).isEqualTo(PoiType.STRAIGHT)
        assertThat(fromString("FIRST_AID")).isEqualTo(PoiType.FIRST_AID)
        assertThat(fromString("FOURTH_CATEGORY")).isEqualTo(PoiType.FOURTH_CATEGORY)
        assertThat(fromString("THIRD_CATEGORY")).isEqualTo(PoiType.THIRD_CATEGORY)
        assertThat(fromString("SECOND_CATEGORY")).isEqualTo(PoiType.SECOND_CATEGORY)
        assertThat(fromString("FIRST_CATEGORY")).isEqualTo(PoiType.FIRST_CATEGORY)
        assertThat(fromString("HORS_CATEGORY")).isEqualTo(PoiType.HORS_CATEGORY)
        assertThat(fromString("RESIDENCE")).isEqualTo(PoiType.RESIDENCE)
        assertThat(fromString("SPRINT")).isEqualTo(PoiType.SPRINT)
    }

    @Test
    fun `fromString handles unknown type`() {
        assertThat(fromString("UNKNOWN")).isEqualTo(PoiType.GENERIC)
    }

    @Test
    fun `check fromTcxType`() {
        assertThat(fromTcxType("Generic")).isEqualTo(PoiType.GENERIC)
        assertThat(fromTcxType("Summit")).isEqualTo(PoiType.SUMMIT)
        assertThat(fromTcxType("Valley")).isEqualTo(PoiType.VALLEY)
        assertThat(fromTcxType("Water")).isEqualTo(PoiType.WATER)
        assertThat(fromTcxType("Food")).isEqualTo(PoiType.FOOD)
        assertThat(fromTcxType("Danger")).isEqualTo(PoiType.DANGER)
        assertThat(fromTcxType("Left")).isEqualTo(PoiType.LEFT)
        assertThat(fromTcxType("Right")).isEqualTo(PoiType.RIGHT)
        assertThat(fromTcxType("Straight")).isEqualTo(PoiType.STRAIGHT)
        assertThat(fromTcxType("First Aid")).isEqualTo(PoiType.FIRST_AID)
        assertThat(fromTcxType("4th Category")).isEqualTo(PoiType.FOURTH_CATEGORY)
        assertThat(fromTcxType("3rd Category")).isEqualTo(PoiType.THIRD_CATEGORY)
        assertThat(fromTcxType("2nd Category")).isEqualTo(PoiType.SECOND_CATEGORY)
        assertThat(fromTcxType("1st Category")).isEqualTo(PoiType.FIRST_CATEGORY)
        assertThat(fromTcxType("Hors Category")).isEqualTo(PoiType.HORS_CATEGORY)
        assertThat(fromTcxType("Residence")).isEqualTo(PoiType.RESIDENCE)
        assertThat(fromTcxType("Sprint")).isEqualTo(PoiType.SPRINT)
    }

    @Test
    fun `fromTcxType returns null if element was not found`() {
        assertThat(fromTcxType("unknown")).isNull()
    }

    @Test
    fun `check fromGpxSym`() {
        assertThat(fromGpxSym("generic")).isEqualTo(PoiType.GENERIC)
        assertThat(fromGpxSym("summit")).isEqualTo(PoiType.SUMMIT)
        assertThat(fromGpxSym("valley")).isEqualTo(PoiType.VALLEY)
        assertThat(fromGpxSym("water")).isEqualTo(PoiType.WATER)
        assertThat(fromGpxSym("food")).isEqualTo(PoiType.FOOD)
        assertThat(fromGpxSym("danger")).isEqualTo(PoiType.DANGER)
        assertThat(fromGpxSym("left")).isEqualTo(PoiType.LEFT)
        assertThat(fromGpxSym("right")).isEqualTo(PoiType.RIGHT)
        assertThat(fromGpxSym("straight")).isEqualTo(PoiType.STRAIGHT)
        assertThat(fromGpxSym("residence")).isEqualTo(PoiType.RESIDENCE)
        assertThat(fromGpxSym("sprint")).isEqualTo(PoiType.SPRINT)
    }

    @Test
    fun `fromGpxSym returns null if element was not found`() {
        assertThat(fromGpxSym("unknown")).isNull()
    }
}
