package org.devshred.upload

import org.springframework.stereotype.Service
import java.util.*

@Service
class FileStore {
    private val store: MutableMap<UUID, StoredFile> = HashMap()

    fun all(): Collection<StoredFile> = store.values
    fun get(id: UUID) = store[id]
    fun put(uuid: UUID, file: StoredFile) {
        store[uuid] = file
    }

    fun delete(id: UUID) = store.remove(id)
}
