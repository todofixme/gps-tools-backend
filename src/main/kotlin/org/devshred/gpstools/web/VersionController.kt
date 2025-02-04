package org.devshred.gpstools.web

import jakarta.annotation.PostConstruct
import org.devshred.gpstools.api.ServerApi
import org.devshred.gpstools.api.model.VersionDTO
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.CrossOrigin
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1")
@CrossOrigin(origins = ["*"], maxAge = 3600)
class VersionController : ServerApi {
    private val log = LoggerFactory.getLogger(javaClass)

    @Value("\${app.version}")
    private lateinit var appVersion: String

    @Value("\${git.version}")
    private lateinit var gitVersion: String

    @PostConstruct
    fun printVersion() {
        log.info("[APP_VERSION] {}", appVersion)
        log.info("[GIT_VERSION] {}", gitVersion)
    }

    override fun version(): ResponseEntity<VersionDTO> {
        return ResponseEntity.ok(VersionDTO(appVersion, gitVersion))
    }
}
