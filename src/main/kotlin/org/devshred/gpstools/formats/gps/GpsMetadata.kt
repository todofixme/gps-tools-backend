package org.devshred.gpstools.formats.gps

data class GpsMetadata(
    val description: String? = null,
    val copyrightAuthor: String? = null,
    val copyrightYear: Int? = null,
    val linkHref: String? = null,
) {
    companion object {
        fun ofNullable(
            description: String? = null,
            copyrightAuthor: String? = null,
            copyrightYear: Int? = null,
            linkHref: String? = null,
        ): GpsMetadata? =
            if (description != null || copyrightAuthor != null || copyrightYear != null || linkHref != null) {
                GpsMetadata(
                    description = description,
                    copyrightAuthor = copyrightAuthor,
                    copyrightYear = copyrightYear,
                    linkHref = linkHref,
                )
            } else {
                null
            }
    }
}
