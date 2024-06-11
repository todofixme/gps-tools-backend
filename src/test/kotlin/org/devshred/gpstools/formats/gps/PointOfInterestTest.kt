package org.devshred.gpstools.formats.gps

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class PointOfInterestTest {
    @Test
    fun `should add pointOfInterest`() {
        val pointOfInterest1 =
            PointOfInterest(
                uuid = UUID.randomUUID(),
                latitude = 36.74881700,
                longitude = -4.07262399,
                time = Instant.ofEpochSecond(1262315764),
                type = PoiType.FOOD,
                name = "Highlight1",
            )

        val wayPoint2 =
            PointOfInterest(
                uuid = UUID.randomUUID(),
                latitude = 36.74881700,
                longitude = -4.07262399,
                time = Instant.ofEpochSecond(1262315764),
                type = PoiType.RESIDENCE,
                name = "Highlight2",
            )

        val mergedPoints = mergePoints(listOf(pointOfInterest1), listOf(wayPoint2))

        assertThat(mergedPoints).hasSize(2)
        assertThat(mergedPoints.map { it.name }).contains("Highlight1", "Highlight2")
    }

    @Test
    fun `should change pointOfInterest`() {
        val uuid = UUID.randomUUID()
        val pointOfInterest1 =
            PointOfInterest(
                uuid = uuid,
                latitude = 36.74881700,
                longitude = -4.07262399,
                time = Instant.ofEpochSecond(1262315764),
                type = PoiType.FOOD,
                name = "Highlight1",
            )

        val pointOfInterest2 =
            PointOfInterest(
                uuid = uuid,
                latitude = 36.74881700,
                longitude = -4.07262399,
                time = Instant.ofEpochSecond(1262315764),
                type = PoiType.RESIDENCE,
                name = "Highlight2",
            )

        val mergedWaypoints = mergePoints(listOf(pointOfInterest1), listOf(pointOfInterest2))

        assertThat(mergedWaypoints).hasSize(1)
        assertThat(mergedWaypoints.map { it.name }).contains("Highlight2")
    }
}
