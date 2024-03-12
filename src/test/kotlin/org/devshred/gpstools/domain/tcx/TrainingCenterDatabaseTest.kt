package org.devshred.gpstools.domain.tcx

import org.devshred.gpstools.domain.tcx.TcxTools.XML_MAPPER
import org.hamcrest.MatcherAssert.assertThat
import org.junit.jupiter.api.Test
import org.xmlunit.diff.DefaultNodeMatcher
import org.xmlunit.diff.ElementSelectors
import org.xmlunit.matchers.CompareMatcher
import java.time.LocalDateTime
import java.time.ZoneOffset

class TrainingCenterDatabaseTest {
    @Test
    fun `testing a full TCX file`() {
        val tcx =
            TrainingCenterDatabase(
                course =
                    mutableListOf(
                        Course(
                            name = "Happy Path Example",
                            lap =
                                Lap(
                                    totalTimeSeconds = 28771.0,
                                    distanceMeters = 79920.05133152752,
                                    beginPosition =
                                        Position(
                                            latitudeDegrees = 36.72100500,
                                            longitudeDegrees = -4.41088200,
                                        ),
                                    endPosition =
                                        Position(
                                            latitudeDegrees = 36.73361890,
                                            longitudeDegrees = -3.68807099,
                                        ),
                                    intensity = "Active",
                                ),
                            track =
                                Track(
                                    trackpoints =
                                        mutableListOf(
                                            Trackpoint(
                                                time = LocalDateTime.of(2010, 1, 1, 0, 0, 0).atZone(ZoneOffset.UTC),
                                                position =
                                                    Position(
                                                        latitudeDegrees = 36.72100500,
                                                        longitudeDegrees = -4.41088200,
                                                    ),
                                                altitudeMeters = 14.000000,
                                                distanceMeters = 0.000,
                                            ),
                                            Trackpoint(
                                                time = LocalDateTime.of(2010, 1, 1, 3, 16, 4).atZone(ZoneOffset.UTC),
                                                position =
                                                    Position(
                                                        latitudeDegrees = 36.74881700,
                                                        longitudeDegrees = -4.07262399,
                                                    ),
                                                altitudeMeters = 3.0000000,
                                                distanceMeters = 32678.529,
                                            ),
                                            Trackpoint(
                                                time = LocalDateTime.of(2010, 1, 1, 7, 59, 31).atZone(ZoneOffset.UTC),
                                                position =
                                                    Position(
                                                        latitudeDegrees = 36.73361890,
                                                        longitudeDegrees = -3.68807099,
                                                    ),
                                                altitudeMeters = 11.000000,
                                                distanceMeters = 79920.051,
                                            ),
                                        ),
                                ),
                            coursePoints =
                                mutableListOf(
                                    CoursePoint(
                                        name = "Highlight",
                                        time = LocalDateTime.of(2010, 1, 1, 3, 16, 4).atZone(ZoneOffset.UTC),
                                        position =
                                            Position(
                                                latitudeDegrees = 36.74881700,
                                                longitudeDegrees = -4.07262399,
                                            ),
                                        pointType = "Food",
                                    ),
                                ),
                        ),
                    ),
            )

        val expected = this::class.java.classLoader.getResource("data/test.tcx")?.readText()
        val actual = XML_MAPPER.writeValueAsString(tcx)

        assertThat(
            actual,
            CompareMatcher.isSimilarTo(expected)
                .withNodeMatcher(DefaultNodeMatcher(ElementSelectors.byName))
                .ignoreWhitespace(),
        )
    }
}
