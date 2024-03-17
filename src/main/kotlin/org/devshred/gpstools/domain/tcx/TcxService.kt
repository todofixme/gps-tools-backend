package org.devshred.gpstools.domain.tcx

import org.devshred.gpstools.domain.proto.GpsContainerMapper
import org.devshred.gpstools.domain.proto.ProtoService
import org.springframework.stereotype.Service
import java.io.ByteArrayInputStream

@Service
class TcxService(private val protoService: ProtoService, private val mapper: GpsContainerMapper) {
    fun protoFileToTcxInputStream(
        storageLocation: String,
        name: String?,
    ): ByteArrayInputStream {
        val proto = protoService.readProtoGpsContainer(storageLocation, name)
        val gpsContainer = mapper.map(proto)
        val tcx: TrainingCenterDatabase = createTcxFromGpsContainer(gpsContainer)
        val outputStream = tcxToByteArrayOutputStream(tcx)
        return ByteArrayInputStream(outputStream.toByteArray())
    }
}
