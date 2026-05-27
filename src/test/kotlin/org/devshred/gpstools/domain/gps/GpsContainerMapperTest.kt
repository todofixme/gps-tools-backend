package org.devshred.gpstools.domain.gps

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.garmin.xmlschemas.trainingcenterdatabase.v2.CoursePointT
import com.garmin.xmlschemas.trainingcenterdatabase.v2.PositionT
import io.jenetics.jpx.Copyright
import io.jenetics.jpx.GPX
import io.jenetics.jpx.Link
import mil.nga.sf.geojson.FeatureCollection
import mil.nga.sf.geojson.Point
import mil.nga.sf.geojson.Position
import org.apache.commons.lang3.RandomStringUtils
import org.apache.commons.math3.random.RandomDataGenerator
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.devshred.gpstools.api.model.FeatureCollectionDTO
import org.devshred.gpstools.api.model.FeatureDTO
import org.devshred.gpstools.api.model.LineStringDTO
import org.devshred.gpstools.api.model.PointDTO
import org.devshred.gpstools.formats.gps.ExtensionValues
import org.devshred.gpstools.formats.gps.GpsContainer
import org.devshred.gpstools.formats.gps.GpsContainerMapper
import org.devshred.gpstools.formats.gps.GpsMetadata
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
import java.io.File
import java.math.BigDecimal
import java.nio.file.Path
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
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
    fun `convert to TCX truncates POI name to 10 characters`() {
        val tcx =
            mapper.toTcx(
                gpsContainer.copy(
                    pointsOfInterest =
                        listOf(
                            PointOfInterest(
                                uuid = UUID.randomUUID(),
                                latitude = 36.74881700,
                                longitude = -4.07262399,
                                name = "This name is way too long for TCX",
                            ),
                        ),
                ),
            )

        val actualTcx = TcxTools.XML_MAPPER.writeValueAsString(tcx)
        val tcxNs = mapOf("tcx" to "http://www.garmin.com/xmlschemas/TrainingCenterDatabase/v2")

        xmlAssertThat(actualTcx)
            .withNamespaceContext(tcxNs)
            .valueByXPath("//tcx:TrainingCenterDatabase/tcx:Courses/tcx:Course/tcx:CoursePoint/tcx:Name/text()")
            .isEqualTo("This name ")
    }

    @Test
    fun `convert to TCX uses Generic for PoiTypes not defined in TCX spec`() {
        val tcx =
            mapper.toTcx(
                gpsContainer.copy(
                    pointsOfInterest =
                        listOf(
                            PointOfInterest(
                                uuid = UUID.randomUUID(),
                                latitude = 36.74881700,
                                longitude = -4.07262399,
                                type = PoiType.RESIDENCE,
                                name = "Home",
                            ),
                        ),
                ),
            )

        val actualTcx = TcxTools.XML_MAPPER.writeValueAsString(tcx)
        val tcxNs = mapOf("tcx" to "http://www.garmin.com/xmlschemas/TrainingCenterDatabase/v2")

        xmlAssertThat(actualTcx)
            .withNamespaceContext(tcxNs)
            .valueByXPath("//tcx:TrainingCenterDatabase/tcx:Courses/tcx:Course/tcx:CoursePoint/tcx:PointType/text()")
            .isEqualTo("Generic")
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
        ).isEqualTo(listOf(mil.nga.sf.Point(32.0, 31.0)))
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
        assertThat(result.pointsOfInterest[0].longitude).isEqualTo(gpsContainer.track.trackPoints[2].longitude)
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
                track = gpsContainer.track!!.copy(trackPoints = gpsContainer.track.trackPoints.map { it.copy(time = null) }),
            )

        val result = cut.withOptimizedPointsOfInterest()

        assertThat(result.pointsOfInterest).hasSize(1)
        assertThat(result.pointsOfInterest[0].latitude).isEqualTo(gpsContainer.track.trackPoints[2].latitude)
        assertThat(result.pointsOfInterest[0].longitude).isEqualTo(gpsContainer.track.trackPoints[2].longitude)
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
        assertThat((point.geometry as Point).position.x).isEqualTo(-3.688)
        assertThat((point.geometry as Point).position.y).isEqualTo(36.733)
        assertThat(point.properties["type"]).isEqualTo("FOOD")
        assertThat(point.properties["name"]).isEqualTo("Point 1")
    }

    @Test
    fun `handle null date`() {
        val coursePointT =
            CoursePointT().apply {
                time = null
                name = "some point"
                position =
                    PositionT().apply {
                        latitudeDegrees = 12.34
                        longitudeDegrees = 67.89
                    }
                altitudeMeters = 1300.2
                pointType = "First Aid"
            }

        val result = coursePointT.toGpsPointOfInterest()

        assertThat(result.time).isNull()

        assertThat(result.name).isEqualTo("some point")
        assertThat(result.latitude).isEqualTo(12.34)
        assertThat(result.longitude).isEqualTo(67.89)
        assertThat(result.elevation).isEqualTo(1300.2)
        assertThat(result.type).isEqualTo(PoiType.FIRST_AID)
    }

    @Test
    fun `should read description from GPX metadata`() {
        val gpx =
            GPX
                .builder()
                .metadata { m -> m.desc("My Description") }
                .build()

        val result = mapper.fromGpx(gpx)

        assertThat(result.metadata?.description).isEqualTo("My Description")
    }

    @Test
    fun `should read copyright from GPX metadata`() {
        val gpx =
            GPX
                .builder()
                .metadata { m -> m.copyright(Copyright.of("Johannes Schmidt", 2024)) }
                .build()

        val result = mapper.fromGpx(gpx)

        assertThat(result.metadata?.copyrightAuthor).isEqualTo("Johannes Schmidt")
        assertThat(result.metadata?.copyrightYear).isEqualTo(2024)
    }

    @Test
    fun `should read link href from GPX metadata`() {
        val gpx =
            GPX
                .builder()
                .metadata { m -> m.addLink(Link.of("https://example.com", "Example", null)) }
                .build()

        val result = mapper.fromGpx(gpx)

        assertThat(result.metadata?.linkHref).isEqualTo("https://example.com")
    }

    @Test
    fun `should return null metadata when GPX has no metadata fields`() {
        val gpx = GPX.builder().build()

        val result = mapper.fromGpx(gpx)

        assertThat(result.metadata).isNull()
    }

    @Test
    fun `should write description to GPX metadata`() {
        val container =
            GpsContainer(
                name = "My Track",
                metadata = GpsMetadata(description = "My Description"),
                pointsOfInterest = emptyList(),
                track = null,
            )

        val gpx = mapper.toGpx(container)

        assertThat(
            gpx.metadata
                .get()
                .description
                .get(),
        ).isEqualTo("My Description")
    }

    @Test
    fun `should write copyright to GPX metadata`() {
        val container =
            GpsContainer(
                name = "My Track",
                metadata = GpsMetadata(copyrightAuthor = "Johannes Schmidt", copyrightYear = 2024),
                pointsOfInterest = emptyList(),
                track = null,
            )

        val gpx = mapper.toGpx(container)

        assertThat(
            gpx.metadata
                .get()
                .copyright
                .get()
                .author,
        ).isEqualTo("Johannes Schmidt")
        assertThat(
            gpx.metadata
                .get()
                .copyright
                .get()
                .year
                .get()
                .value,
        ).isEqualTo(2024)
    }

    @Test
    fun `should write link to GPX metadata with text combining desc and name`() {
        val container =
            GpsContainer(
                name = "My Track",
                metadata = GpsMetadata(description = "My Description", linkHref = "https://example.com"),
                pointsOfInterest = emptyList(),
                track = null,
            )

        val gpx = mapper.toGpx(container)

        assertThat(gpx.metadata.get().links).hasSize(1)
        assertThat(
            gpx.metadata
                .get()
                .links[0]
                .href
                .toString(),
        ).isEqualTo("https://example.com")
        assertThat(
            gpx.metadata
                .get()
                .links[0]
                .text
                .get(),
        ).isEqualTo("My Description; My Track")
    }

    @Test
    fun `should round-trip metadata through proto`() {
        val container =
            GpsContainer(
                name = "My Track",
                metadata =
                    GpsMetadata(
                        description = "My Description",
                        copyrightAuthor = "Johannes Schmidt",
                        copyrightYear = 2024,
                        linkHref = "https://example.com",
                    ),
                pointsOfInterest = emptyList(),
                track = null,
            )

        val proto = mapper.toProto(container)
        val result = mapper.fromProto(proto)

        assertThat(result.metadata?.description).isEqualTo("My Description")
        assertThat(result.metadata?.copyrightAuthor).isEqualTo("Johannes Schmidt")
        assertThat(result.metadata?.copyrightYear).isEqualTo(2024)
        assertThat(result.metadata?.linkHref).isEqualTo("https://example.com")
    }

    @Test
    fun `should not write copyright when only copyrightYear is set without author`() {
        val container =
            GpsContainer(
                name = "My Track",
                metadata = GpsMetadata(copyrightYear = 2024),
                pointsOfInterest = emptyList(),
                track = null,
            )

        val gpx = mapper.toGpx(container)

        assertThat(gpx.metadata.get().copyright).isEmpty
    }

    @Test
    fun `should write copyright with only author when year is absent`() {
        val container =
            GpsContainer(
                name = "My Track",
                metadata = GpsMetadata(copyrightAuthor = "Johannes Schmidt"),
                pointsOfInterest = emptyList(),
                track = null,
            )

        val gpx = mapper.toGpx(container)

        assertThat(
            gpx.metadata
                .get()
                .copyright
                .get()
                .author,
        ).isEqualTo("Johannes Schmidt")
        assertThat(
            gpx.metadata
                .get()
                .copyright
                .get()
                .year,
        ).isEmpty
    }

    @Test
    fun `should write link text with only name when description is absent`() {
        val container =
            GpsContainer(
                name = "My Track",
                metadata = GpsMetadata(linkHref = "https://example.com"),
                pointsOfInterest = emptyList(),
                track = null,
            )

        val gpx = mapper.toGpx(container)

        assertThat(
            gpx.metadata
                .get()
                .links[0]
                .text
                .get(),
        ).isEqualTo("My Track")
    }

    @Test
    fun `should write link to GPX track node`() {
        val container =
            GpsContainer(
                name = "My Track",
                metadata = GpsMetadata(description = "My Description", linkHref = "https://example.com"),
                pointsOfInterest = emptyList(),
                track = Track(listOf(TrackPoint(1.0, 2.0))),
            )

        val gpx = mapper.toGpx(container)

        assertThat(gpx.tracks[0].links).hasSize(1)
        assertThat(
            gpx.tracks[0]
                .links[0]
                .href
                .toString(),
        ).isEqualTo("https://example.com")
        assertThat(
            gpx.tracks[0]
                .links[0]
                .text
                .get(),
        ).isEqualTo("My Description; My Track")
    }

    @Test
    fun `should write link to GPX track node with only name when description is absent`() {
        val container =
            GpsContainer(
                name = "My Track",
                metadata = GpsMetadata(linkHref = "https://example.com"),
                pointsOfInterest = emptyList(),
                track = Track(listOf(TrackPoint(1.0, 2.0))),
            )

        val gpx = mapper.toGpx(container)

        assertThat(
            gpx.tracks[0]
                .links[0]
                .text
                .get(),
        ).isEqualTo("My Track")
    }

    @Test
    fun `should not write link to GPX track node when metadata has no link`() {
        val container =
            GpsContainer(
                name = "My Track",
                metadata = GpsMetadata(description = "My Description"),
                pointsOfInterest = emptyList(),
                track = Track(listOf(TrackPoint(1.0, 2.0))),
            )

        val gpx = mapper.toGpx(container)

        assertThat(gpx.tracks[0].links).isEmpty()
    }

    @Test
    fun `should read all metadata fields from GPX file`() {
        val gpx =
            GPX.read(
                Path.of("src/test/http/data/metadata.gpx"),
            )

        val result = mapper.fromGpx(gpx)

        assertThat(result.metadata?.description).isEqualTo("WfF ERT Savoyen-Tour 2024")
        assertThat(result.metadata?.copyrightAuthor).isEqualTo("Johannes Schmidt")
        assertThat(result.metadata?.copyrightYear).isEqualTo(2024)
        assertThat(result.metadata?.linkHref).isEqualTo("https://www.komoot.com/de-de/tour/1212348753")
    }

    @Test
    fun `should write desc to GeoJSON LineString properties`() {
        val container =
            GpsContainer(
                name = "My Track",
                metadata = GpsMetadata(description = "My Description"),
                pointsOfInterest = emptyList(),
                track = Track(listOf(TrackPoint(1.0, 2.0))),
            )

        val result = mapper.toGeoJson(container)

        val lineStringFeature = result.features.first { it.geometry.type == "LineString" }
        assertThat(lineStringFeature.properties["desc"]).isEqualTo("My Description")
    }

    @Test
    fun `should write copyright to GeoJSON LineString properties`() {
        val container =
            GpsContainer(
                name = "My Track",
                metadata = GpsMetadata(copyrightAuthor = "Johannes Schmidt", copyrightYear = 2026),
                pointsOfInterest = emptyList(),
                track = Track(listOf(TrackPoint(1.0, 2.0))),
            )

        val result = mapper.toGeoJson(container)

        val lineStringFeature = result.features.first { it.geometry.type == "LineString" }
        val copyright = lineStringFeature.properties["copyright"] as Map<*, *>
        assertThat(copyright["author"]).isEqualTo("Johannes Schmidt")
        assertThat(copyright["year"]).isEqualTo(2026)
    }

    @Test
    fun `should write copyright with only author to GeoJSON`() {
        val container =
            GpsContainer(
                name = "My Track",
                metadata = GpsMetadata(copyrightAuthor = "Johannes Schmidt"),
                pointsOfInterest = emptyList(),
                track = Track(listOf(TrackPoint(1.0, 2.0))),
            )

        val result = mapper.toGeoJson(container)

        val lineStringFeature = result.features.first { it.geometry.type == "LineString" }
        val copyright = lineStringFeature.properties["copyright"] as Map<*, *>
        assertThat(copyright["author"]).isEqualTo("Johannes Schmidt")
        assertThat(copyright["year"]).isNull()
    }

    @Test
    fun `should write copyright with only year to GeoJSON`() {
        val container =
            GpsContainer(
                name = "My Track",
                metadata = GpsMetadata(copyrightYear = 2026),
                pointsOfInterest = emptyList(),
                track = Track(listOf(TrackPoint(1.0, 2.0))),
            )

        val result = mapper.toGeoJson(container)

        val lineStringFeature = result.features.first { it.geometry.type == "LineString" }
        val copyright = lineStringFeature.properties["copyright"] as Map<*, *>
        assertThat(copyright["year"]).isEqualTo(2026)
        assertThat(copyright["author"]).isNull()
    }

    @Test
    fun `should write link to GeoJSON LineString properties`() {
        val container =
            GpsContainer(
                name = "My Track",
                metadata = GpsMetadata(linkHref = "https://example.com"),
                pointsOfInterest = emptyList(),
                track = Track(listOf(TrackPoint(1.0, 2.0))),
            )

        val result = mapper.toGeoJson(container)

        val lineStringFeature = result.features.first { it.geometry.type == "LineString" }
        assertThat(lineStringFeature.properties["link"]).isEqualTo("https://example.com")
    }

    @Test
    fun `should not write metadata properties when metadata is null`() {
        val container =
            GpsContainer(
                name = "My Track",
                metadata = null,
                pointsOfInterest = emptyList(),
                track = Track(listOf(TrackPoint(1.0, 2.0))),
            )

        val result = mapper.toGeoJson(container)

        val lineStringFeature = result.features.first { it.geometry.type == "LineString" }
        assertThat(lineStringFeature.properties).doesNotContainKey("desc")
        assertThat(lineStringFeature.properties).doesNotContainKey("copyright")
        assertThat(lineStringFeature.properties).doesNotContainKey("link")
    }

    @Test
    fun `should extract desc from GeoJSON properties`() {
        val featureCollectionDTO =
            FeatureCollectionDTO(
                features =
                    listOf(
                        FeatureDTO(
                            geometry =
                                LineStringDTO(
                                    coordinates = listOf(listOf(BigDecimal("10.0"), BigDecimal("53.0"))),
                                    type = "LineString",
                                ),
                            properties = mapOf("name" to "Track", "desc" to "My Description"),
                            type = "Feature",
                        ),
                    ),
                type = "FeatureCollection",
            )

        val result = mapper.fromGeoJson(featureCollectionDTO, "Fallback")

        assertThat(result.metadata?.description).isEqualTo("My Description")
    }

    @Test
    fun `should extract copyright from GeoJSON properties`() {
        val featureCollectionDTO =
            FeatureCollectionDTO(
                features =
                    listOf(
                        FeatureDTO(
                            geometry =
                                LineStringDTO(
                                    coordinates = listOf(listOf(BigDecimal("10.0"), BigDecimal("53.0"))),
                                    type = "LineString",
                                ),
                            properties = mapOf("copyright" to mapOf("author" to "Johannes Schmidt", "year" to 2026)),
                            type = "Feature",
                        ),
                    ),
                type = "FeatureCollection",
            )

        val result = mapper.fromGeoJson(featureCollectionDTO, "Fallback")

        assertThat(result.metadata?.copyrightAuthor).isEqualTo("Johannes Schmidt")
        assertThat(result.metadata?.copyrightYear).isEqualTo(2026)
    }

    @Test
    fun `should extract link from GeoJSON properties`() {
        val featureCollectionDTO =
            FeatureCollectionDTO(
                features =
                    listOf(
                        FeatureDTO(
                            geometry =
                                LineStringDTO(
                                    coordinates = listOf(listOf(BigDecimal("10.0"), BigDecimal("53.0"))),
                                    type = "LineString",
                                ),
                            properties = mapOf("link" to "https://www.komoot.com/tour/123"),
                            type = "Feature",
                        ),
                    ),
                type = "FeatureCollection",
            )

        val result = mapper.fromGeoJson(featureCollectionDTO, "Fallback")

        assertThat(result.metadata?.linkHref).isEqualTo("https://www.komoot.com/tour/123")
    }

    @Test
    fun `should extract full metadata from test_advanced json`() {
        val json = File("src/test/http/data/test_advanced.json").readText()
        val featureCollectionDTO =
            jacksonObjectMapper()
                .readValue(json, FeatureCollectionDTO::class.java)

        val result = mapper.fromGeoJson(featureCollectionDTO, "Fallback")

        assertThat(result.metadata?.description).isEqualTo("WfF ERT Savoyen-Tour 2024")
        assertThat(result.metadata?.copyrightAuthor).isEqualTo("Johannes Schmidt")
        assertThat(result.metadata?.copyrightYear).isEqualTo(2026)
        assertThat(result.metadata?.linkHref).isEqualTo("https://www.komoot.com/de-de/tour/1212348753")
    }

    @Test
    fun `should return null metadata when no metadata fields in GeoJSON`() {
        val featureCollectionDTO =
            FeatureCollectionDTO(
                features =
                    listOf(
                        FeatureDTO(
                            geometry =
                                LineStringDTO(
                                    coordinates = listOf(listOf(BigDecimal("10.0"), BigDecimal("53.0"))),
                                    type = "LineString",
                                ),
                            properties = mapOf("name" to "Track"),
                            type = "Feature",
                        ),
                    ),
                type = "FeatureCollection",
            )

        val result = mapper.fromGeoJson(featureCollectionDTO, "Fallback")

        assertThat(result.metadata).isNull()
    }

    @Test
    fun `should extract copyright with only author from GeoJSON properties`() {
        val featureCollectionDTO =
            FeatureCollectionDTO(
                features =
                    listOf(
                        FeatureDTO(
                            geometry =
                                LineStringDTO(
                                    coordinates = listOf(listOf(BigDecimal("10.0"), BigDecimal("53.0"))),
                                    type = "LineString",
                                ),
                            properties = mapOf("copyright" to mapOf("author" to "Johannes Schmidt")),
                            type = "Feature",
                        ),
                    ),
                type = "FeatureCollection",
            )

        val result = mapper.fromGeoJson(featureCollectionDTO, "Fallback")

        assertThat(result.metadata?.copyrightAuthor).isEqualTo("Johannes Schmidt")
        assertThat(result.metadata?.copyrightYear).isNull()
    }

    @Test
    fun `should extract copyright with only year from GeoJSON properties`() {
        val featureCollectionDTO =
            FeatureCollectionDTO(
                features =
                    listOf(
                        FeatureDTO(
                            geometry =
                                LineStringDTO(
                                    coordinates = listOf(listOf(BigDecimal("10.0"), BigDecimal("53.0"))),
                                    type = "LineString",
                                ),
                            properties = mapOf("copyright" to mapOf("year" to 2026)),
                            type = "Feature",
                        ),
                    ),
                type = "FeatureCollection",
            )

        val result = mapper.fromGeoJson(featureCollectionDTO, "Fallback")

        assertThat(result.metadata?.copyrightAuthor).isNull()
        assertThat(result.metadata?.copyrightYear).isEqualTo(2026)
    }

    private fun randomWayPoint(): GpxWayPoint =
        GpxWayPoint
            .builder()
            .lat(randomGenerator.nextDouble())
            .lon(randomGenerator.nextDouble())
            .build()

    @Test
    fun `should convert LineString feature to Track`() {
        val featureCollectionDTO =
            FeatureCollectionDTO(
                features =
                    listOf(
                        FeatureDTO(
                            geometry =
                                LineStringDTO(
                                    coordinates =
                                        listOf(
                                            listOf(BigDecimal("37.29994"), BigDecimal("-3.133811")),
                                            listOf(BigDecimal("37.176037"), BigDecimal("-3.596386")),
                                        ),
                                    type = "LineString",
                                ),
                            properties = mapOf("name" to "Test Track"),
                            type = "Feature",
                        ),
                    ),
                type = "FeatureCollection",
            )

        val result = mapper.fromGeoJson(featureCollectionDTO, "Fallback Name")

        assertThat(result.name).isEqualTo("Test Track")

        val track = checkNotNull(result.track) { "track must not be null" }
        assertThat(track.trackPoints).hasSize(2)
        assertThat(track.trackPoints[0].latitude).isEqualTo(-3.133811)
        assertThat(track.trackPoints[0].longitude).isEqualTo(37.29994)
        assertThat(track.trackPoints[1].latitude).isEqualTo(-3.596386)
        assertThat(track.trackPoints[1].longitude).isEqualTo(37.176037)

        assertThat(result.pointsOfInterest).isEmpty()
    }

    @Test
    fun `should convert LineString with elevation to Track`() {
        val featureCollectionDTO =
            FeatureCollectionDTO(
                features =
                    listOf(
                        FeatureDTO(
                            geometry =
                                LineStringDTO(
                                    coordinates =
                                        listOf(
                                            listOf(
                                                BigDecimal("-3.133811"),
                                                BigDecimal("37.29994"),
                                                BigDecimal("100.5"),
                                            ),
                                            listOf(
                                                BigDecimal("-3.596386"),
                                                BigDecimal("37.176037"),
                                                BigDecimal("250.0"),
                                            ),
                                        ),
                                    type = "LineString",
                                ),
                            properties = null,
                            type = "Feature",
                        ),
                    ),
                type = "FeatureCollection",
            )

        val result = mapper.fromGeoJson(featureCollectionDTO, "Fallback Name")

        assertThat(result.name).isEqualTo("Fallback Name")

        val track = checkNotNull(result.track) { "track must not be null" }
        assertThat(track.trackPoints).hasSize(2)
        assertThat(track.trackPoints[0].elevation).isEqualTo(100.5)
        assertThat(track.trackPoints[1].elevation).isEqualTo(250.0)
    }

    @Test
    fun `should convert mixed LineString and Point features`() {
        val featureCollectionDTO =
            FeatureCollectionDTO(
                features =
                    listOf(
                        FeatureDTO(
                            geometry =
                                LineStringDTO(
                                    coordinates =
                                        listOf(
                                            listOf(BigDecimal("-3.133811"), BigDecimal("37.29994")),
                                            listOf(BigDecimal("-3.596386"), BigDecimal("37.176037")),
                                        ),
                                    type = "LineString",
                                ),
                            properties = mapOf("name" to "Track Name"),
                            type = "Feature",
                        ),
                        FeatureDTO(
                            geometry =
                                PointDTO(
                                    coordinates = listOf(BigDecimal("37.203426"), BigDecimal("-3.439042")),
                                    type = "Point",
                                ),
                            properties = mapOf("name" to "POI", "type" to "FOOD"),
                            type = "Feature",
                        ),
                    ),
                type = "FeatureCollection",
            )

        val result = mapper.fromGeoJson(featureCollectionDTO, "Fallback Name")

        assertThat(result.name).isEqualTo("Track Name")

        val track = checkNotNull(result.track) { "track must not be null" }
        assertThat(track.trackPoints).hasSize(2)

        assertThat(result.pointsOfInterest).hasSize(1)
        assertThat(result.pointsOfInterest[0].name).isEqualTo("POI")
        assertThat(result.pointsOfInterest[0].type).isEqualTo(PoiType.FOOD)
        assertThat(result.pointsOfInterest[0].latitude).isEqualTo(-3.439042)
        assertThat(result.pointsOfInterest[0].longitude).isEqualTo(37.203426)
    }

    @Test
    fun `should still convert Points-only GeoJSON`() {
        val featureCollectionDTO =
            FeatureCollectionDTO(
                features =
                    listOf(
                        FeatureDTO(
                            geometry =
                                PointDTO(
                                    coordinates = listOf(BigDecimal("48.2"), BigDecimal("12.5")),
                                    type = "Point",
                                ),
                            properties = mapOf("name" to "POI"),
                            type = "Feature",
                        ),
                    ),
                type = "FeatureCollection",
            )

        val result = mapper.fromGeoJson(featureCollectionDTO, "Fallback Name")

        assertThat(result.name).isEqualTo("Fallback Name")
        assertThat(result.track).isNull()
        assertThat(result.pointsOfInterest).hasSize(1)
    }

    @Test
    fun `should use correct coordinate order for Point (lon-lat from GeoJSON)`() {
        val featureCollectionDTO =
            FeatureCollectionDTO(
                features =
                    listOf(
                        FeatureDTO(
                            geometry =
                                PointDTO(
                                    coordinates = listOf(BigDecimal("48.2"), BigDecimal("12.5")),
                                    type = "Point",
                                ),
                            properties = mapOf("name" to "Test Point"),
                            type = "Feature",
                        ),
                    ),
                type = "FeatureCollection",
            )

        val result = mapper.fromGeoJson(featureCollectionDTO, "Test")

        assertThat(result.pointsOfInterest).hasSize(1)
        assertThat(result.pointsOfInterest[0].latitude).isEqualTo(12.5)
        assertThat(result.pointsOfInterest[0].longitude).isEqualTo(48.2)
    }

    @Test
    fun `should use first LineString when multiple are present`() {
        val featureCollectionDTO =
            FeatureCollectionDTO(
                features =
                    listOf(
                        FeatureDTO(
                            geometry =
                                LineStringDTO(
                                    coordinates =
                                        listOf(
                                            listOf(BigDecimal("2.0"), BigDecimal("1.0")),
                                            listOf(BigDecimal("4.0"), BigDecimal("3.0")),
                                        ),
                                    type = "LineString",
                                ),
                            properties = mapOf("name" to "First Track"),
                            type = "Feature",
                        ),
                        FeatureDTO(
                            geometry =
                                LineStringDTO(
                                    coordinates =
                                        listOf(
                                            listOf(BigDecimal("6.0"), BigDecimal("5.0")),
                                            listOf(BigDecimal("8.0"), BigDecimal("7.0")),
                                        ),
                                    type = "LineString",
                                ),
                            properties = mapOf("name" to "Second Track"),
                            type = "Feature",
                        ),
                    ),
                type = "FeatureCollection",
            )

        val result = mapper.fromGeoJson(featureCollectionDTO, "Fallback")

        assertThat(result.name).isEqualTo("First Track")

        val track = checkNotNull(result.track) { "track must not be null" }
        assertThat(track.trackPoints).hasSize(2)
        assertThat(track.trackPoints[0].latitude).isEqualTo(1.0)
        assertThat(track.trackPoints[0].longitude).isEqualTo(2.0)
    }

    @Test
    fun `should extract times from coordinateProperties in LineString`() {
        val featureCollectionDTO =
            FeatureCollectionDTO(
                features =
                    listOf(
                        FeatureDTO(
                            geometry =
                                LineStringDTO(
                                    coordinates =
                                        listOf(
                                            listOf(BigDecimal("10.0"), BigDecimal("53.0"), BigDecimal("5.0")),
                                            listOf(BigDecimal("10.1"), BigDecimal("53.1"), BigDecimal("5.5")),
                                        ),
                                    type = "LineString",
                                ),
                            properties =
                                mapOf(
                                    "name" to "Test Track",
                                    "coordinateProperties" to mapOf("times" to listOf(0, 5000)),
                                ),
                            type = "Feature",
                        ),
                    ),
                type = "FeatureCollection",
            )
        val beforeParsing = Instant.now()

        val result = mapper.fromGeoJson(featureCollectionDTO, "Fallback")

        val afterParsing = Instant.now()
        val track = checkNotNull(result.track) { "track must not be null" }
        assertThat(track.trackPoints).hasSize(2)

        val firstTime = checkNotNull(track.trackPoints[0].time) { "first time must not be null" }
        val secondTime = checkNotNull(track.trackPoints[1].time) { "second time must not be null" }

        assertThat(firstTime).isBetween(beforeParsing.minusMillis(100), afterParsing.plusMillis(100))
        assertThat(secondTime).isBetween(
            beforeParsing.plusMillis(5000).minusMillis(100),
            afterParsing.plusMillis(5000).plusMillis(100),
        )
    }

    @Test
    fun `should handle LineString without coordinateProperties`() {
        val featureCollectionDTO =
            FeatureCollectionDTO(
                features =
                    listOf(
                        FeatureDTO(
                            geometry =
                                LineStringDTO(
                                    coordinates =
                                        listOf(
                                            listOf(BigDecimal("10.0"), BigDecimal("53.0")),
                                            listOf(BigDecimal("10.1"), BigDecimal("53.1")),
                                        ),
                                    type = "LineString",
                                ),
                            properties = mapOf("name" to "Test Track"),
                            type = "Feature",
                        ),
                    ),
                type = "FeatureCollection",
            )

        val result = mapper.fromGeoJson(featureCollectionDTO, "Fallback")

        val track = checkNotNull(result.track) { "track must not be null" }
        assertThat(track.trackPoints).hasSize(2)
        assertThat(track.trackPoints[0].time).isNull()
        assertThat(track.trackPoints[1].time).isNull()
    }

    @Test
    fun `should handle empty coordinateProperties object`() {
        val featureCollectionDTO =
            FeatureCollectionDTO(
                features =
                    listOf(
                        FeatureDTO(
                            geometry =
                                LineStringDTO(
                                    coordinates =
                                        listOf(
                                            listOf(BigDecimal("10.0"), BigDecimal("53.0")),
                                            listOf(BigDecimal("10.1"), BigDecimal("53.1")),
                                        ),
                                    type = "LineString",
                                ),
                            properties =
                                mapOf(
                                    "name" to "Test Track",
                                    "coordinateProperties" to emptyMap<String, Any>(),
                                ),
                            type = "Feature",
                        ),
                    ),
                type = "FeatureCollection",
            )

        val result = mapper.fromGeoJson(featureCollectionDTO, "Fallback")

        val track = checkNotNull(result.track) { "track must not be null" }
        assertThat(track.trackPoints).hasSize(2)
        assertThat(track.trackPoints[0].time).isNull()
        assertThat(track.trackPoints[1].time).isNull()
    }

    @Test
    fun `should handle coordinateProperties with null times`() {
        val featureCollectionDTO =
            FeatureCollectionDTO(
                features =
                    listOf(
                        FeatureDTO(
                            geometry =
                                LineStringDTO(
                                    coordinates =
                                        listOf(
                                            listOf(BigDecimal("10.0"), BigDecimal("53.0")),
                                            listOf(BigDecimal("10.1"), BigDecimal("53.1")),
                                        ),
                                    type = "LineString",
                                ),
                            properties =
                                mapOf(
                                    "name" to "Test Track",
                                    "coordinateProperties" to mapOf("times" to null),
                                ),
                            type = "Feature",
                        ),
                    ),
                type = "FeatureCollection",
            )

        val result = mapper.fromGeoJson(featureCollectionDTO, "Fallback")

        val track = checkNotNull(result.track) { "track must not be null" }
        assertThat(track.trackPoints).hasSize(2)
        assertThat(track.trackPoints[0].time).isNull()
        assertThat(track.trackPoints[1].time).isNull()
    }

    @Test
    fun `should throw exception when times array length does not match coordinates`() {
        val featureCollectionDTO =
            FeatureCollectionDTO(
                features =
                    listOf(
                        FeatureDTO(
                            geometry =
                                LineStringDTO(
                                    coordinates =
                                        listOf(
                                            listOf(BigDecimal("10.0"), BigDecimal("53.0")),
                                            listOf(BigDecimal("10.1"), BigDecimal("53.1")),
                                            listOf(BigDecimal("10.2"), BigDecimal("53.2")),
                                        ),
                                    type = "LineString",
                                ),
                            properties =
                                mapOf(
                                    "name" to "Test Track",
                                    "coordinateProperties" to mapOf("times" to listOf(0, 5000)),
                                ),
                            type = "Feature",
                        ),
                    ),
                type = "FeatureCollection",
            )

        assertThatThrownBy {
            mapper.fromGeoJson(featureCollectionDTO, "Fallback")
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("coordinateProperties.times array length")
            .hasMessageContaining("must match coordinates length")
    }

    @Test
    fun `should parse test_advanced json with 44 coordinate properties`() {
        val json = File("src/test/http/data/test_advanced.json").readText()
        val featureCollectionDTO =
            jacksonObjectMapper()
                .readValue(json, FeatureCollectionDTO::class.java)
        val beforeParsing = Instant.now()

        val result = mapper.fromGeoJson(featureCollectionDTO, "Fallback")

        val afterParsing = Instant.now()
        val track = checkNotNull(result.track) { "track must not be null" }
        assertThat(track.trackPoints).hasSize(44)

        val firstTime = checkNotNull(track.trackPoints[0].time) { "first time must not be null" }
        val lastTime = checkNotNull(track.trackPoints[43].time) { "last time must not be null" }

        assertThat(firstTime).isBetween(beforeParsing.minusMillis(100), afterParsing.plusMillis(100))
        assertThat(lastTime).isBetween(
            beforeParsing.plusMillis(487837).minusMillis(100),
            afterParsing.plusMillis(487837).plusMillis(100),
        )
    }

    @Test
    fun `should handle times starting with non-zero offset`() {
        val featureCollectionDTO =
            FeatureCollectionDTO(
                features =
                    listOf(
                        FeatureDTO(
                            geometry =
                                LineStringDTO(
                                    coordinates =
                                        listOf(
                                            listOf(BigDecimal("10.0"), BigDecimal("53.0")),
                                            listOf(BigDecimal("10.1"), BigDecimal("53.1")),
                                            listOf(BigDecimal("10.2"), BigDecimal("53.2")),
                                        ),
                                    type = "LineString",
                                ),
                            properties =
                                mapOf(
                                    "name" to "Test Track",
                                    "coordinateProperties" to mapOf("times" to listOf(1000, 2000, 3000)),
                                ),
                            type = "Feature",
                        ),
                    ),
                type = "FeatureCollection",
            )
        val beforeParsing = Instant.now()

        val result = mapper.fromGeoJson(featureCollectionDTO, "Fallback")

        val afterParsing = Instant.now()
        val track = checkNotNull(result.track) { "track must not be null" }
        assertThat(track.trackPoints).hasSize(3)

        val firstTime = checkNotNull(track.trackPoints[0].time) { "first time must not be null" }

        assertThat(firstTime).isBetween(
            beforeParsing.plusMillis(1000).minusMillis(100),
            afterParsing.plusMillis(1000).plusMillis(100),
        )
    }

    @Test
    fun `should use provided clock for timestamp calculation`() {
        val fixedInstant = Instant.parse("2024-01-15T10:30:00Z")
        val fixedClock = Clock.fixed(fixedInstant, ZoneOffset.UTC)
        val featureCollectionDTO =
            FeatureCollectionDTO(
                features =
                    listOf(
                        FeatureDTO(
                            geometry =
                                LineStringDTO(
                                    coordinates =
                                        listOf(
                                            listOf(BigDecimal("10.0"), BigDecimal("53.0")),
                                            listOf(BigDecimal("10.1"), BigDecimal("53.1")),
                                        ),
                                    type = "LineString",
                                ),
                            properties =
                                mapOf(
                                    "name" to "Test Track",
                                    "coordinateProperties" to mapOf("times" to listOf(0, 5000)),
                                ),
                            type = "Feature",
                        ),
                    ),
                type = "FeatureCollection",
            )

        val result = mapper.fromGeoJson(featureCollectionDTO, "Fallback", fixedClock)

        val track = checkNotNull(result.track) { "track must not be null" }
        assertThat(track.trackPoints).hasSize(2)
        assertThat(track.trackPoints[0].time).isEqualTo(fixedInstant)
        assertThat(track.trackPoints[1].time).isEqualTo(fixedInstant.plusMillis(5000))
    }

    @Test
    fun `should throw detailed exception when times contains non-numeric value`() {
        val featureCollectionDTO =
            FeatureCollectionDTO(
                features =
                    listOf(
                        FeatureDTO(
                            geometry =
                                LineStringDTO(
                                    coordinates =
                                        listOf(
                                            listOf(BigDecimal("10.0"), BigDecimal("53.0")),
                                            listOf(BigDecimal("10.1"), BigDecimal("53.1")),
                                            listOf(BigDecimal("10.2"), BigDecimal("53.2")),
                                        ),
                                    type = "LineString",
                                ),
                            properties =
                                mapOf(
                                    "name" to "Test Track",
                                    "coordinateProperties" to mapOf("times" to listOf(0, "invalid", 5000)),
                                ),
                            type = "Feature",
                        ),
                    ),
                type = "FeatureCollection",
            )

        assertThatThrownBy {
            mapper.fromGeoJson(featureCollectionDTO, "Fallback")
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("coordinateProperties.times[1]")
            .hasMessageContaining("not a number")
    }

    @Test
    fun `should throw detailed exception when times contains null value`() {
        val featureCollectionDTO =
            FeatureCollectionDTO(
                features =
                    listOf(
                        FeatureDTO(
                            geometry =
                                LineStringDTO(
                                    coordinates =
                                        listOf(
                                            listOf(BigDecimal("10.0"), BigDecimal("53.0")),
                                            listOf(BigDecimal("10.1"), BigDecimal("53.1")),
                                        ),
                                    type = "LineString",
                                ),
                            properties =
                                mapOf(
                                    "name" to "Test Track",
                                    "coordinateProperties" to mapOf("times" to listOf(0, null)),
                                ),
                            type = "Feature",
                        ),
                    ),
                type = "FeatureCollection",
            )

        assertThatThrownBy {
            mapper.fromGeoJson(featureCollectionDTO, "Fallback")
        }.isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("coordinateProperties.times[1]")
            .hasMessageContaining("null")
    }
}
