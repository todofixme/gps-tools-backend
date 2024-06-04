package org.devshred.gpstools.formats.gps

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class WayPointTest {
    @Test
    fun `should add waypoint`() {
        val wayPoint1 =
            WayPoint(
                latitude = 36.74881700,
                longitude = -4.07262399,
                time = Instant.ofEpochSecond(1262315764),
                type = PoiType.FOOD,
                name = "Highlight1",
                uuid = UUID.randomUUID(),
            )

        val wayPoint2 =
            WayPoint(
                latitude = 36.74881700,
                longitude = -4.07262399,
                time = Instant.ofEpochSecond(1262315764),
                type = PoiType.RESIDENCE,
                name = "Highlight2",
                uuid = UUID.randomUUID(),
            )

        val mergedWaypoints = mergeWaypoints(listOf(wayPoint1), listOf(wayPoint2))

        assertThat(mergedWaypoints).hasSize(2)
        assertThat(mergedWaypoints.map { it.name }).contains("Highlight1", "Highlight2")
    }

    @Test
    fun `should change waypoint`() {
        val uuid = UUID.randomUUID()
        val wayPoint1 =
            WayPoint(
                latitude = 36.74881700,
                longitude = -4.07262399,
                time = Instant.ofEpochSecond(1262315764),
                type = PoiType.FOOD,
                name = "Highlight1",
                uuid = uuid,
            )

        val wayPoint2 =
            WayPoint(
                latitude = 36.74881700,
                longitude = -4.07262399,
                time = Instant.ofEpochSecond(1262315764),
                type = PoiType.RESIDENCE,
                name = "Highlight2",
                uuid = uuid,
            )

        val mergedWaypoints = mergeWaypoints(listOf(wayPoint1), listOf(wayPoint2))

        assertThat(mergedWaypoints).hasSize(1)
        assertThat(mergedWaypoints.map { it.name }).contains("Highlight2")
    }
}
