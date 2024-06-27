package org.devshred.gpstools.storage

import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

@Service
class TrackStore {
    private val store: MutableMap<UUID, StoredTrack> = ConcurrentHashMap()

    fun get(id: UUID) = store.getOrElse(id) { throw NotFoundException("Track with ID $id not found.") }

    fun put(
        uuid: UUID,
        file: StoredTrack,
    ) {
        store[uuid] = file
    }

    fun put(storedTrack: StoredTrack) {
        store[storedTrack.id] = storedTrack.copy(lastModifiedDate = LocalDateTime.now())
    }

    fun delete(id: UUID) = store.remove(id)

    fun list() = store.values.toList()
}
