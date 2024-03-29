package org.devshred.gpstools.storage

import org.springframework.stereotype.Service
import java.util.UUID

@Service
class FileStore {
    private val store: MutableMap<UUID, StoredFile> = HashMap()

    fun get(id: UUID) = store.getOrElse(id) { throw NotFoundException("File with ID $id not found.") }

    fun put(
        uuid: UUID,
        file: StoredFile,
    ) {
        store[uuid] = file
    }

    fun delete(id: UUID) = store.remove(id)
}
