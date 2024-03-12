package org.devshred.gpstools.domain.tcx

import org.devshred.gpstools.domain.IOService
import org.devshred.gpstools.domain.proto.GpsContainerMapper
import org.devshred.gpstools.domain.proto.protoInputStreamResourceToProtoGpsContainer
import org.springframework.stereotype.Service
import java.io.ByteArrayInputStream

@Service
class TcxService(private val ioService: IOService, private val mapper: GpsContainerMapper) {
    fun protoFileToTcxInputStream(
        storageLocation: String,
        name: String?,
    ): ByteArrayInputStream {
        val stream = ioService.getAsStream(storageLocation)
        val proto = protoInputStreamResourceToProtoGpsContainer(stream, name)
        val gpsContainer = mapper.map(proto)
        val tcx: TrainingCenterDatabase = createTcxFromGpsContainer(gpsContainer)
        val outputStream = tcxToByteArrayOutputStream(tcx)
        return ByteArrayInputStream(outputStream.toByteArray())
    }
}
