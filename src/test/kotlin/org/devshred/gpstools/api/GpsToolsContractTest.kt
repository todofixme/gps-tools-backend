package org.devshred.gpstools.api

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.ObjectMapper
import io.github.microcks.testcontainers.MicrocksContainer
import io.github.microcks.testcontainers.model.TestRequest
import io.github.microcks.testcontainers.model.TestResult
import io.github.microcks.testcontainers.model.TestRunnerType
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.parser.OpenAPIV3Parser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.testcontainers.Testcontainers
import org.testcontainers.junit.jupiter.Container
import java.io.File

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@org.testcontainers.junit.jupiter.Testcontainers
class GpsToolsContractTest {
    companion object {
        @JvmStatic
        @Container
        var microcksContainer: MicrocksContainer =
            MicrocksContainer("quay.io/microcks/microcks-uber:1.11.0")
                .withAccessToHost(true)

        @JvmStatic
        @BeforeAll
        fun setup() {
            microcksContainer.importAsMainArtifact(File("src/main/spec/api-spec.yaml"))
        }

        @JvmStatic
        val parsedSpec: OpenAPI = OpenAPIV3Parser().read("src/main/spec/api-spec.yaml")
    }

    @LocalServerPort
    private val port: Int? = null

    @BeforeEach
    fun setupPort() {
        port?.let { Testcontainers.exposeHostPorts(it) }
    }

    @Test
    fun testOpenAPIContract() {
        val testRequest =
            TestRequest
                .Builder()
                .serviceId(parsedSpec.info.title + ":" + parsedSpec.info.version)
                .runnerType(TestRunnerType.OPEN_API_SCHEMA.name)
                .testEndpoint("http://host.testcontainers.internal:$port/api/v1")
                .build()

        val testResult: TestResult = microcksContainer.testEndpoint(testRequest)

        val mapper: ObjectMapper = ObjectMapper().setSerializationInclusion(JsonInclude.Include.NON_NULL)
        println(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(testResult))

        assertTrue(testResult.isSuccess)
        assertEquals(1, testResult.testCaseResults.size)
    }
}
