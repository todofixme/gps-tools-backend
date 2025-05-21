package org.devshred.gpstools.domain.gps

import io.jenetics.jpx.GPX
import mil.nga.sf.geojson.FeatureCollection
import mil.nga.sf.geojson.Point
import mil.nga.sf.geojson.Position
import org.apache.commons.lang3.RandomStringUtils
import org.apache.commons.math3.random.RandomDataGenerator
import org.assertj.core.api.Assertions.assertThat
import org.devshred.gpstools.formats.gps.ExtensionValues
import org.devshred.gpstools.formats.gps.GpsContainer
import org.devshred.gpstools.formats.gps.GpsContainerMapper
import org.devshred.gpstools.formats.gps.PoiType
import org.devshred.gpstools.formats.gps.PointOfInterest
import org.devshred.gpstools.formats.gps.Track
import org.devshred.gpstools.formats.gps.TrackPoint
import org.devshred.gpstools.formats.gps.toGps
import org.devshred.gpstools.formats.gps.toGpsPointOfInterest
import org.devshred.gpstools.formats.gps.toGpsTrackPoint
import org.devshred.gpstools.formats.gps.toGpx
import org.devshred.gpstools.formats.gps.toProto
import org.devshred.gpstools.formats.proto.protoContainer
import org.devshred.gpstools.formats.proto.protoPointOfInterest
import org.devshred.gpstools.formats.proto.protoTrack
import org.devshred.gpstools.formats.proto.protoTrackPoint
import org.devshred.gpstools.formats.tcx.TcxTools
import org.hamcrest.MatcherAssert.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.xmlunit.diff.DefaultNodeMatcher
import org.xmlunit.diff.ElementSelectors
import org.xmlunit.matchers.CompareMatcher
import java.time.Instant
import java.util.UUID
import java.util.stream.Stream
import io.jenetics.jpx.WayPoint as GpxWayPoint
import org.xmlunit.assertj.XmlAssert.assertThat as xmlAssertThat

class GpsContainerMapperTest {
    private val randomGenerator = RandomDataGenerator().randomGenerator
    private val mapper: GpsContainerMapper = GpsContainerMapper()

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
    fun `convert from proto to GpsContainer`() {
        val protoGpsContainer: org.devshred.gpstools.formats.proto.ProtoContainer =
            protoContainer {
                name = "My Track"
                pointsOfInterest +=
                    listOf(
                        protoPointOfInterest {
                            uuid = UUID.randomUUID().toString()
                            latitude = 1.0
                            longitude = 2.0
                            type = org.devshred.gpstools.formats.proto.ProtoPoiType.SUMMIT
                        },
                    )
                track =
                    protoTrack {
                        trackPoints +=
                            protoTrackPoint {
                                latitude = 1.0
                                longitude = 2.0
                            }
                    }
            }

        val domainGpsContainer = mapper.fromProto(protoGpsContainer)

        assertThat(domainGpsContainer.name).isEqualTo("My Track")
        assertThat(domainGpsContainer.pointsOfInterest).hasSize(1)
        assertThat(domainGpsContainer.pointsOfInterest[0].type).isEqualTo(PoiType.SUMMIT)
        assertThat(domainGpsContainer.track!!.trackPoints).hasSize(1)
    }

    @Test
    fun `set trackname from first track`() {
        val trackname = RandomStringUtils.insecure().nextAlphabetic(8)
        val gpx =
            GPX
                .builder()
                .metadata { m -> m.name("yet another name") }
                .addTrack { track ->
                    run {
                        track.name(trackname)
                        track.addSegment { s -> s.addPoint(randomWayPoint()).build() }
                    }
                }.build()

        val gpsContainer = mapper.fromGpx(gpx)

        assertThat(gpsContainer.name).isEqualTo(trackname)
    }

    @Test
    fun `set trackname from GPX metadata if no track was found`() {
        val trackname = RandomStringUtils.insecure().nextAlphabetic(8)
        val gpx =
            GPX
                .builder()
                .metadata { m -> m.name(trackname) }
                .build()

        val gpsContainer = mapper.fromGpx(gpx)

        assertThat(gpsContainer.name).isEqualTo(trackname)
    }

    @Test
    fun `skip setting trackname if neither track nor metadata was found`() {
        val gpx = GPX.builder().build()

        val gpsContainer = mapper.fromGpx(gpx)

        assertThat(gpsContainer.name).isNull()
    }

