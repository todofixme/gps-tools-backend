package org.devshred.gpstools.web

import org.slf4j.LoggerFactory
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantLock

/**
 * Locks a track by its ID during processing to avoid race conditions.
 */
class TrackLocker private constructor() {
    companion object {
        private val lockedTracks = ConcurrentHashMap<UUID, LockWrapper>()

        @Volatile
        private var instance: TrackLocker? = null

        fun trackLocker(): TrackLocker {
            if (instance == null) {
                synchronized(this) {
                    if (instance == null) {
                        instance = TrackLocker()
                    }
                }
            }
            return instance!!
        }
    }

    private val log = LoggerFactory.getLogger(javaClass)

    private class LockWrapper {
        val lock: Lock = ReentrantLock()
        private val log = LoggerFactory.getLogger(javaClass)
        private val numberOfThreadsInQueue = AtomicInteger(1)

        fun addThreadInQueue(): LockWrapper {
            val currentQueueLength = numberOfThreadsInQueue.incrementAndGet()
            log.debug("current queue length: {}", currentQueueLength)
            return this
        }

        fun removeThreadFromQueue(): Int = numberOfThreadsInQueue.decrementAndGet()
    }

    fun lock(trackId: UUID) {
        val lockWrapper =
            lockedTracks.compute(trackId) { _: UUID, v: LockWrapper? -> v?.addThreadInQueue() ?: LockWrapper() }
        lockWrapper!!.lock.lock()
        log.debug("locked {}", trackId)
    }

    fun unlock(trackId: UUID) {
        val lockWrapper = lockedTracks[trackId]
        if (lockWrapper == null) {
            log.warn("no lock found for {}", trackId)
            return
        }
        lockWrapper.lock.unlock()
        log.debug("released key {}", trackId)
        if (lockWrapper.removeThreadFromQueue() == 0) {
            lockedTracks.remove(trackId, lockWrapper)
            log.debug("removed lock {}", trackId)
        }
    }
}
