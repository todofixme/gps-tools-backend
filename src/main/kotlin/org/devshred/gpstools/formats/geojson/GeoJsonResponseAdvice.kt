package org.devshred.gpstools.formats.geojson

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import org.slf4j.LoggerFactory
import org.springframework.core.MethodParameter
import org.springframework.http.MediaType
import org.springframework.http.converter.HttpMessageConverter
import org.springframework.http.server.ServerHttpRequest
import org.springframework.http.server.ServerHttpResponse
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice

@ControllerAdvice
class GeoJsonResponseAdvice(
    private val objectMapper: ObjectMapper,
) : ResponseBodyAdvice<Any> {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun supports(
        returnType: MethodParameter,
        converterType: Class<out HttpMessageConverter<*>>,
    ): Boolean = true

    override fun beforeBodyWrite(
        body: Any?,
        returnType: MethodParameter,
        selectedContentType: MediaType,
        selectedConverterType: Class<out HttpMessageConverter<*>>,
        request: ServerHttpRequest,
        response: ServerHttpResponse,
    ): Any? {
        if (body == null || !isGeoJsonObject(body)) return body

        return try {
            val jsonNode = body as? JsonNode ?: objectMapper.valueToTree(body)

            if (jsonNode is ObjectNode) {
                removeDuplicateTypeField(jsonNode)
                objectMapper.convertValue(jsonNode, Map::class.java)
            } else {
                body
            }
        } catch (e: Exception) {
            log.error("Fehler beim Verarbeiten der GeoJSON-Antwort: {}", e.message)
            body
        }
    }

    private fun isGeoJsonObject(obj: Any): Boolean {
        val className = obj.javaClass.name
        return className.contains("Point") ||
            className.contains("LineString") ||
            className.contains("Feature") ||
            className.contains("GeoJson")
    }

    private fun removeDuplicateTypeField(node: JsonNode) {
        if (node is ObjectNode && node.has("type")) {
            val typeValue = node["type"].asText()
            var foundFirst = false

            node.properties().asSequence().toList().forEach { (key, value) ->
                when {
                    key == "type" && value.asText() == typeValue -> {
                        if (foundFirst) node.remove(key) else foundFirst = true
                    }

                    value.isObject || value.isArray -> removeDuplicateTypeField(value)
                }
            }
        } else if (node.isArray) {
            node.forEach { removeDuplicateTypeField(it) }
        }
    }
}
