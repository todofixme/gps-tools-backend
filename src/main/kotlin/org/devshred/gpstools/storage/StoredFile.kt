package org.devshred.gpstools.storage

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import java.util.UUID

@JsonIgnoreProperties("storageLocation")
data class StoredFile(
    val id: UUID,
    val filename: Filename,
    val mimeType: String,
    val href: String,
    val size: Long,
    val storageLocation: String,
)

@JvmInline
value class Filename(val value: String)
