package org.devshred.gpstools.formats.gpx

import io.jenetics.jpx.GPX
import org.springframework.stereotype.Service
import java.nio.file.Path

@Service
class GpxService {
    fun gpxFromFileLocation(location: String): GPX = GPX.read(Path.of(location))
}
