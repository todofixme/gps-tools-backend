package org.devshred.gpstools.web

import org.apache.tomcat.util.http.fileupload.impl.SizeLimitExceededException
import org.devshred.gpstools.storage.NotFoundException
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler
import java.io.IOException

@ControllerAdvice
class ExceptionControllerAdvice {
    private val log = LoggerFactory.getLogger(javaClass)

    @ExceptionHandler
    fun handleIllegalArgumentException(ex: IllegalArgumentException): ResponseEntity<ProblemDetail> {
        val errorMessage: ProblemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.message!!)
        log.warn(errorMessage.detail)
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorMessage)
    }

    @ExceptionHandler
    fun handleSizeLimitExceededException(ex: SizeLimitExceededException): ResponseEntity<ProblemDetail> {
        val errorMessage: ProblemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.PAYLOAD_TOO_LARGE, "Uploaded file too large.")
        log.warn(errorMessage.detail)
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(errorMessage)
    }

    @ExceptionHandler
    fun handleIOException(ex: IOException): ResponseEntity<ProblemDetail> {
        val errorMessage: ProblemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "IO error")
        log.warn(errorMessage.detail)
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorMessage)
    }

    @ExceptionHandler
    fun handleNullPointerException(ex: java.lang.NullPointerException): ResponseEntity<ProblemDetail> {
        val errorMessage: ProblemDetail = ProblemDetail.forStatus(HttpStatus.NOT_FOUND)
        log.warn(errorMessage.detail)
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorMessage)
    }

    @ExceptionHandler
    fun handleNotFoundException(ex: NotFoundException): ResponseEntity<ProblemDetail> {
        val errorMessage: ProblemDetail = ProblemDetail.forStatus(HttpStatus.NOT_FOUND)
        log.warn(errorMessage.detail)
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorMessage)
    }
}
