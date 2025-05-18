package org.devshred.gpstools.storage

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.devshred.gpstools.api.model.FeatureCollectionDTO
import org.devshred.gpstools.api.model.FeatureDTO
import org.devshred.gpstools.api.model.PointDTO
import org.devshred.gpstools.formats.gps.GpsContainerMapper
import org.devshred.gpstools.formats.gpx.GpxService
import org.devshred.gpstools.formats.proto.ProtoPoiType
import org.devshred.gpstools.formats.proto.ProtoService
import org.devshred.gpstools.formats.proto.protoContainer
import org.devshred.gpstools.formats.proto.protoPointOfInterest
import org.devshred.gpstools.formats.proto.protoTrack
import org.devshred.gpstools.formats.proto.protoTrackPoint
import org.junit.jupiter.api.Test
import java.io.InputStream
import java.math.BigDecimal
import java.util.UUID

class FileServiceTest {
    private val trackStore = mockk<TrackStore>()
    private val ioService = mockk<IOService>()
    private val protoService = mockk<ProtoService>()
    private val gpxService = GpxService()

    private val mapper = GpsContainerMapper()

    private var cut = FileService(trackStore, ioService, protoService, gpxService, mapper)

    private val trackId = UUID.randomUUID()
    private val storageLocation = "/path/to/file"
    private val trackName = "test"
    private val storedTrack = StoredTrack(trackId, trackName, storageLocation)

    @Test
    fun `replace single point`() {
        val protoContainer = createProtoContainer(Pair(UUID.randomUUID(), "some point"))

        every { trackStore.get(trackId) } returns storedTrack
        every { protoService.readProtoContainer(storageLocation) } returns protoContainer
        every { ioService.createTempFile(any<InputStream>(), trackName) } returns storedTrack
        every { trackStore.put(trackId, storedTrack) } returns Unit
        every { ioService.delete(storageLocation) } returns Unit

        val pointDTO =
            PointDTO(
                coordinates = listOf(BigDecimal.valueOf(11), BigDecimal.valueOf(22)),
                type = "Point",
            )
        val featureDTO =
            FeatureDTO(
                geometry = pointDTO,
                properties =
                    mapOf(
                        "name" to "another point",
                        "type" to "GENERIC",
                        "uuid" to UUID.randomUUID().toString(),
                    ),
                type = "Feature",
            )

        // 'merge == false' => replace
        val wayPointUpdate = cut.handleWayPointUpdate(trackId, featureDTO, null, false)

        assertThat(wayPointUpdate.features).hasSize(1)
        assertThat(wayPointUpdate.features[0].properties["name"]).isEqualTo("another point")
    }

    @Test
    fun `replace multiple points`() {
        val protoContainer =
            createProtoContainer(Pair(UUID.randomUUID(), "first point"), Pair(UUID.randomUUID(), "second point"))

        every { trackStore.get(trackId) } returns storedTrack
        every { protoService.readProtoContainer(storageLocation) } returns protoContainer
        every { ioService.createTempFile(any<InputStream>(), trackName) } returns storedTrack
        every { trackStore.put(trackId, any()) } returns Unit
        every { ioService.delete(storageLocation) } returns Unit

        val pointDTO1 =
            PointDTO(
                coordinates = listOf(BigDecimal.valueOf(11), BigDecimal.valueOf(21)),
                type = "Point",
            )
        val featureDTO1 =
            FeatureDTO(
                geometry = pointDTO1,
                properties =
                    mapOf(
                        "name" to "another first point",
                        "type" to "GENERIC",
                        "uuid" to UUID.randomUUID().toString(),
                    ),
                type = "Feature",
            )
        val pointDTO2 =
            PointDTO(
                coordinates = listOf(BigDecimal.valueOf(12), BigDecimal.valueOf(22)),
                type = "Point",
            )
        val featureDTO2 =
            FeatureDTO(
                geometry = pointDTO2,
                properties =
                    mapOf(
                        "name" to "another second point",
                        "type" to "GENERIC",
                        "uuid" to UUID.randomUUID().toString(),
                    ),
                type = "Feature",
            )
        val featureCollectionDTO =
            FeatureCollectionDTO(
                features = listOf(featureDTO1, featureDTO2),
                type = "FeatureCollection",
            )
        // 'merge == false' => replace
        val wayPointUpdate = cut.handleWayPointUpdate(trackId, featureCollectionDTO, null, false)

        assertThat(wayPointUpdate.features).hasSize(2)
        assertThat(wayPointUpdate.features.map { it.properties["name"] }).containsOnly(
            "another first point",
            "another second point",
        )
    }

