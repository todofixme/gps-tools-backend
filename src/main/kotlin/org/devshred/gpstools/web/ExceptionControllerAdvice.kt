package org.devshred.gpstools.web

import org.apache.tomcat.util.http.fileupload.impl.SizeLimitExceededException
import org.devshred.gpstools.api.model.ProblemDTO
import org.devshred.gpstools.storage.NotFoundException
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus.BAD_REQUEST
import org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR
import org.springframework.http.HttpStatus.NOT_FOUND
import org.springframework.http.HttpStatus.PAYLOAD_TOO_LARGE
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler
import java.io.IOException

@ControllerAdvice
class ExceptionControllerAdvice {
    private val log = LoggerFactory.getLogger(javaClass)

    @ExceptionHandler
    fun handleIllegalArgumentException(ex: IllegalArgumentException): ResponseEntity<ProblemDTO> {
        val problem: Problem = createProblem(BAD_REQUEST, ex)
        log.warn(problem.detail)
        return ResponseEntity.status(problem.status).body(problem.toDTO())
    }

    @ExceptionHandler
    fun handleSizeLimitExceededException(ex: SizeLimitExceededException): ResponseEntity<ProblemDTO> {
        val problem: Problem = createProblem(PAYLOAD_TOO_LARGE, "Uploaded file too large.")
        log.warn(ex.message)
        return ResponseEntity.status(problem.status).body(problem.toDTO())
    }

    @ExceptionHandler
    fun handleIOException(ex: IOException): ResponseEntity<ProblemDTO> {
        val problem: Problem = createProblem(INTERNAL_SERVER_ERROR, "IO error")
        log.warn(ex.message)
        return ResponseEntity.status(problem.status).body(problem.toDTO())
    }

    @ExceptionHandler
    fun handleNullPointerException(ex: java.lang.NullPointerException): ResponseEntity<ProblemDTO> {
        val problem: Problem = createProblem(NOT_FOUND)
        log.warn(ex.message)
        return ResponseEntity.status(problem.status).body(problem.toDTO())
    }

    @ExceptionHandler
    fun handleNotFoundException(ex: NotFoundException): ResponseEntity<ProblemDTO> {
        val problem: Problem = createProblem(NOT_FOUND, ex)
        log.warn(problem.detail)
        return ResponseEntity.status(problem.status).body(problem.toDTO())
    }
}
