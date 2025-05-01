package org.devshred.gpstools.domain.gps

import org.assertj.core.api.Assertions.assertThat
import org.devshred.gpstools.formats.gps.GpsContainer
import org.devshred.gpstools.formats.gps.PoiType
import org.devshred.gpstools.formats.gps.PointOfInterest
import org.devshred.gpstools.formats.gps.Track
import org.devshred.gpstools.formats.gps.TrackPoint
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class GpsContainerTest {
    private val gpsContainer =
        GpsContainer(
            name = "Happy Path Example",
            pointsOfInterest =
                listOf(
                    PointOfInterest(
                        uuid = UUID.randomUUID(),
                        latitude = 36.74881700,
                        longitude = -4.07262399,
                        time = Instant.ofEpochSecond(1262315764),
                        type = PoiType.FOOD,
                        name = "Highlight",
                    ),
                ),
            track =
                Track(
                    trackPoints =
                        listOf(
                            TrackPoint(
                                latitude = 36.72100500,
                                longitude = -4.41088200,
                                elevation = 14.000000,
                                time = Instant.ofEpochSecond(1262304000),
                            ),
                            TrackPoint(
                                latitude = 36.74881700,
                                longitude = -4.07262399,
                                elevation = 3.0000000,
                                time = Instant.ofEpochSecond(1262315764),
                            ),
                            TrackPoint(
                                latitude = 36.73361890,
                                longitude = -3.68807099,
                                elevation = 11.000000,
                                time = Instant.ofEpochSecond(1262332771),
                            ),
                        ),
                ),
        )

    @Test
    fun `optimized wayPoint - put on track`() {
        val containerToTest =
            gpsContainer.copy(
                pointsOfInterest =
                    listOf(
                        PointOfInterest(
                            uuid = UUID.randomUUID(),
                            latitude = 36.74981700,
                            longitude = -4.07262399,
                            time = Instant.ofEpochSecond(1262315764),
                            type = PoiType.FOOD,
                            name = "Highlight",
                        ),
                    ),
            )

        val actual = containerToTest.withOptimizedPointsOfInterest()

        assertThat(actual.pointsOfInterest[0].latitude).isEqualTo(36.74881700)
    }

    @Test
    fun `optimize point filters out waypoint, since distance higher than tolerance`() {
        val containerToTest =
            gpsContainer.copy(
                pointsOfInterest =
                    listOf(
                        PointOfInterest(
                            uuid = UUID.randomUUID(),
                            latitude = 36.74181700,
                            longitude = -4.07262399,
                            time = Instant.ofEpochSecond(1262315764),
                            type = PoiType.FOOD,
                            name = "Highlight",
                        ),
                    ),
            )

        val actual = containerToTest.withOptimizedPointsOfInterest()

        assertThat(actual.pointsOfInterest).isEmpty()
    }

    @Test
    fun `optimized wayPoint - order waypoints by time`() {
        val containerToTest =
            gpsContainer.copy(
                pointsOfInterest =
                    listOf(
                        PointOfInterest(
                            UUID.randomUUID(),
                            latitude = 36.732,
                            longitude = -3.688,
                            type = PoiType.RESIDENCE,
                            name = "Finish",
                        ),
                        PointOfInterest(
                            UUID.randomUUID(),
                            latitude = 36.722,
                            longitude = -4.410,
                            type = PoiType.STRAIGHT,
                            name = "Start",
                        ),
                    ),
            )

        val actual = containerToTest.withOptimizedPointsOfInterest()

        assertThat(actual.pointsOfInterest[0].name).isEqualTo("Start")
        assertThat(actual.pointsOfInterest[0].latitude).isEqualTo(36.72100500)

        assertThat(actual.pointsOfInterest[1].name).isEqualTo("Finish")
        assertThat(actual.pointsOfInterest[1].latitude).isEqualTo(36.73361890)
    }
}
