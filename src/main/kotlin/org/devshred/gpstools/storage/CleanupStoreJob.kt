package org.devshred.gpstools.storage

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.LocalDateTime

@Component
class CleanupStoreJob(
    private val trackStore: TrackStore,
    private val ioService: IOService,
    @Value("\${app.store-max-age}") private val maxAge: Long,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedRate = 3_600_000) // schedules the method to run every hour
    fun reportCurrentTime() {
        log.info("Running cleanup job.")

        log.info("{} files in store.", trackStore.list().size)

        trackStore.list().toList() // creates a copy of the list to avoid race conditions
            .filter { it.lastModifiedDate.isBefore(LocalDateTime.now().minusSeconds(maxAge)) }
            .forEach {
                log.info("Deleting file {} ({}).", it.id, it.name)
                ioService.delete(it.storageLocation)
                trackStore.delete(it.id)
            }

        log.info("{} files left in store.", trackStore.list().size)

        log.info("Cleanup job finished.")
    }
}
