package org.devshred.gpstools.storage

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import org.devshred.gpstools.api.model.TrackDTO
import java.time.LocalDateTime
import java.util.UUID

@JsonIgnoreProperties("storageLocation", "createdAt", "lastModifiedDate")
data class StoredTrack(
    val id: UUID,
    val name: String,
    val storageLocation: String,
    val createdDate: LocalDateTime = LocalDateTime.now(),
    val lastModifiedDate: LocalDateTime = LocalDateTime.now(),
) {
    fun toTrackDTO() = TrackDTO(id, name)
}
