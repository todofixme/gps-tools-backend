package org.devshred.gpstools.storage

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.LocalDateTime

@Component
class CleanupStoreJob(
    private val fileStore: FileStore,
    @Value("\${app.store-max-age}") private val maxAge: Long,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedRate = 60_000) // schedules the method to run every 60 seconds
    fun reportCurrentTime() {
        fileStore.list().toList() // creates a copy of the list to avoid race conditions
            .filter { it.createdAt.isBefore(LocalDateTime.now().minusMinutes(maxAge)) }
            .forEach {
                log.info("Deleting file {} ({}).", it.id, it.filename)
                fileStore.delete(it.id)
            }
    }
}
