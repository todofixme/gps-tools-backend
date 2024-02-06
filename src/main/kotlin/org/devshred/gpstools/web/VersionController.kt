package org.devshred.gpstools.web

import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class VersionController {
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

    @GetMapping("/version")
    fun version(): Version {
        return Version(appVersion, gitVersion)
    }
}

data class Version(val app: String, val git: String)
