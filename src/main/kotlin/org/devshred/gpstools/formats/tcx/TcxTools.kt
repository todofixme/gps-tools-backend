package org.devshred.gpstools.formats.tcx

import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.dataformat.xml.XmlMapper
import com.fasterxml.jackson.dataformat.xml.ser.ToXmlGenerator
import java.io.ByteArrayOutputStream

object TcxTools {
    val XML_MAPPER: XmlMapper =
        XmlMapper
            .Builder(XmlMapper())
            .enable(SerializationFeature.INDENT_OUTPUT)
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .build()

    init {
        XML_MAPPER.factory.enable(ToXmlGenerator.Feature.WRITE_XML_DECLARATION)
    }
}

fun tcxToByteArrayOutputStream(tcx: TrainingCenterDatabase): ByteArrayOutputStream {
    val out = ByteArrayOutputStream()
    TcxTools.XML_MAPPER.writeValue(out, tcx)
    return out
}
