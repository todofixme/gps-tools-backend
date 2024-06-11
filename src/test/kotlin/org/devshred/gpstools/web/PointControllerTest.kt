package org.devshred.gpstools.web

import mil.nga.sf.geojson.Feature
import mil.nga.sf.geojson.FeatureCollection
import mil.nga.sf.geojson.Point
import mil.nga.sf.geojson.Position
import org.assertj.core.api.Assertions.assertThat
import org.devshred.gpstools.api.model.PointDTO
import org.junit.jupiter.api.Test

class PointControllerTest {
    @Test
    fun `map GeoJSON point to DTO`() {
        val position = Position(53.544225, 10.064383)
        val point = Point(position)
        val feature = Feature(point)
        feature.properties["name"] = "K1"
        feature.properties["type"] = "SUMMIT"

        val featureDTO = feature.toDto()

        assertThat(featureDTO.type).isEqualTo("Feature")
        assertThat(featureDTO.geometry.type).isEqualTo("Point")
        assertThat(featureDTO.geometry).isInstanceOf(PointDTO::class.java)
        assertThat((featureDTO.geometry as PointDTO).coordinates[0].toDouble()).isEqualTo(53.544225)
        assertThat((featureDTO.geometry as PointDTO).coordinates[1].toDouble()).isEqualTo(10.064383)
        assertThat(featureDTO.properties).isInstanceOf(Map::class.java)
        assertThat((featureDTO.properties as Map<*, *>)["name"]).isEqualTo("K1")
        assertThat((featureDTO.properties as Map<*, *>)["type"]).isEqualTo("SUMMIT")
    }

    @Test
    fun `map collection of GeoJSON points to DTO`() {
        val position1 = Position(53.544225, 10.064383)
        val point1 = Point(position1)
        val feature1 = Feature(point1)
        feature1.properties["name"] = "K1"
        feature1.properties["type"] = "SUMMIT"

        val position2 = Position(63.544225, 20.064383)
        val point2 = Point(position2)
        val feature2 = Feature(point2)
        feature2.properties["name"] = "K2"
        feature2.properties["type"] = "VALLEY"

        val featureCollection = FeatureCollection(listOf(feature1, feature2))

        val featureCollectionDTO = featureCollection.toDto()

        assertThat(featureCollectionDTO.type).isEqualTo("FeatureCollection")
        assertThat(featureCollectionDTO.features).hasSize(2)

        assertThat(featureCollectionDTO.features.map { (it.properties as Map<*, *>)["name"] }).containsOnly("K1", "K2")
    }
}
