package org.devshred.gpstools.common

inline fun <R> R?.orElse(block: () -> R): R {
    return this ?: block()
}
