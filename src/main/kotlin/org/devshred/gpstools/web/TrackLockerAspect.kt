package org.devshred.gpstools.web

import org.aspectj.lang.JoinPoint
import org.aspectj.lang.annotation.After
import org.aspectj.lang.annotation.Aspect
import org.aspectj.lang.annotation.Before
import org.devshred.gpstools.web.TrackLocker.Companion.trackLocker
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.util.UUID

@Aspect
@Component
class TrackLockerAspect {
    private val log = LoggerFactory.getLogger(javaClass)

    @Before("@annotation(LockTrack)")
    fun lockBefore(joinPoint: JoinPoint) {
        log.debug("advice running before {}", joinPoint.signature.name)
        val trackId: UUID = findTrackId(joinPoint)
        log.debug("before {}", trackId)
        trackLocker().lock(trackId)
    }

    @After("@annotation(LockTrack)")
    fun unlockAfter(joinPoint: JoinPoint) {
        log.debug("advice running after {}", joinPoint.signature.name)
        val trackId: UUID = findTrackId(joinPoint)
        trackLocker().unlock(trackId)
        log.debug("after {}", trackId)
    }

    private fun findTrackId(joinPoint: JoinPoint): UUID {
        val methodName = joinPoint.signature.name
        val indexOfTrackId =
            joinPoint.target.javaClass.methods
                .first { it.name == methodName }.parameters
                .indexOfFirst { it.name == "trackId" }
        if (indexOfTrackId == -1) {
            throw RuntimeException("Advice won't work because parameter trackId was not found.")
        }
        indexOfTrackId.let { return joinPoint.args[indexOfTrackId] as UUID }
    }
}

/**
 * Marks
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class LockTrack
