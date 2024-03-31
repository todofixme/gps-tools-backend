package org.devshred.gpstools

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.PropertySource
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableScheduling
@PropertySource("classpath:git.properties")
class TheApplication

fun main(args: Array<String>) {
    runApplication<TheApplication>(*args)
}
