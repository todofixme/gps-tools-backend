package org.devshred.gpstools.storage

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
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
)

@JvmInline
value class Filename(val value: String)
