package org.devshred.gpstools.domain.tcx

import org.devshred.gpstools.domain.gps.GpsContainer
import org.devshred.gpstools.domain.gps.PoiType
import org.devshred.gpstools.domain.gps.Track
import org.devshred.gpstools.domain.gps.WayPoint
import org.devshred.gpstools.domain.tcx.TcxTools.XML_MAPPER
import org.hamcrest.MatcherAssert.assertThat
import org.junit.jupiter.api.Test
import org.xmlunit.diff.DefaultNodeMatcher
import org.xmlunit.diff.ElementSelectors
import org.xmlunit.matchers.CompareMatcher
import java.time.Instant

class TcxToolsTest {
    @Test
    fun `test createTcxFromGpsContainer`() {
        val gpsContainer =
            GpsContainer(
                name = "Happy Path Example",
                wayPoints =
                    listOf(
                        WayPoint(
                            latitude = 36.74881700,
                            longitude = -4.07262399,
                            time = Instant.ofEpochSecond(1262315764),
                            type = PoiType.FOOD,
                            name = "Highlight",
                        ),
                    ),
                track =
                    Track(
                        wayPoints =
                            listOf(
                                WayPoint(
                                    latitude = 36.72100500,
                                    longitude = -4.41088200,
                                    elevation = 14.000000,
                                    time = Instant.ofEpochSecond(1262304000),
                                ),
                                WayPoint(
                                    latitude = 36.74881700,
                                    longitude = -4.07262399,
                                    elevation = 3.0000000,
                                    time = Instant.ofEpochSecond(1262315764),
                                ),
                                WayPoint(
                                    latitude = 36.73361890,
                                    longitude = -3.68807099,
                                    elevation = 11.000000,
                                    time = Instant.ofEpochSecond(1262332771),
                                ),
                            ),
                    ),
            )

        val tcx = createTcxFromGpsContainer(gpsContainer)

        val expected = this::class.java.classLoader.getResource("data/full.tcx").readText()
        val actual = XML_MAPPER.writeValueAsString(tcx)

        assertThat(
            actual,
            CompareMatcher.isSimilarTo(expected)
                .withNodeMatcher(DefaultNodeMatcher(ElementSelectors.byName))
                .ignoreWhitespace(),
        )
    }
}
