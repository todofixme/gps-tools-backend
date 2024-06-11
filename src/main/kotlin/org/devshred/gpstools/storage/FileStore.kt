package org.devshred.gpstools.storage

import org.springframework.stereotype.Service
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

@Service
class FileStore {
    private val store: MutableMap<UUID, StoredFile> = ConcurrentHashMap()

    fun get(id: UUID) = store.getOrElse(id) { throw NotFoundException("File with ID $id not found.") }

    fun put(
        uuid: UUID,
        file: StoredFile,
    ) {
        store[uuid] = file
    }

    fun delete(id: UUID) = store.remove(id)

    fun list() = store.values.toList()
}