    @Test
    fun `merge point`() {
        val pointId = UUID.randomUUID()
        val protoContainer =
            createProtoContainer(Pair(pointId, "first point"), Pair(UUID.randomUUID(), "second point"))

        every { trackStore.get(trackId) } returns storedTrack
        every { protoService.readProtoContainer(storageLocation) } returns protoContainer
        every { ioService.createTempFile(any<InputStream>(), trackName) } returns storedTrack
        every { trackStore.put(trackId, any()) } returns Unit
        every { ioService.delete(storageLocation) } returns Unit

        val pointDTO1 =
            PointDTO(
                coordinates = listOf(BigDecimal.valueOf(11), BigDecimal.valueOf(21)),
                type = "Point",
            )
        val featureDTO1 =
            FeatureDTO(
                geometry = pointDTO1,
                properties =
                    mapOf(
                        "name" to "another first point",
                        "type" to "GENERIC",
                        "uuid" to pointId.toString(),
                    ),
                type = "Feature",
            )
        val featureCollectionDTO =
            FeatureCollectionDTO(
                features = listOf(featureDTO1),
                type = "FeatureCollection",
            )
        // 'merge == true' => replace if pointId already exists
        val wayPointUpdate = cut.handleWayPointUpdate(trackId, featureCollectionDTO, null, true)

        assertThat(wayPointUpdate.features).hasSize(2)
        assertThat(wayPointUpdate.features.map { it.properties["name"] }).containsOnly(
            "another first point",
            "second point",
        )
    }

    @Test
    fun `add point`() {
        val protoContainer = createProtoContainer(Pair(UUID.randomUUID(), "first point"))

        every { trackStore.get(trackId) } returns storedTrack
        every { protoService.readProtoContainer(storageLocation) } returns protoContainer
        every { ioService.createTempFile(any<InputStream>(), trackName) } returns storedTrack
        every { trackStore.put(trackId, any()) } returns Unit
        every { ioService.delete(storageLocation) } returns Unit

        val pointDTO1 =
            PointDTO(
                coordinates = listOf(BigDecimal.valueOf(11), BigDecimal.valueOf(21)),
                type = "Point",
            )
        val featureDTO1 =
            FeatureDTO(
                geometry = pointDTO1,
                properties =
                    mapOf(
                        "name" to "second point",
                        "type" to "GENERIC",
                        "uuid" to UUID.randomUUID().toString(),
                    ),
                type = "Feature",
            )
        val featureCollectionDTO =
            FeatureCollectionDTO(
                features = listOf(featureDTO1),
                type = "FeatureCollection",
            )
        // 'merge == true' => add if pointId not exists
        val wayPointUpdate = cut.handleWayPointUpdate(trackId, featureCollectionDTO, null, true)

        assertThat(wayPointUpdate.features).hasSize(2)
        assertThat(wayPointUpdate.features.map { it.properties["name"] }).containsOnly(
            "first point",
            "second point",
        )
    }