    @Test
    fun `map protoBuf TrackPoint`() {
        val lat = randomGenerator.nextDouble()
        val lon = randomGenerator.nextDouble()
        val protoBuf =
            protoTrackPoint {
                latitude = lat
                longitude = lon
            }

        val gpx = protoBuf.toGps()

        assertThat(gpx.latitude).isEqualTo(lat)
        assertThat(gpx.longitude).isEqualTo(lon)
    }

    @Test
    fun `map protoBuf PointOfInterest`() {
        val lat = randomGenerator.nextDouble()
        val lon = randomGenerator.nextDouble()
        val protoBuf =
            protoPointOfInterest {
                uuid = UUID.randomUUID().toString()
                latitude = lat
                longitude = lon
            }

        val gpx = protoBuf.toGps()

        assertThat(gpx.latitude).isEqualTo(lat)
        assertThat(gpx.longitude).isEqualTo(lon)
    }

    @ParameterizedTest(name = "{0} should convert to {1}")
    @MethodSource("protoToGps")
    fun `convert PoiType to ProtoPoiType`(
        poiType: PoiType,
        protoPoiType: org.devshred.gpstools.formats.proto.ProtoPoiType,
    ) {
        assertThat(poiType.toProto()).isEqualTo(protoPoiType)
    }

    companion object {
        @JvmStatic
        private fun protoToGps(): Stream<Arguments> =
            Stream.of(
                Arguments.of(PoiType.GENERIC, org.devshred.gpstools.formats.proto.ProtoPoiType.GENERIC),
                Arguments.of(PoiType.SUMMIT, org.devshred.gpstools.formats.proto.ProtoPoiType.SUMMIT),
                Arguments.of(PoiType.VALLEY, org.devshred.gpstools.formats.proto.ProtoPoiType.VALLEY),
                Arguments.of(PoiType.WATER, org.devshred.gpstools.formats.proto.ProtoPoiType.WATER),
                Arguments.of(PoiType.FOOD, org.devshred.gpstools.formats.proto.ProtoPoiType.FOOD),
                Arguments.of(PoiType.DANGER, org.devshred.gpstools.formats.proto.ProtoPoiType.DANGER),
                Arguments.of(PoiType.LEFT, org.devshred.gpstools.formats.proto.ProtoPoiType.LEFT),
                Arguments.of(PoiType.RIGHT, org.devshred.gpstools.formats.proto.ProtoPoiType.RIGHT),
                Arguments.of(PoiType.STRAIGHT, org.devshred.gpstools.formats.proto.ProtoPoiType.STRAIGHT),
                Arguments.of(PoiType.FIRST_AID, org.devshred.gpstools.formats.proto.ProtoPoiType.FIRST_AID),
                Arguments.of(PoiType.FOURTH_CATEGORY, org.devshred.gpstools.formats.proto.ProtoPoiType.FOURTH_CATEGORY),
                Arguments.of(PoiType.THIRD_CATEGORY, org.devshred.gpstools.formats.proto.ProtoPoiType.THIRD_CATEGORY),
                Arguments.of(PoiType.SECOND_CATEGORY, org.devshred.gpstools.formats.proto.ProtoPoiType.SECOND_CATEGORY),
                Arguments.of(PoiType.FIRST_AID, org.devshred.gpstools.formats.proto.ProtoPoiType.FIRST_AID),
                Arguments.of(PoiType.HORS_CATEGORY, org.devshred.gpstools.formats.proto.ProtoPoiType.HORS_CATEGORY),
                Arguments.of(PoiType.RESIDENCE, org.devshred.gpstools.formats.proto.ProtoPoiType.RESIDENCE),
                Arguments.of(PoiType.SPRINT, org.devshred.gpstools.formats.proto.ProtoPoiType.SPRINT),
            )
    }

    @Test
    fun `union ExtensionValues (B overrides A)`() {
        val valuesA = ExtensionValues(1, 2, null, 4)
        val valuesB = ExtensionValues(null, null, 7, 8)
        val expected = ExtensionValues(1, 2, 7, 8)

        val actual = valuesA.union(valuesB)

        assertThat(actual).isEqualTo(expected)
    }

    @Test
    fun `convert to TCX`() {
        val expectedTcx =
            this::class.java.classLoader
                .getResource("data/full.tcx")
                .readText()

        val tcx = mapper.toTcx(gpsContainer)

        val actualTcx = TcxTools.XML_MAPPER.writeValueAsString(tcx)

        assertThat(
            actualTcx,
            CompareMatcher
                .isSimilarTo(expectedTcx)
                .withNodeMatcher(DefaultNodeMatcher(ElementSelectors.byName))
                .ignoreWhitespace(),
        )
    }

