package org.devshred.gpstools.storage

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import org.devshred.gpstools.api.model.TrackDTO
import java.time.LocalDateTime
import java.util.UUID

@JsonIgnoreProperties("storageLocation", "createdAt")
data class StoredFile(
    val id: UUID,
    val filename: Filename,
    val mimeType: String,
    val href: String,
    val size: Long,
    val storageLocation: String,
    val createdAt: LocalDateTime = LocalDateTime.now(),
) {
    fun toTrackDTO() = TrackDTO(id, filename.value, mimeType, href, size)
}

@JvmInline
value class Filename(val value: String)
