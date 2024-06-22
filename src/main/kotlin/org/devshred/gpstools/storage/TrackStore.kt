package org.devshred.gpstools.storage

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

@Service
class TrackStore {
    private val store: MutableMap<UUID, StoredTrack> = ConcurrentHashMap()
    private val log = LoggerFactory.getLogger(javaClass)

    fun get(id: UUID) = store.getOrElse(id) { throw NotFoundException("Track with ID $id not found.") }

    fun put(
        uuid: UUID,
        storedTrack: StoredTrack,
    ) {
        store[uuid] = storedTrack.copy(lastModifiedDate = LocalDateTime.now())
    }

    fun put(storedTrack: StoredTrack) {
        store[storedTrack.id] = storedTrack.copy(lastModifiedDate = LocalDateTime.now())
    }

    fun delete(id: UUID): StoredTrack? {
        val ret = store.remove(id)
        if (ret == null) {
            log.warn("Failed to delete track with ID {}", id)
        }
        return ret
    }

    fun list() = store.values.toList()
}
