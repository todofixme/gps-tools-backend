package org.devshred.gpstools.formats.geojson

import mil.nga.sf.geojson.FeatureCollection
import mil.nga.sf.geojson.FeatureConverter
import org.springframework.stereotype.Service
import java.io.File

@Service
class GeoJsonService {
    fun geoJsonFromFileLocation(storageLocation: String): FeatureCollection {
        val content = File(storageLocation).readText()
        return FeatureConverter.toFeatureCollection(content)
    }
}
