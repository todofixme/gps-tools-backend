package org.devshred.upload

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import java.util.UUID

@JsonIgnoreProperties("storageLocation")
data class StoredFile(
    val id: UUID,
    val filename: String,
    val mimeType: String,
    val href: String,
    val size: Long,
    val storageLocation: String,
)