    @Test
    fun `create track with new name and delete existing one`() {
        val newTrackName = "New Track Name"
        val protoContainer = createProtoContainer()
        val trackNameSlot = slot<String>()

        every { trackStore.get(trackId) } returns storedTrack
        every { protoService.readProtoContainer(storageLocation) } returns protoContainer
        every { ioService.createTempFile(any<InputStream>(), capture(trackNameSlot)) } returns storedTrack
        every { trackStore.put(trackId, storedTrack) } returns Unit
        every { ioService.delete(storageLocation) } returns Unit

        cut.changeTrackName(trackId, newTrackName)

        assertThat(trackNameSlot.captured).startsWith(newTrackName)
        verify { ioService.delete(storageLocation) }
    }

    @Test
    fun `import GPX activity file`() {
        val actual = cut.getGpsContainerFromGpxFile(getAbsolutePath("garmin/activity.gpx"))

        assertThat(actual).isNotNull
        assertThat(actual.name).isEqualTo("Gorafe Rennradfahren")
        assertThat(actual.pointsOfInterest).isEmpty()
        assertThat(actual.track?.trackPoints).hasSize(1303)
    }

    @Test
    fun `import GPX course file`() {
        val actual = cut.getGpsContainerFromGpxFile(getAbsolutePath("garmin/course.gpx"))

        assertThat(actual).isNotNull
        assertThat(actual.name).isEqualTo("Champs-Élysées")
        assertThat(actual.pointsOfInterest).hasSize(1)
        assertThat(actual.pointsOfInterest[0].name).isEqualTo("Fontaine")
        assertThat(actual.track?.trackPoints).hasSize(18)
    }

    @Test
    fun `import TCX activity file`() {
        val actual = cut.getGpsContainerFromTcxFile(getAbsolutePath("garmin/activity.tcx"))

        assertThat(actual).isNotNull
        assertThat(actual.name).isEqualTo("Activity")
        assertThat(actual.pointsOfInterest).isEmpty()
        assertThat(actual.track?.trackPoints).hasSize(1303)
    }

    @Test
    fun `import TCX course file`() {
        val actual = cut.getGpsContainerFromTcxFile(getAbsolutePath("garmin/course.tcx"))

        assertThat(actual).isNotNull
        assertThat(actual.name).isEqualTo("Champs-Élysées")
        assertThat(actual.pointsOfInterest).hasSize(1)
        assertThat(actual.pointsOfInterest[0].name).isEqualTo("Fontaine")
        assertThat(actual.track?.trackPoints).hasSize(18)
    }

    @Test
    fun `import FIT activity file`() {
        val actual = cut.getGpsContainerFromFitFile(getAbsolutePath("garmin/activity.fit"))

        assertThat(actual).isNotNull
        assertThat(actual.name).isEqualTo("Activity")
        assertThat(actual.pointsOfInterest).isEmpty()
        assertThat(actual.track?.trackPoints).hasSize(1303)
    }

    @Test
    fun `import FIT course file`() {
        val actual = cut.getGpsContainerFromFitFile(getAbsolutePath("garmin/course.fit"))

        assertThat(actual).isNotNull
        assertThat(actual.name).isEqualTo("Activity")
        assertThat(actual.pointsOfInterest).hasSize(1)
        assertThat(actual.pointsOfInterest[0].name).isEqualTo("Wasser")
        assertThat(actual.track?.trackPoints).hasSize(18)
    }

    private fun getAbsolutePath(fileName: String): String {
        val resource = this::class.java.classLoader.getResource(fileName)
        val absolutePath = resource?.toURI()?.path
        return absolutePath!!
    }
}

fun createProtoContainer(vararg points: Pair<UUID, String>) =
    protoContainer {
        name = "My Track"
        pointsOfInterest +=
            points.map {
                protoPointOfInterest {
                    uuid = it.first.toString()
                    name = it.second
                    latitude = 1.0
                    longitude = 2.0
                    type = ProtoPoiType.SUMMIT
                }
            }
        track =
            protoTrack {
                trackPoints +=
                    protoTrackPoint {
                        latitude = 1.0
                        longitude = 2.0
                    }
            }
    }