    @Test
    fun `convert to TCX even if trackPoints does not contain time`() {
        val tcx =
            mapper.toTcx(
                gpsContainer.copy(
                    pointsOfInterest =
                        listOf(
                            PointOfInterest(
                                uuid = UUID.randomUUID(),
                                latitude = 36.74881700,
                                longitude = -4.07262399,
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
                                    ),
                                    TrackPoint(
                                        latitude = 36.74881700,
                                        longitude = -4.07262399,
                                        elevation = 3.0000000,
                                    ),
                                ),
                        ),
                ),
            )

        val actualTcx = TcxTools.XML_MAPPER.writeValueAsString(tcx)

        val tcxNs = mapOf("tcx" to "http://www.garmin.com/xmlschemas/TrainingCenterDatabase/v2")

        xmlAssertThat(actualTcx)
            .withNamespaceContext(tcxNs)
            .valueByXPath("//tcx:TrainingCenterDatabase/tcx:Courses/tcx:Course/tcx:Lap/tcx:TotalTimeSeconds/text()")
            .isEqualTo("0.0")
    }

    @Test
    fun `maps to GeoJSON`() {
        val trackPoints =
            listOf(
                TrackPoint(11.0, 12.0),
                TrackPoint(21.0, 22.0),
                TrackPoint(31.0, 32.0),
                TrackPoint(41.0, 42.0),
                TrackPoint(51.0, 52.0),
            )
        val pointsOfInterest =
            listOf(
                PointOfInterest(UUID.randomUUID(), 11.0, 12.0, type = PoiType.RESIDENCE),
                PointOfInterest(UUID.randomUUID(), 31.0, 32.0, type = PoiType.FOOD),
                PointOfInterest(UUID.randomUUID(), 51.0, 52.0, type = PoiType.SPRINT),
            )
        val track = Track(trackPoints)
        val gpsContainer = GpsContainer("TestTrack", pointsOfInterest, track)

        val actual: FeatureCollection = mapper.toGeoJson(gpsContainer)

        // 1 track and 3 wayPoints
        assertThat(actual.features).hasSize(4)

        // the track (of type LineString)
        assertThat(actual.features.filter { it.geometry.type == "LineString" }).hasSize(1)
        assertThat(
            actual.features
                .filter { it.geometry.type == "LineString" }
                .flatMap { f -> (f.geometry as mil.nga.sf.geojson.LineString).coordinates },
        ).containsExactly(
            Position(12.0, 11.0),
            Position(22.0, 21.0),
            Position(32.0, 31.0),
            Position(42.0, 41.0),
            Position(52.0, 51.0),
        )

        // the wayPoints (of type Point)
        assertThat(actual.features.filter { it.geometry.type == "Point" }).hasSize(3)
        assertThat(
            actual.features
                .filter { it.geometry.type == "Point" }
                .filter { it.properties["type"] == "FOOD" }
                .map { (it.geometry as Point).point },
        ).isEqualTo(listOf(mil.nga.sf.Point(31.0, 32.0)))
    }

    @Test
    fun `maps waypoints only to GeoJSON`() {
        val pointsOfInterest =
            listOf(
                PointOfInterest(UUID.randomUUID(), 11.0, 12.0, type = PoiType.RESIDENCE),
                PointOfInterest(UUID.randomUUID(), 31.0, 32.0, type = PoiType.FOOD),
                PointOfInterest(UUID.randomUUID(), 51.0, 52.0, type = PoiType.SPRINT),
            )
        val gpsContainer = GpsContainer("TestTrack", pointsOfInterest, null)

        val actual: FeatureCollection = mapper.toGeoJson(gpsContainer)

        // 0 track and 3 wayPoints
        assertThat(actual.features).hasSize(3)

        // the wayPoints (of type Point)
        assertThat(actual.features.filter { it.geometry.type == "Point" }).hasSize(3)
    }

    @Test
    fun `convert TrackPoint to GpxWayPoint and back to TrackPoint`() {
        val trackPoint =
            TrackPoint(
                latitude = 36.72100500,
                longitude = -4.41088200,
            )

        val gpx = trackPoint.toGpx()

        assertThat(gpx.latitude.toDouble()).isEqualTo(36.72100500)
        assertThat(gpx.longitude.toDouble()).isEqualTo(-4.41088200)

        val gps = gpx.toGpsTrackPoint()

        assertThat(gps.latitude).isEqualTo(36.72100500)
        assertThat(gps.longitude).isEqualTo(-4.41088200)
    }

    @Test
    fun `convert TrackPoint to GpxWayPoint and back to TrackPoint with elevation`() {
        val trackPoint =
            TrackPoint(
                latitude = 36.72100500,
                longitude = -4.41088200,
                elevation = 14.000000,
            )

        val gpx = trackPoint.toGpx()

        assertThat(gpx.latitude.toDouble()).isEqualTo(36.72100500)
        assertThat(gpx.longitude.toDouble()).isEqualTo(-4.41088200)
        assertThat(gpx.elevation.get().toDouble()).isEqualTo(14.000000)

        val gps = gpx.toGpsTrackPoint()

        assertThat(gps.latitude).isEqualTo(36.72100500)
        assertThat(gps.longitude).isEqualTo(-4.41088200)
        assertThat(gps.elevation).isEqualTo(14.000000)
    }

    @Test
    fun `convert TrackPoint to GpxWayPoint and back to TrackPoint with time`() {
        val trackPoint =
            TrackPoint(
                latitude = 36.72100500,
                longitude = -4.41088200,
                time = Instant.ofEpochSecond(1262304000),
            )

        val gpx = trackPoint.toGpx()

        assertThat(gpx.latitude.toDouble()).isEqualTo(36.72100500)
        assertThat(gpx.longitude.toDouble()).isEqualTo(-4.41088200)
        assertThat(gpx.time.get().epochSecond).isEqualTo(1262304000)

        val gps = gpx.toGpsTrackPoint()

        assertThat(gps.latitude).isEqualTo(36.72100500)
        assertThat(gps.longitude).isEqualTo(-4.41088200)
        assertThat(gps.time?.epochSecond).isEqualTo(1262304000)
    }

    @Test
    fun `convert TrackPoint to GpxWayPoint and back to TrackPoint with heartrate`() {
        val trackPoint =
            TrackPoint(
                latitude = 36.72100500,
                longitude = -4.41088200,
                heartRate = 180,
            )

        val gpx = trackPoint.toGpx()
        val gps = gpx.toGpsTrackPoint()

        assertThat(gps.latitude).isEqualTo(36.72100500)
        assertThat(gps.longitude).isEqualTo(-4.41088200)
        assertThat(gps.heartRate).isEqualTo(180)
    }

    @Test
    fun `convert TrackPoint to GpxWayPoint and back to TrackPoint with cadence`() {
        val trackPoint =
            TrackPoint(
                latitude = 36.72100500,
                longitude = -4.41088200,
                cadence = 90,
            )

        val gpx = trackPoint.toGpx()
        val gps = gpx.toGpsTrackPoint()

        assertThat(gps.latitude).isEqualTo(36.72100500)
        assertThat(gps.longitude).isEqualTo(-4.41088200)
        assertThat(gps.cadence).isEqualTo(90)
    }

    @Test
    fun `convert TrackPoint to GpxWayPoint and back to TrackPoint with power`() {
        val trackPoint =
            TrackPoint(
                latitude = 36.72100500,
                longitude = -4.41088200,
                power = 420,
            )

        val gpx = trackPoint.toGpx()
        val gps = gpx.toGpsTrackPoint()

        assertThat(gps.latitude).isEqualTo(36.72100500)
        assertThat(gps.longitude).isEqualTo(-4.41088200)
        assertThat(gps.power).isEqualTo(420)
    }

    @Test
    fun `convert PointOfInterest to GpxWayPoint and back to PointOfInterest`() {
        val uuid = UUID.randomUUID()
        val poi =
            PointOfInterest(
                uuid = uuid,
                latitude = 36.72100500,
                longitude = -4.41088200,
            )

        val gpx = poi.toGpx()

        assertThat(gpx.latitude.toDouble()).isEqualTo(36.72100500)
        assertThat(gpx.longitude.toDouble()).isEqualTo(-4.41088200)

        val gps = gpx.toGpsPointOfInterest()

        assertThat(gps.latitude).isEqualTo(36.72100500)
        assertThat(gps.longitude).isEqualTo(-4.41088200)
    }

    @Test
    fun `convert PointOfInterest to GpxWayPoint and back to PointOfInterest with name`() {
        val uuid = UUID.randomUUID()
        val name = RandomStringUtils.insecure().nextAlphabetic(8)
        val poi =
            PointOfInterest(
                uuid = uuid,
                latitude = 36.72100500,
                longitude = -4.41088200,
                name = name,
            )

        val gpx = poi.toGpx()

        assertThat(gpx.latitude.toDouble()).isEqualTo(36.72100500)
        assertThat(gpx.longitude.toDouble()).isEqualTo(-4.41088200)
        assertThat(gpx.name.get()).isEqualTo(name)

        val gps = gpx.toGpsPointOfInterest()

        assertThat(gps.latitude).isEqualTo(36.72100500)
        assertThat(gps.longitude).isEqualTo(-4.41088200)
        assertThat(gps.name).isEqualTo(name)
    }

    @Test
    fun `convert PointOfInterest to GpxWayPoint and back to PointOfInterest with type`() {
        val uuid = UUID.randomUUID()
        val type = PoiType.RESIDENCE
        val poi =
            PointOfInterest(
                uuid = uuid,
                latitude = 36.72100500,
                longitude = -4.41088200,
                type = type,
            )

        val gpx = poi.toGpx()

        assertThat(gpx.latitude.toDouble()).isEqualTo(36.72100500)
        assertThat(gpx.longitude.toDouble()).isEqualTo(-4.41088200)
        assertThat(gpx.type.get()).isEqualTo("RESIDENCE")

        val gps = gpx.toGpsPointOfInterest()

        assertThat(gps.latitude).isEqualTo(36.72100500)
        assertThat(gps.longitude).isEqualTo(-4.41088200)
        assertThat(gps.type).isEqualTo(PoiType.RESIDENCE)
    }

    @Test
    fun `optimize point filters out waypoint, since distance higher than tolerance`() {
        val basePoi =
            PointOfInterest(
                uuid = UUID.randomUUID(),
                latitude = 36.73,
                longitude = -3.68,
                type = PoiType.FOOD,
                name = "Point 1",
            )
        val cut = gpsContainer.copy(pointsOfInterest = listOf(basePoi))

        val result = cut.withOptimizedPointsOfInterest()

        assertThat(result.pointsOfInterest).isEmpty()
    }

    @Test
    fun `optimize point of interest succeeds`() {
        val basePoi =
            PointOfInterest(
                uuid = UUID.randomUUID(),
                latitude = 36.733,
                longitude = -3.688,
                type = PoiType.FOOD,
                name = "Point 1",
            )
        val cut = gpsContainer.copy(pointsOfInterest = listOf(basePoi))

        val result = cut.withOptimizedPointsOfInterest()

        assertThat(result.pointsOfInterest).hasSize(1)
        assertThat(result.pointsOfInterest[0].latitude).isEqualTo(gpsContainer.track!!.trackPoints[2].latitude)
        assertThat(result.pointsOfInterest[0].longitude).isEqualTo(gpsContainer.track!!.trackPoints[2].longitude)
    }

    @Test
    fun `optimize point of interest without time succeeds`() {
        val basePoi =
            PointOfInterest(
                uuid = UUID.randomUUID(),
                latitude = 36.733,
                longitude = -3.688,
                type = PoiType.FOOD,
                name = "Point 1",
            )
        val cut =
            gpsContainer.copy(
                pointsOfInterest = listOf(basePoi),
                // remove timestamps from trackPoints
                track = gpsContainer.track!!.copy(trackPoints = gpsContainer.track!!.trackPoints.map { it.copy(time = null) }),
            )

        val result = cut.withOptimizedPointsOfInterest()

        assertThat(result.pointsOfInterest).hasSize(1)
        assertThat(result.pointsOfInterest[0].latitude).isEqualTo(gpsContainer.track!!.trackPoints[2].latitude)
        assertThat(result.pointsOfInterest[0].longitude).isEqualTo(gpsContainer.track!!.trackPoints[2].longitude)
    }

    @Test
    fun `maps point to GeoJSON`() {
        val poi =
            PointOfInterest(
                uuid = UUID.randomUUID(),
                latitude = 36.733,
                longitude = -3.688,
                type = PoiType.FOOD,
                name = "Point 1",
            )

        val result = mapper.toGeoJsonPoints(listOf(poi))

        assertThat(result).hasSize(1)
        val point = result.first()
        assertThat(point.geometry).isInstanceOf(Point::class.java)
        assertThat((point.geometry as Point).position.x).isEqualTo(36.733)
        assertThat((point.geometry as Point).position.y).isEqualTo(-3.688)
        assertThat(point.properties["type"]).isEqualTo("FOOD")
        assertThat(point.properties["name"]).isEqualTo("Point 1")
    }

    private fun randomWayPoint(): GpxWayPoint =
        GpxWayPoint
            .builder()
            .lat(randomGenerator.nextDouble())
            .lon(randomGenerator.nextDouble())
            .build()
}
