package org.devshred.gpstools.domain.common

inline fun <R> R?.orElse(block: () -> R): R {
    return this ?: block()
}
