package org.devshred.gpstools.formats.proto

import org.devshred.gpstools.common.orElse
import org.devshred.gpstools.storage.IOService
import org.springframework.core.io.InputStreamResource
import org.springframework.stereotype.Service

@Service
class ProtoService(private val ioService: IOService) {
    fun readProtoContainer(storageLocation: String): ProtoContainer {
        val inputStreamResource: InputStreamResource = ioService.getAsStream(storageLocation)
        return ProtoContainer.parseFrom(inputStreamResource.contentAsByteArray)
    }

    fun readProtoContainer(
        storageLocation: String,
        trackname: String?,
    ): ProtoContainer =
        trackname?.let {
            readProtoContainer(storageLocation).toBuilder().setName(trackname).build()
        }.orElse {
            readProtoContainer(storageLocation)
        }
}
